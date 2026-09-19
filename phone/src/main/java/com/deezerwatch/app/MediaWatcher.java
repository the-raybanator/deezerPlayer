package com.deezerwatch.app;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.service.notification.NotificationListenerService;
import android.util.Log;

/**
 * Only exists so Android lets us see and control Deezer's media session (needs "notification
 * access"). It doesn't read your notifications - it tracks Deezer's play state and pushes it
 * to the watch.
 */
public class MediaWatcher extends NotificationListenerService {
    private static final String TAG = "DeezerWatch";

    private MediaSessionManager msm;
    private MediaController tracked;

    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            NowPlaying.publish(MediaWatcher.this);
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            NowPlaying.publish(MediaWatcher.this);
        }

        @Override
        public void onSessionDestroyed() {
            retrack();
        }
    };

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsListener =
            list -> retrack();

    @Override
    public void onListenerConnected() {
        msm = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
        try {
            msm.addOnActiveSessionsChangedListener(sessionsListener, new ComponentName(this, MediaWatcher.class));
        } catch (SecurityException e) {
            Log.w(TAG, "No notification access yet", e);
            return;
        }
        retrack();
    }

    @Override
    public void onListenerDisconnected() {
        if (msm != null) msm.removeOnActiveSessionsChangedListener(sessionsListener);
        untrack();
    }

    private void retrack() {
        untrack();
        MediaController c = DeezerSession.find(this);
        if (c != null) {
            c.registerCallback(callback);
            tracked = c;
        }
        NowPlaying.publish(this);
    }

    private void untrack() {
        if (tracked != null) tracked.unregisterCallback(callback);
        tracked = null;
    }
}
