package com.audiobookdownloader;
import java.util.*;
/** A bookId identifies a library entry, not a guessed universal work across recordings. */
public final class LibraryRecordRules {
    private LibraryRecordRules() {}
    public static String bookId(String editionId) {
        if(editionId==null||editionId.trim().isEmpty())throw new IllegalArgumentException("Missing edition");
        return "b3-"+ChapterIdentity.hash(editionId);
    }
    public static boolean provesCatalogue(String edition,List<String> titles,String revision) {
        if(titles.isEmpty()||new HashSet<>(titles).size()!=titles.size())return false;
        for(String title:titles)if(title==null||title.trim().isEmpty())return false;
        ArrayList<String> chapters=new ArrayList<>();
        for(String title:titles)chapters.add(ChapterIdentity.chapter(edition,title));
        return ChapterIdentity.revision(chapters).equals(revision);
    }
}
