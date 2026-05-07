package com.flipphoneguy.f21bands;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Minimal GitHub releases client. {@link #fetchLatest(String, String)} blocks on
 * HTTP and parses the {@code releases/latest} response, picking the first
 * matching {@code .apk} asset. Designed to run on a worker thread.
 *
 * Version comparison via {@link #isNewer(String, String)} treats a {@code v}
 * prefix as optional and any non-digit suffix on a numeric component as zero,
 * so {@code v1.0.1}, {@code 1.0.1}, and {@code 1.0.1-rc} all parse to
 * {@code [1,0,1]}.
 */
public final class Updater {

    public static final class Release {
        public final String tag;
        public final String downloadUrl;
        public final String body;
        public final String publishedAt;

        public Release(String tag, String downloadUrl, String body, String publishedAt) {
            this.tag = tag;
            this.downloadUrl = downloadUrl;
            this.body = body;
            this.publishedAt = publishedAt;
        }
    }

    private Updater() {}

    public static Release fetchLatest(String repo, String assetName) throws Exception {
        URL url = new URL("https://api.github.com/repos/" + repo + "/releases/latest");
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("User-Agent", "f21bandsswap-updater");
        c.setConnectTimeout(10000);
        c.setReadTimeout(10000);
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new RuntimeException("HTTP " + code + " from GitHub");

            StringBuilder sb = new StringBuilder();
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();

            JSONObject json = new JSONObject(sb.toString());
            String tag = json.getString("tag_name");
            String body = json.optString("body", "");
            String publishedAt = json.optString("published_at", "");
            String downloadUrl = "";

            JSONArray assets = json.optJSONArray("assets");
            if (assets != null) {
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject a = assets.getJSONObject(i);
                    String name = a.getString("name");
                    if (!name.endsWith(".apk")) continue;
                    if (assetName != null && !assetName.equals(name)) continue;
                    downloadUrl = a.getString("browser_download_url");
                    break;
                }
            }
            return new Release(tag, downloadUrl, body, publishedAt);
        } finally {
            c.disconnect();
        }
    }

    public static boolean isNewer(String latest, String current) {
        if (current == null || current.isEmpty()) return true;
        int[] l = parse(latest);
        int[] c = parse(current);
        int n = Math.max(l.length, c.length);
        for (int i = 0; i < n; i++) {
            int li = i < l.length ? l[i] : 0;
            int ci = i < c.length ? c[i] : 0;
            if (li > ci) return true;
            if (li < ci) return false;
        }
        return false;
    }

    private static int[] parse(String v) {
        if (v == null) return new int[]{0};
        String t = v.trim();
        if (t.startsWith("v") || t.startsWith("V")) t = t.substring(1);
        String[] parts = t.split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            StringBuilder d = new StringBuilder();
            for (int j = 0; j < parts[i].length(); j++) {
                char ch = parts[i].charAt(j);
                if (ch < '0' || ch > '9') break;
                d.append(ch);
            }
            try {
                out[i] = d.length() > 0 ? Integer.parseInt(d.toString()) : 0;
            } catch (NumberFormatException e) {
                out[i] = 0;
            }
        }
        return out;
    }
}
