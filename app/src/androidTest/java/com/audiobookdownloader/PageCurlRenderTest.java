package com.audiobookdownloader;

import android.content.Context;
import android.graphics.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Export actual Android Canvas frames from the same renderer used for navigation. */
@RunWith(AndroidJUnit4.class)
public class PageCurlRenderTest {
    @Test(timeout=60000) public void texturedCylinderRendersBothDirectionsAndExportsFrames() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("com.audiobookdownloader.preview",context.getPackageName());
        Bitmap texture=Bitmap.createBitmap(360,640,Bitmap.Config.ARGB_8888);
        Canvas source=new Canvas(texture);Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new LinearGradient(0,0,360,640,0xff123655,0xff603090,Shader.TileMode.CLAMP));source.drawPaint(paint);paint.setShader(null);
        paint.setColor(Color.WHITE);paint.setTextSize(28);source.drawText("Audiobook",24,65,paint);
        for(int i=0;i<6;i++){paint.setColor(i%2==0?0xff60dfff:0xffe3c5ff);source.drawRoundRect(24,110+i*70,336,160+i*70,14,14,paint);}
        PageCurlRenderer renderer=new PageCurlRenderer();
        for(int index=0;index<4;index++){
            float progress=new float[]{0,.15f,.32f,.62f}[index];
            for(boolean forward:new boolean[]{true,false}){
                Bitmap input=texture;
                if(!forward){Matrix mirror=new Matrix();mirror.setScale(-1,1);input=Bitmap.createBitmap(texture,0,0,360,640,mirror,true);}
                Bitmap frame=Bitmap.createBitmap(360,640,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(frame);canvas.drawColor(0xff06382d);
                renderer.draw(canvas,input,360,640,progress,.25f,0,0xffd8e7ed,forward);
                int colored=0;for(int y=0;y<640;y+=20)for(int x=0;x<360;x+=20)if(frame.getPixel(x,y)!=0xff06382d)colored++;
                assertTrue("Page texture disappeared",colored>40);
                String name="curl-cylinder-"+(forward?"left-":"right-")+index+".png";
                try(FileOutputStream out=new FileOutputStream(new File(context.getFilesDir(),name))){assertTrue(frame.compress(Bitmap.CompressFormat.PNG,100,out));}
                String output=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).executeShellCommand("sh -c 'run-as com.audiobookdownloader.preview cat files/"+name+" > /sdcard/Download/"+name+"'");
                assertTrue("Export failed: "+output,output.trim().isEmpty());
                frame.recycle();if(!forward)input.recycle();
            }
        }
        texture.recycle();
    }
}
