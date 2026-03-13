package ir.phonx;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.List;

public class SettingsActivity extends AppCompatActivity implements ConfigAdapter.Listener {

    private EditText etServerUri;
    private RecyclerView rvConfigs;
    private TextView tvEmptyConfigs;
    private ConfigAdapter configAdapter;
    private MaterialSwitch switchPsiphon;
    private MaterialSwitch switchTryAll;
    private ConfigStorage configStorage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(R.style.SettingsTheme);
        setContentView(R.layout.activity_settings);

        configStorage = new ConfigStorage(this);

        etServerUri = findViewById(R.id.etServerUri);
        rvConfigs = findViewById(R.id.rvConfigs);
        tvEmptyConfigs = findViewById(R.id.tvEmptyConfigs);
        switchPsiphon = findViewById(R.id.switchPsiphon);
        switchTryAll = findViewById(R.id.switchTryAll);
        View btnSave = findViewById(R.id.btnSave);
        View btnBack = findViewById(R.id.btnBack);

        // Config list
        rvConfigs.setLayoutManager(new LinearLayoutManager(this));
        configAdapter = new ConfigAdapter();
        configAdapter.setListener(this);
        rvConfigs.setAdapter(configAdapter);

        loadConfigList();

        // Psiphon toggle
        switchPsiphon.setChecked(configStorage.isPsiphonEnabled());
        switchPsiphon.setOnCheckedChangeListener((buttonView, isChecked) ->
                configStorage.setPsiphonEnabled(isChecked));

        // Try-all toggle
        switchTryAll.setChecked(configStorage.isTryAllEnabled());
        switchTryAll.setOnCheckedChangeListener((buttonView, isChecked) ->
                configStorage.setTryAllEnabled(isChecked));

        btnSave.setOnClickListener(v -> addConfig());
        btnBack.setOnClickListener(v -> finish());
    }

    private void loadConfigList() {
        List<ConfigEntry> configs = configStorage.loadConfigs();
        String activeId = configStorage.getActiveConfigId();
        configAdapter.setConfigs(configs, activeId);

        if (configs.isEmpty()) {
            tvEmptyConfigs.setVisibility(View.VISIBLE);
            rvConfigs.setVisibility(View.GONE);
        } else {
            tvEmptyConfigs.setVisibility(View.GONE);
            rvConfigs.setVisibility(View.VISIBLE);
        }
    }

    private void addConfig() {
        String input = etServerUri.getText().toString().trim();

        if (input.isEmpty()) {
            Toast.makeText(this, getString(R.string.empty_uri_toast), Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            ConfigEntry entry = ConfigEntry.fromUri(input);
            configStorage.addConfig(entry);
            etServerUri.setText("");
            loadConfigList();
            Toast.makeText(this, R.string.config_added_toast, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this,
                    getString(R.string.invalid_uri_toast, e.getMessage()),
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onConfigSelected(ConfigEntry entry) {
        configStorage.setActiveConfigId(entry.id);
        loadConfigList();
    }

    @Override
    public void onConfigRemoved(ConfigEntry entry) {
        configStorage.removeConfig(entry.id);
        loadConfigList();
        Toast.makeText(this, R.string.config_removed_toast, Toast.LENGTH_SHORT).show();
    }
}
