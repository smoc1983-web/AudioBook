package com.audiobookdownloader;

import android.content.*;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;

/** Framework-only: runs in the separate instrumentation APK process, with no app dependencies. */
public final class SharedAudioFixtureProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    private byte[] data(Uri uri) {
        String id=uri.getLastPathSegment();
        if("html".equals(id))return "<!doctype html><html>503 service error</html>".getBytes(StandardCharsets.UTF_8);
        if("empty".equals(id))return new byte[0];
        ByteBuffer b=ByteBuffer.allocate(16044).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(16036).put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII))
         .putInt(16).putShort((short)1).putShort((short)1).putInt(8000).putInt(16000).putShort((short)2).putShort((short)16)
         .put("data".getBytes(StandardCharsets.US_ASCII)).putInt(16000);
        int seed=uri.getPath().hashCode();for(int n=0;n<8000;n++)b.putShort((short)((seed+n*13)%3000));return b.array();
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort) {
        String[] cols=projection==null ? new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE} : projection;
        MatrixCursor out=new MatrixCursor(cols);Object[] row=new Object[cols.length];
        for(int i=0;i<cols.length;i++) {
            if(OpenableColumns.DISPLAY_NAME.equals(cols[i]))row[i]=uri.getQueryParameter("name")==null ? "Chapter "+uri.getLastPathSegment()+".bin" : uri.getQueryParameter("name");
            if(OpenableColumns.SIZE.equals(cols[i]))row[i]=uri.getLastPathSegment().startsWith("pipe") ? null : data(uri).length+("wrong".equals(uri.getLastPathSegment())?1:0);
        }
        out.addRow(row);return out;
    }
    @Override public AssetFileDescriptor openAssetFile(Uri uri,String mode)throws FileNotFoundException {
        if(!"range".equals(uri.getLastPathSegment()))return super.openAssetFile(uri,mode);
        if(!"r".equals(mode))throw new SecurityException("Read only");
        try {
            byte[] audio=data(uri);File f=new File(getContext().getCacheDir(),"share-range.wav");
            try(FileOutputStream out=new FileOutputStream(f)){out.write(new byte[32]);out.write(audio);out.write(new byte[24]);}
            return new AssetFileDescriptor(ParcelFileDescriptor.open(f,ParcelFileDescriptor.MODE_READ_ONLY),32,audio.length);
        }catch(IOException e){throw new FileNotFoundException(e.toString());}
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException {
        if(!"r".equals(mode))throw new SecurityException("Read only");
        if("denied".equals(uri.getLastPathSegment()))throw new SecurityException("Fixture read denied");
        try {
            if(uri.getLastPathSegment().startsWith("pipe")) {
                ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createPipe();
                new Thread(()->{try(OutputStream out=new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                    if("pipe-stall".equals(uri.getLastPathSegment()))Thread.sleep(3000);
                    out.write(data(uri));
                }catch(Exception ignored){}},"fixture-pipe").start();return pipe[0];
            }
            File file=new File(getContext().getCacheDir(),"share-"+Integer.toHexString(uri.getPath().hashCode())+".wav");
            try(FileOutputStream out=new FileOutputStream(file)){out.write(data(uri));}
            return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);
        }catch(IOException e){throw new FileNotFoundException(e.toString());}
    }
    @Override public String getType(Uri uri){return "application/octet-stream";}
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String s,String[] a){throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
