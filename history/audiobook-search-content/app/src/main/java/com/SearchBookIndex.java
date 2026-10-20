package com.audiobookdownloader;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure data index: no Android, disk, UI or network calls. One instance per merge. */
public final class SearchBookIndex {
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern SPACE = Pattern.compile("\\s+");
    private static final Pattern SUFFIX = Pattern.compile("\\s*[-–—|]\\s*(?:bookinfo|книгоблуд)(?:(?![\\p{L}\\p{N}]).*)$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern ROMAN = Pattern.compile("(?<![\\p{L}\\p{N}])(том|книга|часть|vol|book)\\s*([ivxlcdm]+)(?![\\p{L}\\p{N}])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern VOLUME = Pattern.compile("(?<![\\p{L}\\p{N}])(том|книга|часть|vol|book)\\s*(\\d+)(?![\\p{L}\\p{N}])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    public static String normal(String value) {
        String normalized=Normalizer.normalize(value==null?"":value,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replace('ё','е');
        return SPACE.matcher(NON_WORD.matcher(normalized).replaceAll(" ").trim()).replaceAll(" ");
    }
    public static String titleKey(String title) {
        String clean=SUFFIX.matcher(title==null?"":title).replaceAll("");
        Matcher roman=ROMAN.matcher(clean);
        StringBuffer converted=new StringBuffer();
        while(roman.find()) {
            String digits=roman.group(2).toLowerCase(Locale.ROOT);
            int value=0;
            for(int i=0;i<digits.length();i++) {
                int n=digit(digits.charAt(i));
                value+=i+1<digits.length()&&n<digit(digits.charAt(i+1))?-n:n;
            }
            roman.appendReplacement(converted,Matcher.quoteReplacement(roman.group(1)+" "+value));
        }
        roman.appendTail(converted);
        return normal(VOLUME.matcher(converted.toString()).replaceAll("$1 $2"));
    }
    private static int digit(char c) {
        switch(c){case 'i':return 1;case 'v':return 5;case 'x':return 10;case 'l':return 50;case 'c':return 100;case 'd':return 500;case 'm':return 1000;default:return 0;}
    }
    public static final class Keys {
        public final String title,author;
        private final Set<String> variants;
        private Keys(String raw,String authorPhrase) {
            String key=raw;
            if(!authorPhrase.isEmpty()) {
                if(key.startsWith(authorPhrase+" "))key=key.substring(authorPhrase.length()+1);
                if(key.endsWith(" "+authorPhrase))key=key.substring(0,key.length()-authorPhrase.length()-1);
            }
            title=key;
            String[] words=authorPhrase.isEmpty()?new String[0]:authorPhrase.split(" ");
            Arrays.sort(words);
            StringBuilder sorted=new StringBuilder();
            for(String word:words){if(sorted.length()>0)sorted.append(' ');sorted.append(word);}
            author=sorted.toString();
            variants=new LinkedHashSet<>();
            variants.add(key);variants.add(raw);
            if(!authorPhrase.isEmpty()){variants.add(authorPhrase+" "+key);variants.add(key+" "+authorPhrase);}
        }
    }
    public static Keys keys(String cleanTitle,String author){return new Keys(titleKey(cleanTitle),normal(author));}
    private static final class Bucket {
        final Map<String,TreeSet<Integer>> sites=new HashMap<>();
        void add(int index,Set<String> values){for(String site:values){TreeSet<Integer> ids=sites.get(site);if(ids==null){ids=new TreeSet<>();sites.put(site,ids);}ids.add(index);}}
        void remove(int index,Set<String> values){for(String site:values){TreeSet<Integer> ids=sites.get(site);if(ids!=null){ids.remove(index);if(ids.isEmpty())sites.remove(site);}}}
        int firstOther(Set<String> incoming){int result=Integer.MAX_VALUE;for(Map.Entry<String,TreeSet<Integer>> entry:sites.entrySet())
            if(incoming.size()>1||!incoming.contains(entry.getKey()))result=Math.min(result,entry.getValue().first());return result;}
    }
    private static final class Registered {
        final Keys keys;final Set<String> sites;
        Registered(Keys keys,Set<String> sites){this.keys=keys;this.sites=sites;}
    }
    private final Map<String,Integer> urls=new HashMap<>();
    private final Map<String,Bucket> all=new HashMap<>(),authors=new HashMap<>();
    private final Map<Integer,Registered> registered=new HashMap<>();
    private static String pair(String title,String author){return title+'\u0000'+author;}
    private static Set<String> sites(String[] values){return new HashSet<>(Arrays.asList(values));}
    private static Bucket bucket(Map<String,Bucket> map,String key){Bucket b=map.get(key);if(b==null){b=new Bucket();map.put(key,b);}return b;}
    public int find(Keys keys,String[] sourceSites,String[] addresses) {
        int result=Integer.MAX_VALUE;
        for(String address:addresses){if(address==null||address.isEmpty())continue;Integer found=urls.get(address);if(found!=null)result=Math.min(result,found);}
        if(result!=Integer.MAX_VALUE)return result;
        if(keys.title.isEmpty())return -1;
        Set<String> incoming=sites(sourceSites);
        for(String title:keys.variants) {
            if(keys.author.isEmpty()) {
                Bucket b=all.get(title);if(b!=null)result=Math.min(result,b.firstOther(incoming));
            }else for(String author:new String[]{keys.author,""}) {
                Bucket b=authors.get(pair(title,author));if(b!=null)result=Math.min(result,b.firstOther(incoming));
            }
        }
        return result==Integer.MAX_VALUE?-1:result;
    }
    public void put(int index,Keys keys,String[] sourceSites,String[] addresses) {
        Registered old=registered.remove(index);
        if(old!=null)for(String title:old.keys.variants){
            remove(all,title,index,old.sites);remove(authors,pair(title,old.keys.author),index,old.sites);
        }
        Set<String> values=sites(sourceSites);
        for(String title:keys.variants){bucket(all,title).add(index,values);bucket(authors,pair(title,keys.author)).add(index,values);}
        for(String address:addresses)if(address!=null&&!address.isEmpty())urls.put(address,index);
        registered.put(index,new Registered(keys,values));
    }
    private static void remove(Map<String,Bucket> map,String key,int index,Set<String> sites) {
        Bucket b=map.get(key);if(b==null)return;b.remove(index,sites);if(b.sites.isEmpty())map.remove(key);
    }
}
