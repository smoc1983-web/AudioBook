package com.audiobookdownloader;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import java.io.IOException;
import java.io.OutputStream;

/** Instrumentation-only export through Android scoped storage; no production file commands. */
final class TestBitmapExports {
    static void write(Context context,Bitmap bitmap,String name) throws IOException {
        ContentValues values=new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME,name);
        values.put(MediaStore.MediaColumns.MIME_TYPE,"image/png");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH,"Download/");
        values.put(MediaStore.MediaColumns.IS_PENDING,1);
        Uri uri=context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
        if(uri==null)throw new IOException("Cannot create "+name);
        try(OutputStream out=context.getContentResolver().openOutputStream(uri)){
            if(out==null||!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("Cannot export "+name);
        }
        values.clear();values.put(MediaStore.MediaColumns.IS_PENDING,0);
        context.getContentResolver().update(uri,values,null,null);
    }
}
