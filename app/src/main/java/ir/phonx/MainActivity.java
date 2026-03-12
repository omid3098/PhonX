package ir.phonx;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.VpnService;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class MainActivity extends AppCompatActivity {

    public static final String ACTION_VPN_STATUS = "ir.phonx.VPN_STATUS";
    public static final String EXTRA_STATUS = "STATUS";

    public static final String STATUS_DISCONNECTED = "disconnected";
    public static final String STATUS_CONNECTING = "connecting";
    public static final String STATUS_CONNECTED = "connected";
    public static final String STATUS_ERROR = "error";

    private enum State { DISCONNECTED, CONNECTING, CONNECTED }

    private State currentState = State.DISCONNECTED;

    private Button btnConnect;
    private TextView tvStatus;
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

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String status = intent.getStringExtra(EXTRA_STATUS);
            if (status == null) return;
            switch (status) {
                case STATUS_CONNECTED:
                    setState(State.CONNECTED);
                    break;
                case STATUS_CONNECTING:
                    setState(State.CONNECTING);
                    break;
                case STATUS_DISCONNECTED:
                    setState(State.DISCONNECTED);
                    break;
                case STATUS_ERROR:
                    setState(State.DISCONNECTED);
                    String msg = intent.getStringExtra("message");
                    if (msg != null) {
                        Toast.makeText(MainActivity.this, "خطا: " + msg, Toast.LENGTH_LONG).show();
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
        ImageButton btnSettings = findViewById(R.id.btnSettings);

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
        startService(intent);
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
                break;
            case CONNECTING:
                btnConnect.setBackgroundResource(R.drawable.btn_connect_connecting);
                btnConnect.setText(R.string.disconnect);
                tvStatus.setText(R.string.status_connecting);
                tvStatus.setTextColor(getResources().getColor(R.color.btn_connecting, null));
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
