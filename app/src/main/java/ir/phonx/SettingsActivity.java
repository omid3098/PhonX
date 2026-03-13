package ir.phonx;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.materialswitch.MaterialSwitch;

public class SettingsActivity extends AppCompatActivity {

    private EditText etServerUri;
    private TextView tvCurrentServer;
    private MaterialSwitch switchPsiphon;
    private ConfigStorage configStorage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(R.style.SettingsTheme);
        setContentView(R.layout.activity_settings);

        configStorage = new ConfigStorage(this);

        etServerUri = findViewById(R.id.etServerUri);
        tvCurrentServer = findViewById(R.id.tvCurrentServer);
        switchPsiphon = findViewById(R.id.switchPsiphon);
        Button btnSave = findViewById(R.id.btnSave);
        View btnBack = findViewById(R.id.btnBack);

        loadCurrentConfig();

        // Psiphon toggle
        switchPsiphon.setChecked(configStorage.isPsiphonEnabled());
        switchPsiphon.setOnCheckedChangeListener((buttonView, isChecked) ->
                configStorage.setPsiphonEnabled(isChecked));

        btnSave.setOnClickListener(v -> saveConfig());
        btnBack.setOnClickListener(v -> finish());
    }

    private void loadCurrentConfig() {
        String uri = configStorage.loadUri();
        if (uri != null && !uri.isEmpty()) {
            tvCurrentServer.setText(uri);
        } else {
            tvCurrentServer.setText(R.string.no_server);
        }
    }

    private void saveConfig() {
        String input = etServerUri.getText().toString().trim();

        if (input.isEmpty()) {
            Toast.makeText(this, getString(R.string.empty_uri_toast), Toast.LENGTH_SHORT).show();
            return;
        }

        // Validate the URI before saving
        try {
            ConfigParser.ProxyConfig config = ConfigParser.parse(input);
            configStorage.saveUri(input);
            Toast.makeText(this, R.string.config_saved_toast, Toast.LENGTH_SHORT).show();
            tvCurrentServer.setText(input);
            etServerUri.setText("");
            finish();
        } catch (Exception e) {
            Toast.makeText(this,
                    getString(R.string.invalid_uri_toast, e.getMessage()),
                    Toast.LENGTH_LONG).show();
        }
    }
}
