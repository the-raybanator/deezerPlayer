package com.deezerwatch.app;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.wear.widget.WearableLinearLayoutManager;
import androidx.wear.widget.WearableRecyclerView;

import com.google.android.gms.wearable.DataClient;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Playlist list + now-playing controls. All data comes from the phone over the
 * Wear link; the watch itself never uses the network.
 */
public class MainActivity extends Activity
        implements DataClient.OnDataChangedListener, MessageClient.OnMessageReceivedListener {

    private static final class Playlist {
        final long id;
        final String title;
        final int tracks;

        Playlist(long id, String title, int tracks) {
            this.id = id;
            this.title = title;
            this.tracks = tracks;
        }
    }

    private final List<Playlist> playlists = new ArrayList<>();
    private boolean listLoaded;
    private String listError = "";

    private boolean active;
    private boolean playing;
    private String title = "", artist = "";

    private WearableRecyclerView list;
    private ListAdapter adapter;
    private TextView message;
    private ScrollView playerScroll;
    private LinearLayout player;
    private TextView playerTitle, playerArtist;
    private ImageButton toggleBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        boolean round = getResources().getConfiguration().isScreenRound();
        int inset = round ? (int) (getResources().getDisplayMetrics().widthPixels * 0.12f) : dp(8);

        list = new WearableRecyclerView(this);
        list.setEdgeItemsCenteringEnabled(true);
        list.setLayoutManager(new WearableLinearLayoutManager(this));
        adapter = new ListAdapter();
        list.setAdapter(adapter);
        root.addView(list, new FrameLayout.LayoutParams(-1, -1));

        message = new TextView(this);
        message.setTextColor(Color.LTGRAY);
        message.setGravity(Gravity.CENTER);
        message.setPadding(inset, inset, inset, inset);
        root.addView(message, new FrameLayout.LayoutParams(-1, -1));

        // Scrollable so a long title can't squash the buttons; fillViewport keeps short
        // content vertically centred.
        playerScroll = new ScrollView(this);
        playerScroll.setBackgroundColor(Color.BLACK);
        playerScroll.setFillViewport(true);
        playerScroll.setVerticalScrollBarEnabled(false);
        playerScroll.setVisibility(View.GONE);

        player = new LinearLayout(this);
        player.setOrientation(LinearLayout.VERTICAL);
        player.setGravity(Gravity.CENTER);
        player.setPadding(inset, inset, inset, inset);
        playerScroll.addView(player, new ScrollView.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT));

        playerTitle = new TextView(this);
        playerTitle.setTextColor(Color.WHITE);
        playerTitle.setTextSize(17);
        playerTitle.setTypeface(null, Typeface.BOLD);
        playerTitle.setGravity(Gravity.CENTER);
        playerTitle.setMaxLines(3);
        player.addView(playerTitle);

        playerArtist = new TextView(this);
        playerArtist.setTextColor(Color.LTGRAY);
        playerArtist.setTextSize(14);
        playerArtist.setGravity(Gravity.CENTER);
        playerArtist.setMaxLines(2);
        player.addView(playerArtist);

        // Icon sizes are fractions of the usable width so the row fits any watch:
        // prev/next 26% each, play/pause 34%, remaining 14% split into two gaps.
        int avail = getResources().getDisplayMetrics().widthPixels - 2 * inset;
        int side = (int) (avail * 0.26f);
        int mid = (int) (avail * 0.34f);
        int gap = (int) (avail * 0.07f);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(0, dp(8), 0, dp(8));
        controls.addView(iconButton(R.drawable.ic_prev, "Previous", v -> transport(Protocol.CMD_PREV)),
                new LinearLayout.LayoutParams(side, side));
        toggleBtn = iconButton(R.drawable.ic_play, "Play or pause", v -> transport(Protocol.CMD_TOGGLE));
        LinearLayout.LayoutParams midLp = new LinearLayout.LayoutParams(mid, mid);
        midLp.setMargins(gap, 0, gap, 0);
        controls.addView(toggleBtn, midLp);
        controls.addView(iconButton(R.drawable.ic_next, "Next", v -> transport(Protocol.CMD_NEXT)),
                new LinearLayout.LayoutParams(side, side));
        player.addView(controls, new LinearLayout.LayoutParams(-2, -2));

        // Swipe-right closes the whole app on Wear OS, so give an explicit way back to the list.
        Button menu = pillButton("Playlists", 0xFFCD595A, v -> hidePlayer());
        LinearLayout.LayoutParams menuLp = new LinearLayout.LayoutParams(-2, -2);
        menuLp.gravity = Gravity.CENTER_HORIZONTAL;
        player.addView(menu, menuLp);
        root.addView(playerScroll, new FrameLayout.LayoutParams(-1, -1));

        setContentView(root);
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Wearable.getDataClient(this).addListener(this);
        Wearable.getMessageClient(this).addListener(this);
        readCached(Protocol.PATH_PLAYLISTS);
        readCached(Protocol.PATH_NOW_PLAYING);
        send(Protocol.PATH_REQUEST_PLAYLISTS, "");
        send(Protocol.PATH_REQUEST_STATE, "");
        list.requestFocus();
    }

    @Override
    protected void onPause() {
        Wearable.getDataClient(this).removeListener(this);
        Wearable.getMessageClient(this).removeListener(this);
        super.onPause();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (playerScroll.getVisibility() == View.VISIBLE) {
            hidePlayer();
        } else {
            super.onBackPressed();
        }
    }

    // ---- data from the phone ----------------------------------------------------------

    @Override
    public void onDataChanged(@NonNull DataEventBuffer events) {
        for (DataEvent e : events) {
            if (e.getType() != DataEvent.TYPE_CHANGED) continue;
            apply(e.getDataItem().getUri().getPath(), DataMapItem.fromDataItem(e.getDataItem()).getDataMap());
        }
    }

    @Override
    public void onMessageReceived(@NonNull MessageEvent e) {
        if (Protocol.PATH_RESULT.equals(e.getPath())) {
            toast(new String(e.getData(), StandardCharsets.UTF_8));
        }
    }

    private void readCached(String path) {
        Uri uri = new Uri.Builder().scheme("wear").path(path).build();
        Wearable.getDataClient(this).getDataItems(uri).addOnSuccessListener(buf -> {
            try {
                for (int i = 0; i < buf.getCount(); i++) {
                    apply(path, DataMapItem.fromDataItem(buf.get(i)).getDataMap());
                }
            } finally {
                buf.release();
            }
        });
    }

    private void apply(String path, DataMap m) {
        if (Protocol.PATH_PLAYLISTS.equals(path)) {
            listError = m.getString(Protocol.KEY_ERROR, "");
            playlists.clear();
            try {
                JSONArray a = new JSONArray(m.getString(Protocol.KEY_JSON, "[]"));
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.getJSONObject(i);
                    playlists.add(new Playlist(o.getLong("id"), o.getString("title"), o.optInt("n")));
                }
            } catch (Exception ex) {
                listError = "Bad playlist data";
            }
            listLoaded = true;
        } else if (Protocol.PATH_NOW_PLAYING.equals(path)) {
            active = m.getBoolean(Protocol.KEY_ACTIVE, false);
            playing = m.getBoolean(Protocol.KEY_PLAYING, false);
            title = m.getString(Protocol.KEY_TITLE, "");
            artist = m.getString(Protocol.KEY_ARTIST, "");
        }
        render();
    }

    // ---- requests to the phone --------------------------------------------------------

    private void send(String path, String payload) {
        Wearable.getNodeClient(this).getConnectedNodes().addOnSuccessListener(nodes -> {
            if (nodes.isEmpty()) {
                toast("Phone not connected");
                return;
            }
            byte[] data = payload.getBytes(StandardCharsets.UTF_8);
            for (com.google.android.gms.wearable.Node n : nodes) {
                Wearable.getMessageClient(this).sendMessage(n.getId(), path, data);
            }
        }).addOnFailureListener(e -> toast("Phone not connected"));
    }

    private void transport(String cmd) {
        send(Protocol.PATH_TRANSPORT, cmd);
    }

    private void play(Playlist p) {
        send(Protocol.PATH_PLAY_PLAYLIST, p.id + "\n" + p.title);
        showPlayer();
    }

    private void showPlayer() {
        playerScroll.setVisibility(View.VISIBLE);
        playerScroll.scrollTo(0, 0);
        playerScroll.requestFocus(); // lets the rotary bezel scroll it
    }

    private void hidePlayer() {
        playerScroll.setVisibility(View.GONE);
        list.requestFocus();
    }

    // ---- UI ---------------------------------------------------------------------------

    private void render() {
        if (!listLoaded) {
            message.setText("Loading playlists from phone...");
        } else if (!listError.isEmpty()) {
            message.setText(listError + "\n\nCheck the phone app.");
        } else if (playlists.isEmpty()) {
            message.setText("No playlists.\nSet your Deezer user id in the phone app.");
        } else {
            message.setText("");
        }
        message.setVisibility(message.length() == 0 ? View.GONE : View.VISIBLE);

        playerTitle.setText(active && !title.isEmpty() ? title : "Nothing playing");
        playerArtist.setText(active ? artist : "");
        toggleBtn.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        adapter.notifyDataSetChanged();
    }

    /** Row 0 is a "now playing" shortcut when Deezer has a session; the rest are playlists. */
    private final class ListAdapter extends RecyclerView.Adapter<ListAdapter.Row> {
        final class Row extends RecyclerView.ViewHolder {
            final TextView text;

            Row(TextView t) {
                super(t);
                text = t;
            }
        }

        private int offset() {
            return active ? 1 : 0;
        }

        @NonNull
        @Override
        public Row onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView t = new TextView(MainActivity.this);
            t.setTextColor(Color.WHITE);
            t.setTextSize(15);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(12), dp(10), dp(12), dp(10));
            t.setLayoutParams(new RecyclerView.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new Row(t);
        }

        @Override
        public void onBindViewHolder(@NonNull Row h, int position) {
            if (position < offset()) {
                h.text.setText((playing ? "Now playing: " : "Paused: ") + title);
                h.text.setOnClickListener(v -> showPlayer());
            } else {
                Playlist p = playlists.get(position - offset());
                h.text.setText(p.title + "\n" + p.tracks + " tracks");
                h.text.setOnClickListener(v -> play(p));
            }
        }

        @Override
        public int getItemCount() {
            return playlists.size() + offset();
        }
    }

    /** Transparent icon button; the drawable is scaled to whatever size the layout gives it. */
    private ImageButton iconButton(int drawableRes, String description, View.OnClickListener l) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(drawableRes);
        b.setScaleType(ImageView.ScaleType.FIT_CENTER);
        b.setAdjustViewBounds(true);
        b.setPadding(0, 0, 0, 0);
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.OVAL);
        mask.setColor(Color.WHITE);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), null, mask));
        b.setContentDescription(description);
        b.setOnClickListener(l);
        return b;
    }

    /** Bold white label on a rounded (pill) background of the given colour. */
    private Button pillButton(String label, int color, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTypeface(null, Typeface.BOLD);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(22), dp(8), dp(22), dp(8));
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(24));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x55FFFFFF), shape, null));
        b.setStateListAnimator(null);
        b.setOnClickListener(l);
        return b;
    }

    private void toast(String msg) {
        runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
