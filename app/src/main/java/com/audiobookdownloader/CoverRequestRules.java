package com.audiobookdownloader;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class CoverRequestRules {
    private static final int[] EDGES = {320, 480, 720, 960, 1280, 1536};
    private static final Pattern AUTHOR_THUMB = Pattern.compile("width=([0-9]{1,4})&height=([0-9]{1,4})&rmode=max");

    private CoverRequestRules() {}

    static int edge(int screenPixels, boolean largePlayer) {
        int size = Math.max(screenPixels, largePlayer ? 960 : 320);
        for (int edge : EDGES) if (edge >= size) return edge;
        return EDGES[EDGES.length - 1];
    }

    static String sharper(String address, int edge) {
        if (!NetworkAddressRules.accepts(address)) return address;
        URI uri = URI.create(address);
        if (!"https".equals(uri.getScheme()) || !"cm.author.today".equals(uri.getHost())
            || uri.getPort() != -1 || !uri.getRawPath().startsWith("/content/")) return address;
        String query = uri.getRawQuery();
        if (query == null) return address;
        Matcher thumb = AUTHOR_THUMB.matcher(query);
        if (!thumb.matches()) return address;
        int width = Math.max(edge * 2 / 3, Integer.parseInt(thumb.group(1)));
        int height = Math.max(edge, Integer.parseInt(thumb.group(2)));
        return "https://cm.author.today" + uri.getRawPath() + "?width=" + width + "&height=" + height + "&rmode=max";
    }
}
