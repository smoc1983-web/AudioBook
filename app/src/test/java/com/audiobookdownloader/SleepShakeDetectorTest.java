package com.audiobookdownloader;
import org.junit.Test;
import static org.junit.Assert.*;

public class SleepShakeDetectorTest {
    private long time=10_000_000_000L;
    private boolean sample(SleepShakeDetector d,float x,float y,float z){time+=20_000_000;return d.sample(time,x,y,z);}
    private boolean pulse(SleepShakeDetector d,float strength){
        boolean found=false;
        for(int i=0;i<4;i++)found|=sample(d,strength,0,9.80665f);
        for(int i=0;i<6;i++)found|=sample(d,0,0,9.80665f);
        return found;
    }
    @Test public void twoDeliberateMotionsRestart(){SleepShakeDetector d=new SleepShakeDetector();sample(d,0,0,9.80665f);assertFalse(pulse(d,14));assertTrue(pulse(d,-14));}
    @Test public void restingAndWalkingDoNotRestart(){SleepShakeDetector d=new SleepShakeDetector();for(int i=0;i<500;i++)assertFalse(sample(d,(float)Math.sin(i*.18)*2.8f,0,9.80665f));}
    @Test public void aSingleJoltDoesNotRestart(){SleepShakeDetector d=new SleepShakeDetector();sample(d,0,0,9.80665f);assertFalse(pulse(d,18));for(int i=0;i<100;i++)assertFalse(sample(d,0,0,9.80665f));}
    @Test public void pulsesOutsideWindowDoNotAccumulate(){SleepShakeDetector d=new SleepShakeDetector();sample(d,0,0,9.80665f);assertFalse(pulse(d,14));for(int i=0;i<80;i++)assertFalse(sample(d,0,0,9.80665f));assertFalse(pulse(d,-14));}
    @Test public void cooldownSurvivesTimerReset(){SleepShakeDetector d=new SleepShakeDetector();sample(d,0,0,9.80665f);pulse(d,14);assertTrue(pulse(d,-14));d.resetMotion();sample(d,0,0,9.80665f);assertFalse(pulse(d,14));assertFalse(pulse(d,-14));}
    @Test public void newShakeAfterCooldownIsAccepted(){SleepShakeDetector d=new SleepShakeDetector();sample(d,0,0,9.80665f);pulse(d,14);assertTrue(pulse(d,-14));for(int i=0;i<150;i++)sample(d,0,0,9.80665f);assertFalse(pulse(d,14));assertTrue(pulse(d,-14));}
    @Test public void orientationDoesNotRequireUprightPhone(){SleepShakeDetector d=new SleepShakeDetector();sample(d,9.80665f,0,0);boolean triggered=false;for(int sign:new int[]{1,-1}){for(int i=0;i<4;i++)triggered|=sample(d,9.80665f,sign*14,0);for(int i=0;i<6;i++)triggered|=sample(d,9.80665f,0,0);}assertTrue(triggered);}
    @Test public void invalidSamplesAndLongGapsDoNotTrigger(){SleepShakeDetector d=new SleepShakeDetector();assertFalse(d.sample(time,Float.NaN,0,0));sample(d,0,0,9.80665f);pulse(d,14);time+=3_000_000_000L;assertFalse(sample(d,-14,0,9.80665f));}
}
