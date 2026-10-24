package com.audiobookdownloader;

/** Unknown durations stay unknown; remaining time only needs the current and future chapters. */
public final class AbBookTimeRules {
    public static final class Result {
        public final long remainingMs,elapsedMs,totalMs,chapterRemainingMs;
        public final int knownRemaining,totalRemaining;
        public final float progress;
        Result(long r,long e,long t,long c,int k,int n,float p){remainingMs=r;elapsedMs=e;totalMs=t;chapterRemainingMs=c;knownRemaining=k;totalRemaining=n;progress=p;}
    }
    public static Result calculate(long[] durations,int index,long position,float speed){
        if(index<0||index>=durations.length)return new Result(-1,-1,-1,-1,0,0,0);
        double rate=Float.isNaN(speed)||Float.isInfinite(speed)||speed<=0?1:speed;
        long current=Math.max(0,durations[index]);
        long pos=current>0?Math.min(Math.max(0,position),current):Math.max(0,position);
        long before=0,after=0;int known=0;boolean past=true,whole=true;
        for(int i=0;i<durations.length;i++){
            long d=durations[i];if(d<=0){whole=false;if(i<index)past=false;continue;}
            if(i<index)before+=d;else {after+=d;known++;}
        }
        int count=durations.length-index;
        long remaining=known==count?(long)(Math.max(0,after-pos)/rate):-1;
        long elapsed=past?before+pos:-1;
        long total=whole?before+after:-1;
        float progress=total>0?Math.min(1,(float)((double)(before+pos)/total)):
            Math.min(1,(index+(current>0?(float)pos/current:0))/durations.length);
        return new Result(remaining,elapsed,total,current>0?(long)((current-pos)/rate):-1,known,count,progress);
    }
    public static String time(long ms){
        long seconds=Math.max(0,ms)/1000;
        return seconds>=3600?String.format(java.util.Locale.ROOT,"%d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60):
            String.format(java.util.Locale.ROOT,"%d:%02d",seconds/60,seconds%60);
    }
}
