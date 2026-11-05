package com.audiobookdownloader;
import org.junit.Test;
import static org.junit.Assert.*;
public class PlaybackOptionsTest {
    @Test public void safeRangeIncludesFourTimesAndRejectsInvalidValues() {
        assertEquals(4f,PlaybackOptions.rate(4f),0f);
        assertEquals(.5f,PlaybackOptions.rate(-1f),0f);
        assertEquals(4f,PlaybackOptions.rate(9f),0f);
        assertEquals(1f,PlaybackOptions.rate(Float.NaN),0f);
        assertEquals(1f,PlaybackOptions.rate(Float.POSITIVE_INFINITY),0f);
    }
    @Test public void fineAndNormalStepsStayExactAndStopAtBounds() {
        float value=1f;
        for(int i=0;i<20;i++)value=PlaybackOptions.adjust(value,1,true);
        assertEquals(2f,value,0f);
        assertEquals(1.1f,PlaybackOptions.adjust(1f,1,false),0f);
        assertEquals(.95f,PlaybackOptions.adjust(1f,-1,true),0f);
        assertEquals(.5f,PlaybackOptions.adjust(.5f,-1,false),0f);
        assertEquals(4f,PlaybackOptions.adjust(4f,1,true),0f);
    }
    @Test public void customTimerAcceptsMinutesAndRefusesInvalidOrExcessiveInput() {
        assertEquals(1200,PlaybackOptions.timerSeconds("20"));
        assertEquals(60,PlaybackOptions.timerSeconds(" 1 "));
        assertEquals(86400,PlaybackOptions.timerSeconds("1440"));
        for(String s:new String[]{"", "0", "-1", "1.5", "1441", "999999999999999", "abc"})
            assertEquals(s,0,PlaybackOptions.timerSeconds(s));
    }
}
