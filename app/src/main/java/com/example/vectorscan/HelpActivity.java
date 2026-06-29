package com.example.vectorscan;

import android.os.Bundle;
import android.text.Html;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class HelpActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);

        // Toolbar with back arrow
        Toolbar toolbar = findViewById(R.id.toolbarHelp);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        // Render HTML formatted help text
        TextView tvHelp = findViewById(R.id.tvHelpContent);
        tvHelp.setText(Html.fromHtml(getString(R.string.help_description), Html.FROM_HTML_MODE_COMPACT));
    }
}
