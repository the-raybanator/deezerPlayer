package com.deezerwatch.app;

import android.util.Log;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import org.json.JSONArray;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Receives requests from the watch (over the Bluetooth link, no internet on the watch). */
public class WatchService extends WearableListenerService {
    private static final String TAG = "DeezerWatch";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    @Override
    public void onMessageReceived(MessageEvent e) {
        final String node = e.getSourceNodeId();
        final String payload = new String(e.getData(), StandardCharsets.UTF_8);

        switch (e.getPath()) {
            case Protocol.PATH_REQUEST_PLAYLISTS:
                IO.execute(this::pushPlaylists);
                break;
            case Protocol.PATH_REQUEST_STATE:
                IO.execute(() -> NowPlaying.publish(this));
                break;
            case Protocol.PATH_TRANSPORT:
                IO.execute(() -> reply(node, DeezerControl.transport(this, payload)));
                break;
            case Protocol.PATH_PLAY_PLAYLIST:
                IO.execute(() -> {
                    int nl = payload.indexOf('\n');
                    if (nl < 0) return;
                    try {
                        long id = Long.parseLong(payload.substring(0, nl));
                        reply(node, DeezerControl.playPlaylist(this, id, payload.substring(nl + 1)));
                    } catch (NumberFormatException ex) {
                        Log.w(TAG, "Bad playlist id in " + payload);
                    }
                });
                break;
            default:
                break;
        }
    }

    private void pushPlaylists() {
        PutDataMapRequest req = PutDataMapRequest.create(Protocol.PATH_PLAYLISTS);
        try {
            JSONArray list = DeezerApi.fetchPlaylists(Prefs.userId(this), Prefs.token(this));
            req.getDataMap().putString(Protocol.KEY_JSON, list.toString());
            req.getDataMap().putString(Protocol.KEY_ERROR, "");
        } catch (Exception ex) {
            Log.w(TAG, "Playlist fetch failed", ex);
            req.getDataMap().putString(Protocol.KEY_JSON, "[]");
            req.getDataMap().putString(Protocol.KEY_ERROR, String.valueOf(ex.getMessage()));
        }
        // Changes on every push so the watch is notified even if the list is identical.
        req.getDataMap().putLong(Protocol.KEY_UPDATED, System.currentTimeMillis());
        Wearable.getDataClient(this).putDataItem(req.asPutDataRequest().setUrgent());
    }

    private void reply(String node, String text) {
        if (text.isEmpty()) return;
        Wearable.getMessageClient(this).sendMessage(node, Protocol.PATH_RESULT, text.getBytes(StandardCharsets.UTF_8));
    }
}
