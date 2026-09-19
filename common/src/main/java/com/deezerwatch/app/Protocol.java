package com.deezerwatch.app;

/**
 * Wire format shared by the phone and watch apps (compiled into both).
 *
 * Watch -> phone: messages under /request. Nothing is expected back on the same call.
 * Phone -> watch: state as data items (/data/...) so the watch can read the last value
 * at any time, plus short status strings as messages on /result.
 */
final class Protocol {
    private Protocol() {}

    static final String DEEZER_PACKAGE = "deezer.android.app";

    // watch -> phone (messages)
    static final String PATH_REQUEST_PLAYLISTS = "/request/playlists";
    /** payload: "<playlistId>\n<title>" (UTF-8) */
    static final String PATH_PLAY_PLAYLIST = "/request/play_playlist";
    /** payload: one of the CMD_* strings (UTF-8) */
    static final String PATH_TRANSPORT = "/request/transport";
    static final String PATH_REQUEST_STATE = "/request/state";

    static final String CMD_PLAY = "play";
    static final String CMD_PAUSE = "pause";
    static final String CMD_TOGGLE = "toggle";
    static final String CMD_NEXT = "next";
    static final String CMD_PREV = "prev";

    // phone -> watch
    /** message; payload is a human-readable status line shown as a toast */
    static final String PATH_RESULT = "/result";
    /** data item: KEY_JSON = [{"id":123,"title":"..","n":42}, ...], KEY_UPDATED = ms since epoch */
    static final String PATH_PLAYLISTS = "/data/playlists";
    /** data item: KEY_ACTIVE, KEY_TITLE, KEY_ARTIST, KEY_ALBUM, KEY_PLAYING */
    static final String PATH_NOW_PLAYING = "/data/now_playing";

    static final String KEY_JSON = "json";
    static final String KEY_UPDATED = "updated";
    static final String KEY_ERROR = "error";
    static final String KEY_ACTIVE = "active";
    static final String KEY_TITLE = "title";
    static final String KEY_ARTIST = "artist";
    static final String KEY_ALBUM = "album";
    static final String KEY_PLAYING = "playing";
}
