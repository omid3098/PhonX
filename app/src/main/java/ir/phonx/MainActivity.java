package ir.phonx;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity implements VpnStatusManager.Listener {
    public static final String STATUS_DISCONNECTED = "disconnected";
    public static final String STATUS_CONNECTING = "connecting";
    public static final String STATUS_CONNECTING_PSIPHON = "connecting_psiphon";
    public static final String STATUS_CONNECTED = "connected";
    public static final String STATUS_VERIFYING = "verifying";
    public static final String STATUS_ERROR = "error";
    public static final String STATUS_TRYING_NEXT = "trying_next";
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        configStorage = new ConfigStorage(this);
        bottomNav = findViewById(R.id.bottomNav);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_layout), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });

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
    protected void onStart() {
        super.onStart();
        VpnStatusManager.getInstance().register(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        VpnStatusManager.getInstance().unregister(this);
    }

    @Override
    public void onVpnStatusChanged(String status, String ip, String country, int attempt, int total, String configName, String message) {
        if (status == null) return;
        switch (status) {
            case STATUS_CONNECTED:
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
                if (message != null) {
                    Toast.makeText(this, getString(R.string.error_prefix, message), Toast.LENGTH_LONG).show();
                    if (stateListener != null) {
                        stateListener.onError(message);
                    }
                }
                break;
        }
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
