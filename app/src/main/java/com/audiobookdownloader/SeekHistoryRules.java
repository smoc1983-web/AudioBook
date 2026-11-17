package com.audiobookdownloader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Bounded local return points: IDs only, no playback URLs or user accounts. */
final class SeekHistoryRules {
    static final int LIMIT=50;
    static final class Entry {
        final String id, edition, chapter;
        final int from, to;
        final long time;
        Entry(String id,String edition,String chapter,int from,int to,long time) {
            if(id==null || !id.matches("[0-9a-f]{32}") || edition==null || !edition.matches("e2-[0-9a-f]{64}") ||
               chapter==null || !chapter.matches("c2-[0-9a-f]{64}") || from<0 || to<0 || time<0)
                throw new IllegalArgumentException("Invalid seek return point");
            this.id=id;this.edition=edition;this.chapter=chapter;this.from=from;this.to=to;this.time=time;
        }
    }
    static List<Entry> append(List<Entry> previous,Entry point) {
        if(point.from==point.to)return Collections.unmodifiableList(new ArrayList<>(previous));
        for(Entry entry:previous)if(entry.id.equals(point.id))return Collections.unmodifiableList(new ArrayList<>(previous));
        List<Entry> result=new ArrayList<>();result.add(point);
        result.addAll(previous.subList(0,Math.min(previous.size(),LIMIT-1)));
        return Collections.unmodifiableList(result);
    }
    static List<Entry> edition(List<Entry> entries,String edition) {
        List<Entry> result=new ArrayList<>();
        for(Entry entry:entries)if(entry.edition.equals(edition))result.add(entry);
        return Collections.unmodifiableList(result);
    }
    static int resolve(Entry point,String edition,List<String> chapters) {
        return point.edition.equals(edition) ? ChapterIdentity.resolve(chapters,point.chapter) : -1;
    }
}
