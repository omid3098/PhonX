package ir.phonx;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    public static final String ACTION_VPN_STATUS = "ir.phonx.VPN_STATUS";
    public static final String EXTRA_STATUS = "STATUS";

    public static final String STATUS_DISCONNECTED = "disconnected";
    public static final String STATUS_CONNECTING = "connecting";
    public static final String STATUS_CONNECTING_PSIPHON = "connecting_psiphon";
    public static final String STATUS_CONNECTED = "connected";
    public static final String STATUS_VERIFYING = "verifying";
    public static final String STATUS_ERROR = "error";
    public static final String STATUS_TRYING_NEXT = "trying_next";
    public static final String EXTRA_IP = "ip_address";
    public static final String EXTRA_COUNTRY = "ip_country";

    public enum State { DISCONNECTED, CONNECTING, CONNECTED }

    public interface VpnStateListener {
        void onStateChanged(State state, String ip, String country);
        void onStatusText(String text);
        void onError(String message);
    }

    private State currentState = State.DISCONNECTED;
    private String lastIp;
    private String lastCountry;
    private VpnStateListener stateListener;
    private ConfigStorage configStorage;

    private BottomNavigationView bottomNav;
    private Fragment homeFragment;
    private Fragment settingsFragment;
    private Fragment activeFragment;

    private final ActivityResultLauncher<Intent> vpnPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    startVpnService();
                } else {
                    Toast.makeText(this, "VPN permission denied", Toast.LENGTH_SHORT).show();
                    setState(State.DISCONNECTED);
                }
            });

    private final ActivityResultLauncher<String> notifPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                // Proceed to start VPN regardless — notification is nice-to-have
                requestVpnPermissionAndStart();
            });

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String status = intent.getStringExtra(EXTRA_STATUS);
            if (status == null) return;
            switch (status) {
                case STATUS_CONNECTED:
                    String ip = intent.getStringExtra(EXTRA_IP);
                    String country = intent.getStringExtra(EXTRA_COUNTRY);
                    lastIp = ip;
                    lastCountry = country;
                    setState(State.CONNECTED);
                    break;
                case STATUS_CONNECTING:
                case STATUS_CONNECTING_PSIPHON:
                    setState(State.CONNECTING);
                    break;
                case STATUS_VERIFYING:
                    setState(State.CONNECTING);
                    if (stateListener != null) {
                        stateListener.onStatusText(getString(R.string.status_verifying));
                    }
                    break;
                case STATUS_TRYING_NEXT:
                    setState(State.CONNECTING);
                    int attempt = intent.getIntExtra("attempt", 0);
                    int total = intent.getIntExtra("total", 0);
                    if (stateListener != null) {
                        stateListener.onStatusText(getString(R.string.status_trying_config, attempt, total));
                    }
                    break;
                case STATUS_DISCONNECTED:
                    lastIp = null;
                    lastCountry = null;
                    setState(State.DISCONNECTED);
                    break;
                case STATUS_ERROR:
                    lastIp = null;
                    lastCountry = null;
                    setState(State.DISCONNECTED);
                    String msg = intent.getStringExtra("message");
                    if (msg != null) {
                        Toast.makeText(MainActivity.this, getString(R.string.error_prefix, msg), Toast.LENGTH_LONG).show();
                        if (stateListener != null) {
                            stateListener.onError(msg);
                        }
                    }
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        configStorage = new ConfigStorage(this);
        bottomNav = findViewById(R.id.bottomNav);

        if (savedInstanceState == null) {
            homeFragment = new HomeFragment();
            settingsFragment = new SettingsFragment();
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragmentContainer, settingsFragment, "settings")
                    .hide(settingsFragment)
                    .add(R.id.fragmentContainer, homeFragment, "home")
                    .commit();
            activeFragment = homeFragment;
        } else {
            homeFragment = getSupportFragmentManager().findFragmentByTag("home");
            settingsFragment = getSupportFragmentManager().findFragmentByTag("settings");
            activeFragment = homeFragment;
        }

        bottomNav.setOnItemSelectedListener(item -> {
            Fragment target;
            if (item.getItemId() == R.id.nav_home) {
                target = homeFragment;
            } else if (item.getItemId() == R.id.nav_settings) {
                target = settingsFragment;
            } else {
                return false;
            }
            if (target != activeFragment) {
                getSupportFragmentManager().beginTransaction()
                        .hide(activeFragment)
                        .show(target)
                        .commit();
                activeFragment = target;
            }
            return true;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(
                statusReceiver, new IntentFilter(ACTION_VPN_STATUS));
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(statusReceiver);
    }

    // Called by HomeFragment
    void onConnectClicked() {
        if (currentState == State.CONNECTED || currentState == State.CONNECTING) {
            stopVpnService();
            return;
        }

        if (!configStorage.hasConfig()) {
            Toast.makeText(this, R.string.no_config_toast, Toast.LENGTH_SHORT).show();
            bottomNav.setSelectedItemId(R.id.nav_settings);
            return;
        }

        setState(State.CONNECTING);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                   != PackageManager.PERMISSION_GRANTED) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            return;
        }

        requestVpnPermissionAndStart();
    }

    private void requestVpnPermissionAndStart() {
        Intent vpnIntent = VpnService.prepare(this);
        if (vpnIntent != null) {
            vpnPermissionLauncher.launch(vpnIntent);
        } else {
            startVpnService();
        }
    }

    private void startVpnService() {
        Intent intent = new Intent(this, PhonXVpnService.class);
        intent.setAction(PhonXVpnService.ACTION_START);
        ContextCompat.startForegroundService(this, intent);
    }

    private void stopVpnService() {
        Intent intent = new Intent(this, PhonXVpnService.class);
        intent.setAction(PhonXVpnService.ACTION_STOP);
        startService(intent);
    }

    private void setState(State state) {
        currentState = state;
        if (stateListener != null) {
            stateListener.onStateChanged(state, lastIp, lastCountry);
        }
    }

    // Fragment access methods

    State getCurrentState() {
        return currentState;
    }

    String getLastIp() {
        return lastIp;
    }

    String getLastCountry() {
        return lastCountry;
    }

    void registerStateListener(VpnStateListener listener) {
        this.stateListener = listener;
        // Deliver current state immediately
        listener.onStateChanged(currentState, lastIp, lastCountry);
    }

    void unregisterStateListener(VpnStateListener listener) {
        if (this.stateListener == listener) {
            this.stateListener = null;
        }
    }

    // For test access
    BottomNavigationView getBottomNav() {
        return bottomNav;
    }
}
