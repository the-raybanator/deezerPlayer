package com.deezerwatch.app;

import android.content.ComponentName;
import android.content.Context;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;

import java.util.List;

/** Finds the Deezer app's active media session (needs notification access to be granted). */
final class DeezerSession {
    private DeezerSession() {}

    /** @return Deezer's controller, or null if Deezer has no live session or access isn't granted. */
    static MediaController find(Context ctx) {
        MediaSessionManager msm = (MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
        try {
            List<MediaController> all = msm.getActiveSessions(new ComponentName(ctx, MediaWatcher.class));
            for (MediaController c : all) {
                if (Protocol.DEEZER_PACKAGE.equals(c.getPackageName())) return c;
            }
        } catch (SecurityException e) {
            // notification access not granted
        }
        return null;
    }
}
