package com.example.vectorscan;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.switchmaterial.SwitchMaterial;

public class SettingsActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "vectorscan_prefs";
    private static final String KEY_DARK_MODE = "dark_mode";
    private static final String KEY_KEEP_SESSION = "keep_session";

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

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

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
    }
}
