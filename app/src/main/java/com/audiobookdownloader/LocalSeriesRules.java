package com.audiobookdownloader;
import java.util.*;import java.util.regex.*;
/** Local filename grouping is labelled as such; it never proves a publication or creates an alert. */
public final class LocalSeriesRules {
 public static String base(String title){String key=SearchBookIndex.titleKey(SeriesAuthorityRules.queryTitle(title));return key.replaceFirst("\\s+(?:том|книга)\\s+\\d+(?:\\s.*)?$","").trim();}
 public static List<String> siblings(String title,Collection<String> titles){String group=base(title);List<String> found=new ArrayList<>();for(String t:titles)if(!group.isEmpty()&&group.equals(base(t)))found.add(t);if(found.stream().noneMatch(t->ContinuationRules.order(t)!=null))return Collections.emptyList();found.sort(Comparator.comparingDouble(t->{Double n=ContinuationRules.order(t);return n==null?1:n;}));return found;}
}
