package com.example.vectorscan;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class LoginActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "vectorscan_prefs";
    private static final String KEY_DARK_MODE = "dark_mode";
    private static final String KEY_KEEP_SESSION = "keep_session";
    private static final String KEY_SAVED_USERNAME = "saved_username";

    private TextInputEditText etUsername, etPassword;
    private MaterialButton btnLogin;
    private TextView tvRegister;
    private DatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply dark mode preference before setContentView
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean darkMode = prefs.getBoolean(KEY_DARK_MODE, false);
        AppCompatDelegate.setDefaultNightMode(
                darkMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
        );

        super.onCreate(savedInstanceState);

        // Auto-login if keep_session is enabled
        boolean keepSession = prefs.getBoolean(KEY_KEEP_SESSION, false);
        String savedUser = prefs.getString(KEY_SAVED_USERNAME, null);
        if (keepSession && savedUser != null) {
            Intent intent = new Intent(this, HomeActivity.class);
            intent.putExtra("username", savedUser);
            startActivity(intent);
            finish();
            return;
        }

        setContentView(R.layout.activity_login);

        dbHelper = new DatabaseHelper(this);

        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvRegister = findViewById(R.id.tvRegister);

        btnLogin.setOnClickListener(v -> attemptLogin());
        tvRegister.setOnClickListener(v -> showRegisterDialog());
    }

    private void attemptLogin() {
        String username = etUsername.getText() != null ? etUsername.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Por favor, rellena todos los campos", Toast.LENGTH_SHORT).show();
            return;
        }

        if (dbHelper.checkUser(username, password)) {
            // Save username for keep-session
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            prefs.edit().putString(KEY_SAVED_USERNAME, username).apply();

            Toast.makeText(this, "¡Bienvenido, " + username + "!", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(LoginActivity.this, HomeActivity.class);
            intent.putExtra("username", username);
            startActivity(intent);
            finish();
        } else {
            Toast.makeText(this, "Usuario o contraseña incorrectos", Toast.LENGTH_SHORT).show();
        }
    }

    private void showRegisterDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Registrar nuevo usuario");

        // Inflate a simple layout with two EditTexts
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_register, null);
        builder.setView(dialogView);

        TextInputEditText etNewUser = dialogView.findViewById(R.id.etNewUsername);
        TextInputEditText etNewPass = dialogView.findViewById(R.id.etNewPassword);

        builder.setPositiveButton("Registrar", (dialog, which) -> {
            String newUser = etNewUser.getText() != null ? etNewUser.getText().toString().trim() : "";
            String newPass = etNewPass.getText() != null ? etNewPass.getText().toString().trim() : "";

            if (newUser.isEmpty() || newPass.isEmpty()) {
                Toast.makeText(this, "Rellena todos los campos", Toast.LENGTH_SHORT).show();
                return;
            }

            if (dbHelper.addUser(newUser, newPass)) {
                Toast.makeText(this, "Usuario registrado correctamente", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "El usuario ya existe", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancelar", null);
        builder.show();
    }
}
