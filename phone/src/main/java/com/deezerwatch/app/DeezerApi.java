package com.deezerwatch.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/**
 * Reads a user's playlists from Deezer's public API. Runs on the phone only -
 * the watch never talks to the internet.
 *
 * With just a numeric user id this returns that user's public playlists.
 * With an access token (and id blank or "me") it also returns private ones.
 */
final class DeezerApi {
    private static final int MAX_PLAYLISTS = 300;

    private DeezerApi() {}

    /** @return array of {"id": long, "title": String, "n": int} */
    static JSONArray fetchPlaylists(String userId, String token) throws Exception {
        String who = userId.isEmpty() ? "me" : userId;
        if (who.equals("me") && token.isEmpty()) {
            throw new Exception("Enter your Deezer user id (or an access token)");
        }

        JSONArray out = new JSONArray();
        String url = withToken("https://api.deezer.com/user/" + who + "/playlists?limit=100", token);
        while (url != null && out.length() < MAX_PLAYLISTS) {
            JSONObject page = getJson(url);
            JSONArray data = page.optJSONArray("data");
            if (data == null) break;
            for (int i = 0; i < data.length() && out.length() < MAX_PLAYLISTS; i++) {
                JSONObject p = data.getJSONObject(i);
                String title = p.optString("title");
                if (title.isEmpty()) continue;
                JSONObject o = new JSONObject();
                o.put("id", p.getLong("id"));
                o.put("title", title);
                o.put("n", p.optInt("nb_tracks"));
                out.put(o);
            }
            url = page.isNull("next") ? null : withToken(page.optString("next"), token);
        }
        return out;
    }

    private static String withToken(String url, String token) throws Exception {
        if (token.isEmpty() || url.isEmpty() || url.contains("access_token=")) return url.isEmpty() ? null : url;
        return url + (url.contains("?") ? "&" : "?") + "access_token=" + URLEncoder.encode(token, "UTF-8");
    }

    private static JSONObject getJson(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            JSONObject o = new JSONObject(sb.toString());
            // Deezer reports errors as HTTP 200 with an "error" object.
            JSONObject err = o.optJSONObject("error");
            if (err != null) throw new Exception("Deezer: " + err.optString("message", "request failed"));
            return o;
        } finally {
            c.disconnect();
        }
    }
}
