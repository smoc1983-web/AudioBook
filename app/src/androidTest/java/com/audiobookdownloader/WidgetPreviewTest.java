package com.audiobookdownloader;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RemoteViews;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class WidgetPreviewTest {
    @Test(timeout=60000) public void previewsHaveIntrinsicHeightAndRenderAllTenThemes() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        int original=context.getSharedPreferences("audiobook_downloader",0).getInt("glass_theme",2);
        try {
            for(int theme=1;theme<=10;theme++) {
                context.getSharedPreferences("audiobook_downloader",0).edit().putInt("glass_theme",theme).commit();
                for(int kind=0;kind<3;kind++) {
                    final int selected=kind, selectedTheme=theme;
                    final Bitmap[] result=new Bitmap[1];
                    InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                        int[] layouts={R.layout.ab_widget_preview_compact,R.layout.ab_widget_preview_cover,R.layout.ab_widget_preview_player};
                        View declared=LayoutInflater.from(context).inflate(layouts[selected],new FrameLayout(context),false);
                        declared.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
                        float density=context.getResources().getDisplayMetrics().density;
                        assertTrue("Launcher preview collapsed to a strip",declared.getMeasuredHeight()>=(selected==0?64:150)*density);
                        RemoteViews remote=AbGlassWidgets.INSTANCE.previewKind(context,selected);
                        View view=remote.apply(context,new FrameLayout(context));
                        int width=(int)((selected==1?190:360)*density),height=(int)((selected==0?80:200)*density);
                        view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
                        view.layout(0,0,width,height);
                        android.graphics.Rect coverBounds=new android.graphics.Rect();
                        View cover=view.findViewById(R.id.ab_widget_cover);cover.getDrawingRect(coverBounds);
                        ((android.view.ViewGroup)view).offsetDescendantRectToMyCoords(cover,coverBounds);
                        android.graphics.Rect backgroundBounds=new android.graphics.Rect();
                        View background=view.findViewById(R.id.ab_widget_background);background.getDrawingRect(backgroundBounds);
                        ((android.view.ViewGroup)view).offsetDescendantRectToMyCoords(background,backgroundBounds);
                        assertTrue("Cover crosses the widget border",backgroundBounds.contains(coverBounds));
                        if(selected==2){
                            View previous=view.findViewById(R.id.ab_widget_previous),timer=view.findViewById(R.id.ab_widget_timer);
                            assertTrue("Player controls leave unused row width",timer.getRight()-previous.getLeft()>=((View)previous.getParent()).getWidth()*.95f);
                            assertTrue("Large widget cover did not use available width",cover.getWidth()>=width*.2f);
                            assertTrue("Large widget cover did not use available height",cover.getHeight()>=height*.7f);
                        }
                        assertEquals("Ваша аудиокнига",((TextView)view.findViewById(R.id.ab_widget_title)).getText().toString());
                        assertTrue("Chapter missing",((TextView)view.findViewById(R.id.ab_widget_chapter)).getText().toString().contains("Глава 1"));
                        result[0]=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);view.draw(new Canvas(result[0]));
                        int filled=0;for(int y=0;y<height;y+=8)for(int x=0;x<width;x+=8)if((result[0].getPixel(x,y)>>>24)>0)filled++;
                        assertTrue("Blank widget for theme "+selectedTheme,filled>width*height/256);
                    });
                    if(kind==2)InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                        android.os.Bundle options=new android.os.Bundle();
                        options.putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,280);
                        options.putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,150);
                        options.putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,150);
                        View compact=AbGlassWidgets.INSTANCE.previewKind(context,2,options).apply(context,new FrameLayout(context));
                        float density=context.getResources().getDisplayMetrics().density;
                        int cw=(int)(280*density),ch=(int)(150*density);
                        compact.measure(View.MeasureSpec.makeMeasureSpec(cw,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(ch,View.MeasureSpec.EXACTLY));
                        compact.layout(0,0,cw,ch);
                        assertTrue("Resized widget lost its controls",compact.findViewById(R.id.ab_widget_toggle).getHeight()>0);
                        View toggle=compact.findViewById(R.id.ab_widget_toggle);
                        android.graphics.Rect bounds=new android.graphics.Rect();toggle.getDrawingRect(bounds);
                        ((android.view.ViewGroup)compact).offsetDescendantRectToMyCoords(toggle,bounds);
                        assertTrue("Resized controls outside widget",new android.graphics.Rect(0,0,cw,ch).contains(bounds));
                        // Real launchers send both orientation ranges; a small landscape height
                        // must not force the portrait widget into the small layout.
                        options.putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,360);
                        options.putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,480);
                        options.putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,200);
                        android.content.res.Configuration config=new android.content.res.Configuration(context.getResources().getConfiguration());
                        config.orientation=android.content.res.Configuration.ORIENTATION_PORTRAIT;
                        Context portrait=context.createConfigurationContext(config);
                        View full=AbGlassWidgets.INSTANCE.previewKind(portrait,2,options).apply(portrait,new FrameLayout(portrait));
                        int pw=(int)(360*density),ph=(int)(200*density);
                        full.measure(View.MeasureSpec.makeMeasureSpec(pw,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(ph,View.MeasureSpec.EXACTLY));
                        full.layout(0,0,pw,ph);
                        assertTrue("Landscape range selected the wrong portrait layout",full.findViewById(R.id.ab_widget_cover).getHeight()>=ph*.7f);

                    });
                    if(theme==2)TestBitmapExports.write(context,result[0],"preview-widget-"+kind+".png");
                    result[0].recycle();
                }
            }
        } finally {context.getSharedPreferences("audiobook_downloader",0).edit().putInt("glass_theme",original).commit();}
    }
}
