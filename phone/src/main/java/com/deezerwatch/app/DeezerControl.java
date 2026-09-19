package com.deezerwatch.app;

import android.content.Context;
import android.content.Intent;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.net.Uri;

/**
 * Drives the Deezer app on the phone. Every method returns a short status line
 * that is shown on the watch.
 */
final class DeezerControl {
    private DeezerControl() {}

    static String transport(Context ctx, String cmd) {
        MediaController c = DeezerSession.find(ctx);
        if (c == null) {
            if (Protocol.CMD_PLAY.equals(cmd) || Protocol.CMD_TOGGLE.equals(cmd)) {
                return launchDeezer(ctx, null) ? "Opening Deezer - pick a playlist" : "Deezer isn't running";
            }
            return "Deezer isn't running";
        }
        MediaController.TransportControls t = c.getTransportControls();
        switch (cmd) {
            case Protocol.CMD_PLAY:
                t.play();
                break;
            case Protocol.CMD_PAUSE:
                t.pause();
                break;
            case Protocol.CMD_TOGGLE:
                PlaybackState ps = c.getPlaybackState();
                if (ps != null && ps.getState() == PlaybackState.STATE_PLAYING) t.pause();
                else t.play();
                break;
            case Protocol.CMD_NEXT:
                t.skipToNext();
                break;
            case Protocol.CMD_PREV:
                t.skipToPrevious();
                break;
            default:
                return "Unknown command";
        }
        return "";
    }

    /**
     * Starts a playlist in the Deezer app by its id. Deezer's own deep link supports
     * "?autoplay=true", which begins playback (works whether or not Deezer is running).
     * Playing by name instead can match a different playlist with the same title.
     */
    static String playPlaylist(Context ctx, long id, String title) {
        Uri uri = Uri.parse("deezer://www.deezer.com/playlist/" + id + "?autoplay=true");
        if (launchDeezer(ctx, uri)) return "Playing " + title;
        return "Couldn't open Deezer (allow \"Display over other apps\" on the phone)";
    }

    private static boolean launchDeezer(Context ctx, Uri deepLink) {
        Intent i = deepLink != null
                ? new Intent(Intent.ACTION_VIEW, deepLink).setPackage(Protocol.DEEZER_PACKAGE)
                : ctx.getPackageManager().getLaunchIntentForPackage(Protocol.DEEZER_PACKAGE);
        if (i == null) return false;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(i);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
