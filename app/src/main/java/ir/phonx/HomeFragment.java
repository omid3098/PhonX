package ir.phonx;

import android.animation.ObjectAnimator;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class HomeFragment extends Fragment implements MainActivity.VpnStateListener {

    private Button btnConnect;
    private TextView tvStatus;
    private TextView tvIpAddress;
    private View viewStateGlow;
    private AnimatorSet pulseAnimator;
    private ObjectAnimator glowAnimator;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        btnConnect = view.findViewById(R.id.btnConnect);
        tvStatus = view.findViewById(R.id.tvStatus);
        tvIpAddress = view.findViewById(R.id.tvIpAddress);
        viewStateGlow = view.findViewById(R.id.viewStateGlow);

        btnConnect.setOnClickListener(v -> {
            MainActivity host = (MainActivity) requireActivity();
            host.onConnectClicked();
        });

        // Apply current state immediately
        MainActivity host = (MainActivity) requireActivity();
        applyState(host.getCurrentState(), host.getLastIp(), host.getLastCountry());
    }

    @Override
    public void onStart() {
        super.onStart();
        ((MainActivity) requireActivity()).registerStateListener(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        cancelAnimations();
        ((MainActivity) requireActivity()).unregisterStateListener(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cancelAnimations();
    }

    @Override
    public void onStateChanged(MainActivity.State state, String ip, String country) {
        applyState(state, ip, country);
    }

    @Override
    public void onStatusText(String text) {
        if (tvStatus != null) {
            tvStatus.setText(text);
        }
    }

    @Override
    public void onError(String message) {
        // Errors are shown as Toast by MainActivity
    }

    private void applyState(MainActivity.State state, String ip, String country) {
        if (btnConnect == null) return;

        cancelAnimations();
        btnConnect.setScaleX(1f);
        btnConnect.setScaleY(1f);

        switch (state) {
            case DISCONNECTED:
                btnConnect.setBackgroundResource(R.drawable.btn_connect_disconnected);
                btnConnect.setText(R.string.connect);
                tvStatus.setText(R.string.status_disconnected);
                tvStatus.setTextColor(getResources().getColor(R.color.btn_disconnected, null));
                viewStateGlow.setBackgroundResource(R.drawable.bg_glow_disconnected);
                viewStateGlow.setAlpha(0.3f);
                tvIpAddress.setText("");
                tvIpAddress.setVisibility(View.GONE);
                break;
            case CONNECTING:
                btnConnect.setBackgroundResource(R.drawable.btn_connect_connecting);
                btnConnect.setText(R.string.disconnect);
                tvStatus.setText(R.string.status_connecting);
                tvStatus.setTextColor(getResources().getColor(R.color.btn_connecting, null));
                viewStateGlow.setBackgroundResource(R.drawable.bg_glow_connecting);
                tvIpAddress.setVisibility(View.GONE);
                startPulseAnimation();
                break;
            case CONNECTED:
                btnConnect.setBackgroundResource(R.drawable.btn_connect_connected);
                btnConnect.setText(R.string.disconnect);
                tvStatus.setText(R.string.status_connected);
                tvStatus.setTextColor(getResources().getColor(R.color.btn_connected, null));
                viewStateGlow.setBackgroundResource(R.drawable.bg_glow_connected);
                viewStateGlow.setAlpha(0.6f);
                if (ip != null) {
                    if (country != null && !country.isEmpty()) {
                        tvIpAddress.setText(getString(R.string.ip_label_with_country, ip, country));
                    } else {
                        tvIpAddress.setText(getString(R.string.ip_label, ip));
                    }
                    tvIpAddress.setVisibility(View.VISIBLE);
                }
                break;
        }
    }

    private void startPulseAnimation() {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(btnConnect, "scaleX", 1f, 1.05f);
        scaleX.setRepeatMode(ValueAnimator.REVERSE);
        scaleX.setRepeatCount(ValueAnimator.INFINITE);

        ObjectAnimator scaleY = ObjectAnimator.ofFloat(btnConnect, "scaleY", 1f, 1.05f);
        scaleY.setRepeatMode(ValueAnimator.REVERSE);
        scaleY.setRepeatCount(ValueAnimator.INFINITE);

        pulseAnimator = new AnimatorSet();
        pulseAnimator.playTogether(scaleX, scaleY);
        pulseAnimator.setDuration(800);
        pulseAnimator.start();

        glowAnimator = ObjectAnimator.ofFloat(viewStateGlow, "alpha", 0.3f, 0.7f);
        glowAnimator.setDuration(800);
        glowAnimator.setRepeatMode(ValueAnimator.REVERSE);
        glowAnimator.setRepeatCount(ValueAnimator.INFINITE);
        glowAnimator.start();
    }

    private void cancelAnimations() {
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
            pulseAnimator = null;
        }
        if (glowAnimator != null) {
            glowAnimator.cancel();
            glowAnimator = null;
        }
    }
}
