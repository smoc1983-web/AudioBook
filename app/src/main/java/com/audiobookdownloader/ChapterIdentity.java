package com.audiobookdownloader;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.io.InputStream;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/** Versioned identities never use a list index or a lossy filesystem name. */
public final class ChapterIdentity {
    private ChapterIdentity() {}
    public static String hash(String value) {
        return hex(digest().digest(value.getBytes(StandardCharsets.UTF_8)));
    }
    public static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (java.security.NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
    public static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        for (byte b : bytes) out.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return out.toString();
    }
    private static String field(String s) { return s.length() + ":" + s; }
    public static String edition(String book, String url) {
        return "e2-" + hash(url.trim().isEmpty() ? field(book) : field(url.trim().replaceAll("#.*$", "").replaceAll("/+$", "")));
    }
    public static String chapter(String edition, String title) {
        return "c2-" + hash(field(edition) + field(title));
    }
    public static String download(String book, String title, String src, String editionUrl) {
        return "q2-" + hash(field(edition(book, editionUrl)) + field(title) + field(src));
    }
    public static String revision(List<String> chapters) {
        ArrayList<String> sorted = new ArrayList<>(chapters); Collections.sort(sorted);
        StringBuilder out = new StringBuilder();
        for (String chapter : sorted) out.append(field(chapter));
        return hash(out.toString());
    }
    /** Missing/ambiguous chapters must never receive another chapter's saved time. */
    public static int resolve(List<String> ids, String savedId) {
        if (savedId == null || savedId.isEmpty()) return -1;
        int at = ids.indexOf(savedId);
        return at >= 0 && ids.lastIndexOf(savedId) == at ? at : -1;
    }
    public static String conflictName(String name, String hash, int attempt) {
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        return base + " (restored-" + hash.substring(0, 12) + (attempt == 0 ? "" : "-" + attempt) + ")" + ext;
    }
    public static String checksum(InputStream in) throws IOException {
        MessageDigest digest = digest(); byte[] buffer = new byte[65536];
        int n; while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n);
        return hex(digest.digest());
    }
}
