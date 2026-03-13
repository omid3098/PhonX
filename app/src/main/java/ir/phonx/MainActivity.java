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
import android.widget.Button;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

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

    private enum State { DISCONNECTED, CONNECTING, CONNECTED }

    private State currentState = State.DISCONNECTED;

    private Button btnConnect;
    private TextView tvStatus;
    private TextView tvIpAddress;
    private ConfigStorage configStorage;

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
                    setState(State.CONNECTED);
                    String ip = intent.getStringExtra(EXTRA_IP);
                    if (ip != null && tvIpAddress != null) {
                        String country = intent.getStringExtra(EXTRA_COUNTRY);
                        if (country != null && !country.isEmpty()) {
                            tvIpAddress.setText(getString(R.string.ip_label_with_country, ip, country));
                        } else {
                            tvIpAddress.setText(getString(R.string.ip_label, ip));
                        }
                        tvIpAddress.setVisibility(View.VISIBLE);
                    }
                    break;
                case STATUS_CONNECTING:
                case STATUS_CONNECTING_PSIPHON:
                    setState(State.CONNECTING);
                    break;
                case STATUS_VERIFYING:
                    setState(State.CONNECTING);
                    tvStatus.setText(R.string.status_verifying);
                    break;
                case STATUS_TRYING_NEXT:
                    setState(State.CONNECTING);
                    int attempt = intent.getIntExtra("attempt", 0);
                    int total = intent.getIntExtra("total", 0);
                    tvStatus.setText(getString(R.string.status_trying_config, attempt, total));
                    break;
                case STATUS_DISCONNECTED:
                    setState(State.DISCONNECTED);
                    break;
                case STATUS_ERROR:
                    setState(State.DISCONNECTED);
                    String msg = intent.getStringExtra("message");
                    if (msg != null) {
                        Toast.makeText(MainActivity.this, getString(R.string.error_prefix, msg), Toast.LENGTH_LONG).show();
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

        btnConnect = findViewById(R.id.btnConnect);
        tvStatus = findViewById(R.id.tvStatus);
        tvIpAddress = findViewById(R.id.tvIpAddress);
        View btnSettings = findViewById(R.id.btnSettings);

        btnConnect.setOnClickListener(v -> onConnectClicked());
        btnSettings.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        setState(State.DISCONNECTED);
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

    private void onConnectClicked() {
        if (currentState == State.CONNECTED || currentState == State.CONNECTING) {
            stopVpnService();
            return;
        }

        if (!configStorage.hasConfig()) {
            Toast.makeText(this, R.string.no_config_toast, Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, SettingsActivity.class));
            return;
        }

        setState(State.CONNECTING);

        // On Android 13+, request notification permission so the VPN notification
        // is visible in the status bar and notification shade.
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
        switch (state) {
            case DISCONNECTED:
                btnConnect.setBackgroundResource(R.drawable.btn_connect_disconnected);
                btnConnect.setText(R.string.connect);
                tvStatus.setText(R.string.status_disconnected);
                tvStatus.setTextColor(getResources().getColor(R.color.btn_disconnected, null));
                if (tvIpAddress != null) {
                    tvIpAddress.setText("");
                    tvIpAddress.setVisibility(View.GONE);
                }
                break;
            case CONNECTING:
                btnConnect.setBackgroundResource(R.drawable.btn_connect_connecting);
                btnConnect.setText(R.string.disconnect);
                tvStatus.setText(R.string.status_connecting);
                tvStatus.setTextColor(getResources().getColor(R.color.btn_connecting, null));
                if (tvIpAddress != null) {
                    tvIpAddress.setVisibility(View.GONE);
                }
                break;
            case CONNECTED:
                btnConnect.setBackgroundResource(R.drawable.btn_connect_connected);
                btnConnect.setText(R.string.disconnect);
                tvStatus.setText(R.string.status_connected);
                tvStatus.setTextColor(getResources().getColor(R.color.btn_connected, null));
                break;
        }
    }
}
