package com.jocude.vectorscan;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class SettingsActivity extends AppCompatActivity {

    private static final String KEY_DARK_MODE = "dark_mode";
    private static final String KEY_KEEP_SESSION = "keep_session";

    private TextInputLayout tilServerUrl;
    private TextInputEditText etServerUrl;
    private TextView tvServerStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Toolbar with back arrow
        Toolbar toolbar = findViewById(R.id.toolbarSettings);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        SharedPreferences prefs = getSharedPreferences(ServerConfig.PREFS_NAME, MODE_PRIVATE);

        // Dark mode
        SwitchMaterial switchDarkMode = findViewById(R.id.switchDarkMode);
        switchDarkMode.setChecked(prefs.getBoolean(KEY_DARK_MODE, false));
        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(KEY_DARK_MODE, isChecked).apply();
            AppCompatDelegate.setDefaultNightMode(
                    isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
            );
        });

        // Keep session
        SwitchMaterial switchKeepSession = findViewById(R.id.switchKeepSession);
        switchKeepSession.setChecked(prefs.getBoolean(KEY_KEEP_SESSION, false));
        switchKeepSession.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(KEY_KEEP_SESSION, isChecked).apply();
        });

        // Server URL
        tilServerUrl = findViewById(R.id.tilServerUrl);
        etServerUrl = findViewById(R.id.etServerUrl);
        tvServerStatus = findViewById(R.id.tvServerStatus);
        MaterialButton btnTestServer = findViewById(R.id.btnTestServer);

        etServerUrl.setText(ServerConfig.getUrl(this));
        etServerUrl.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveServerUrl();
            }
            return false;
        });
        btnTestServer.setOnClickListener(v -> testServer());
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveServerUrl();
    }

    /** @return la URL guardada, o null si la escrita no es válida. */
    private String saveServerUrl() {
        String raw = etServerUrl.getText() != null ? etServerUrl.getText().toString() : "";
        String url = ServerConfig.normalize(raw);
        if (url == null) {
            tilServerUrl.setError(getString(R.string.server_invalid));
            return null;
        }
        tilServerUrl.setError(null);
        ServerConfig.setUrl(this, url);
        if (!url.equals(raw)) etServerUrl.setText(url);
        return url;
    }

    private void testServer() {
        String url = saveServerUrl();
        if (url == null) return;
        tvServerStatus.setText(R.string.server_testing);
        new VectorScanClient(url).checkHealthAsync(new VectorScanClient.Callback<String>() {
            @Override
            public void onSuccess(String version) {
                if (isFinishing()) return;
                tvServerStatus.setText(getString(R.string.server_ok, version));
            }

            @Override
            public void onError(String message) {
                if (isFinishing()) return;
                tvServerStatus.setText(getString(R.string.server_error, message));
            }
        });
    }
}
