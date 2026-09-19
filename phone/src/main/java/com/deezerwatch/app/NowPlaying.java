package com.deezerwatch.app;

import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;

import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

/** Pushes what Deezer is playing to the watch as a data item. */
final class NowPlaying {
    private NowPlaying() {}

    static void publish(Context ctx) {
        MediaController c = DeezerSession.find(ctx);

        PutDataMapRequest req = PutDataMapRequest.create(Protocol.PATH_NOW_PLAYING);
        req.getDataMap().putBoolean(Protocol.KEY_ACTIVE, c != null);
        String title = "", artist = "", album = "";
        boolean playing = false;
        if (c != null) {
            MediaMetadata m = c.getMetadata();
            if (m != null) {
                title = nz(m.getString(MediaMetadata.METADATA_KEY_TITLE));
                artist = nz(m.getString(MediaMetadata.METADATA_KEY_ARTIST));
                if (artist.isEmpty()) artist = nz(m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST));
                album = nz(m.getString(MediaMetadata.METADATA_KEY_ALBUM));
            }
            PlaybackState ps = c.getPlaybackState();
            playing = ps != null && ps.getState() == PlaybackState.STATE_PLAYING;
        }
        req.getDataMap().putString(Protocol.KEY_TITLE, title);
        req.getDataMap().putString(Protocol.KEY_ARTIST, artist);
        req.getDataMap().putString(Protocol.KEY_ALBUM, album);
        req.getDataMap().putBoolean(Protocol.KEY_PLAYING, playing);
        Wearable.getDataClient(ctx).putDataItem(req.asPutDataRequest().setUrgent());
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
