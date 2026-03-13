package ir.phonx;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.List;

public class SettingsFragment extends Fragment implements ConfigAdapter.Listener {

    private EditText etServerUri;
    private RecyclerView rvConfigs;
    private TextView tvEmptyConfigs;
    private ConfigAdapter configAdapter;
    private MaterialSwitch switchPsiphon;
    private MaterialSwitch switchTryAll;
    private ConfigStorage configStorage;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        configStorage = new ConfigStorage(requireContext());

        etServerUri = view.findViewById(R.id.etServerUri);
        rvConfigs = view.findViewById(R.id.rvConfigs);
        tvEmptyConfigs = view.findViewById(R.id.tvEmptyConfigs);
        switchPsiphon = view.findViewById(R.id.switchPsiphon);
        switchTryAll = view.findViewById(R.id.switchTryAll);
        View btnSave = view.findViewById(R.id.btnSave);

        // Config list
        rvConfigs.setLayoutManager(new LinearLayoutManager(requireContext()));
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
            Toast.makeText(requireContext(), getString(R.string.empty_uri_toast), Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            ConfigEntry entry = ConfigEntry.fromUri(input);
            configStorage.addConfig(entry);
            etServerUri.setText("");
            loadConfigList();
            Toast.makeText(requireContext(), R.string.config_added_toast, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(requireContext(),
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
        Toast.makeText(requireContext(), R.string.config_removed_toast, Toast.LENGTH_SHORT).show();
    }
}
