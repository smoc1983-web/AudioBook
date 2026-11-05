package com.audiobookdownloader;
public final class PlaybackOptions {
    private PlaybackOptions() {}
    public static float rate(float value) {
        if(Float.isNaN(value)||Float.isInfinite(value))return 1f;
        return Math.round(Math.max(.5f,Math.min(4f,value))*100)/100f;
    }
    public static float adjust(float value,int direction,boolean fine) {
        return rate((Math.round(rate(value)*100)+Integer.signum(direction)*(fine?5:10))/100f);
    }
    public static int timerSeconds(String minutes) {
        try {
            int value=Integer.parseInt(minutes.trim());
            return value>=1 && value<=1440 ? value*60 : 0;
        }catch(NumberFormatException e){return 0;}
    }
}
