package com.deezerwatch.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.wearable.Wearable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Setup screen: Deezer account, permissions, and a status check for the watch link. */
public class MainActivity extends Activity {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private TextView status;
    private EditText userIn;
    private EditText tokenIn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(24), dp(16), dp(24));
        scroll.addView(root);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText("Deezer Watch");
        title.setTextSize(26);
        root.addView(title);

        status = new TextView(this);
        status.setPadding(0, dp(8), 0, dp(8));
        root.addView(status);

        root.addView(label("1. Permissions"));
        root.addView(button("Enable notification access",
                v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))));
        root.addView(button("Allow display over other apps",
                v -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())))));
        root.addView(hint("Notification access lets the watch see and control Deezer's player. "
                + "Display-over-apps lets the watch open Deezer when it isn't running. "
                + "On Android 13+ you may first need Settings > Apps > Deezer Watch > "
                + "(three dots) > Allow restricted settings."));

        root.addView(label("2. Your Deezer playlists"));
        root.addView(hint("User id is the number in your profile link (deezer.com/profile/<id>). "
                + "Only public playlists show up with just the id. For private playlists "
                + "paste a Deezer access token too."));
        userIn = new EditText(this);
        userIn.setHint("Deezer user id");
        userIn.setSingleLine();
        userIn.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        userIn.setText(Prefs.userId(this));
        root.addView(userIn);
        tokenIn = new EditText(this);
        tokenIn.setHint("Access token (optional)");
        tokenIn.setSingleLine();
        tokenIn.setText(Prefs.token(this));
        root.addView(tokenIn);

        root.addView(button("Save and test", v -> saveAndTest()));

        root.addView(label("3. On the watch"));
        root.addView(hint("Install the watch app (see README), open it, and your playlists appear. "
                + "The watch must be paired with this phone."));
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private void saveAndTest() {
        final String user = userIn.getText().toString().trim();
        final String token = tokenIn.getText().toString().trim();
        Prefs.save(this, user, token);
        toast("Checking Deezer...");
        IO.execute(() -> {
            try {
                int n = DeezerApi.fetchPlaylists(user, token).length();
                toast(n == 0 ? "Connected, but no playlists found (private? add a token)"
                        : "Found " + n + " playlists");
            } catch (Exception ex) {
                toast("Failed: " + ex.getMessage());
            }
        });
    }

    private void updateStatus() {
        String nl = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        boolean nlOn = nl != null && nl.contains(getPackageName());
        boolean overlayOn = Settings.canDrawOverlays(this);
        final String base = "Notification access: " + (nlOn ? "ON" : "OFF")
                + "\nDisplay over apps: " + (overlayOn ? "ON" : "OFF")
                + "\nDeezer player: " + (nlOn ? (DeezerSession.find(this) != null ? "found" : "not running") : "?");
        status.setText(base + "\nWatch: checking...");
        Wearable.getNodeClient(this).getConnectedNodes().addOnCompleteListener(t -> {
            String w = !t.isSuccessful() ? "Google Play services / Wear OS app missing"
                    : t.getResult().isEmpty() ? "not connected" : t.getResult().get(0).getDisplayName();
            status.setText(base + "\nWatch: " + w);
        });
    }

    private Button button(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    private TextView label(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(18);
        t.setPadding(0, dp(20), 0, dp(4));
        return t;
    }

    private TextView hint(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    private void toast(String msg) {
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        });
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
