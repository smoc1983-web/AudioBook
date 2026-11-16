package com.audiobookdownloader;

/** Unknown durations stay unknown; remaining time only needs current and future chapters. */
public final class AbBookTimeRules {
    public static final class Result {
        public final long remainingMs, elapsedMs, totalMs, chapterRemainingMs;
        public final int knownRemaining, totalRemaining;
        public final float progress;
        public final boolean approximate;

        Result(long remaining, long elapsed, long total, long chapterRemaining,
                int known, int count, float progress, boolean approximate) {
            this.remainingMs=remaining; this.elapsedMs=elapsed; this.totalMs=total;
            this.chapterRemainingMs=chapterRemaining; this.knownRemaining=known;
            this.totalRemaining=count; this.progress=progress; this.approximate=approximate;
        }
    }

    private static final class Totals {
        long before, after;
        int knownRemaining;
        boolean pastKnown=true, wholeKnown=true;

        Totals(long[] durations, int index) {
            for(int i=0; i<durations.length; i++) {
                long duration=durations[i];
                if(duration<=0) {
                    wholeKnown=false;
                    if(i<index) pastKnown=false;
                } else if(i<index) before+=duration;
                else { after+=duration; knownRemaining++; }
            }
        }
        long remaining(long position, double rate, int count) {
            return knownRemaining==count ? (long)(Math.max(0,after-position)/rate) : -1;
        }
        long elapsed(long position) { return pastKnown ? before+position : -1; }
        long total() { return wholeKnown ? before+after : -1; }
        float progress(int index, long position, long current, int count) {
            double fraction=wholeKnown ? (double)(before+position)/(before+after)
                : (index+chapterFraction(position,current))/count;
            return (float)Math.min(1,fraction);
        }
    }

    public static Result calculate(long[] durations, int index, long position, float speed) {
        return calculate(durations,index,position,speed,false);
    }

    public static Result calculate(long[] durations, int index, long position, float speed, boolean completed) {
        if(index<0 || index>=durations.length)
            return new Result(-1,-1,-1,-1,0,0,completed?1:0,!completed);
        double rate=rate(speed);
        long current=Math.max(0,durations[index]);
        long pos=clampPosition(position,current);
        Totals totals=new Totals(durations,index);
        int count=durations.length-index;
        return new Result(totals.remaining(pos,rate,count),totals.elapsed(pos),totals.total(),
            chapterRemaining(pos,current,rate),totals.knownRemaining,count,
            completed?1:totals.progress(index,pos,current,durations.length),!completed&&!totals.wholeKnown);
    }

    private static double rate(float speed) {
        return Float.isNaN(speed) || Float.isInfinite(speed) || speed<=0 ? 1 : speed;
    }
    private static long clampPosition(long position, long current) {
        return current>0 ? Math.min(Math.max(0,position),current) : Math.max(0,position);
    }
    private static double chapterFraction(long position, long current) {
        return current>0 ? (double)position/current : 0;
    }
    private static long chapterRemaining(long position, long current, double rate) {
        return current>0 ? (long)((current-position)/rate) : -1;
    }

    public static String time(long ms) {
        long seconds=Math.max(0,ms)/1000;
        return seconds>=3600 ? String.format(java.util.Locale.ROOT,"%d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60)
            : String.format(java.util.Locale.ROOT,"%d:%02d",seconds/60,seconds%60);
    }
}
