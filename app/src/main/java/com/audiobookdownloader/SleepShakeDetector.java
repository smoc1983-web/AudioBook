package com.audiobookdownloader;

/** Sensor-time based detector. Gravity removal makes shaking independent of phone orientation. */
public final class SleepShakeDetector {
    private long previousNs, firstHitMs, lastHitMs, lastTriggerMs=Long.MIN_VALUE;
    private float gravityX,gravityY,gravityZ;
    private int hits;
    private boolean initialized,above;
    public void resetMotion(){previousNs=0;firstHitMs=0;lastHitMs=0;hits=0;above=false;initialized=false;}
    public boolean sample(long timestampNs,float x,float y,float z){
        if(Float.isNaN(x)||Float.isInfinite(x)||Float.isNaN(y)||Float.isInfinite(y)||Float.isNaN(z)||Float.isInfinite(z)||timestampNs<=0)return false;
        if(!initialized||timestampNs<=previousNs||timestampNs-previousNs>1_000_000_000L){
            gravityX=x;gravityY=y;gravityZ=z;previousNs=timestampNs;initialized=true;above=false;hits=0;return false;
        }
        float dt=(timestampNs-previousNs)/1_000_000_000f;previousNs=timestampNs;
        float alpha=(float)Math.exp(-dt/.28f);
        gravityX=alpha*gravityX+(1-alpha)*x;
        gravityY=alpha*gravityY+(1-alpha)*y;
        gravityZ=alpha*gravityZ+(1-alpha)*z;
        float dx=x-gravityX,dy=y-gravityY,dz=z-gravityZ;
        float force=(float)Math.sqrt(dx*dx+dy*dy+dz*dz)/9.80665f;
        long now=timestampNs/1_000_000L;
        if(force<.48f){above=false;return false;}
        if(force<.85f||above)return false;
        above=true;
        if(lastTriggerMs!=Long.MIN_VALUE&&now-lastTriggerMs<2500)return false;
        if(hits>0&&now-lastHitMs<120)return false;
        if(hits==0||now-firstHitMs>1400){hits=0;firstHitMs=now;}
        lastHitMs=now;
        if(++hits<2)return false;
        lastTriggerMs=now;hits=0;return true;
    }
}
