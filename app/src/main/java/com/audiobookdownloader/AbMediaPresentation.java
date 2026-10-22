package com.audiobookdownloader;

/** Samsung and AOSP use MediaSession metadata rather than notification text on Android 13+. */
public final class AbMediaPresentation {
    private AbMediaPresentation() {}
    public static String bookTitle(String raw) {
        String title=raw==null?"":raw.replaceFirst("(?iu)^\\s*Аудиокнига\\s+", "").trim();
        title=title.replaceFirst("(?iu)\\s*[-–—|·]?\\s*слушать(?:\\s+или\\s+скачать)?\\s+(?:онлайн|бесплатно).*$", "").trim();
        return title.isEmpty()?"Аудиокнига":title;
    }
    public static String chapter(int index,int count,String raw) {
        String label="Глава "+(Math.max(0,index)+1)+(count>0?" из "+count:"");
        String name=raw==null?"":raw.trim();
        if(name.isEmpty()||name.matches("(?iu)(?:глава\\s*)?0*"+(Math.max(0,index)+1)+"(?:\\.[a-z0-9]+)?"))return label;
        return label+" · "+name;
    }
}
