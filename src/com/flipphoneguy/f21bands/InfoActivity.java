package com.flipphoneguy.f21bands;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public final class InfoActivity extends Activity {

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_info);

        TextView usUrl    = findViewById(R.id.info_url_us);
        TextView stockUrl = findViewById(R.id.info_url_stock);
        usUrl.setText(Constants.urlForRegion(Constants.REGION_US));
        stockUrl.setText(Constants.urlForRegion(Constants.REGION_STOCK));

        Button github = findViewById(R.id.btn_github);
        Button repo   = findViewById(R.id.btn_repo);
        github.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { open(getString(R.string.info_github_url)); }
        });
        repo.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { open(getString(R.string.info_repo_url)); }
        });
    }

    private void open(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {}
    }
}
