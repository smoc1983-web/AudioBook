package com.audiobookdownloader;
import android.database.*;
import android.os.*;
import android.provider.DocumentsProvider;
import android.provider.DocumentsContract.Document;
import java.io.*;

/** Separate test APK UID: exercise persistent read-only tree grants without a cloud account. */
public final class LibraryRootsGrantProvider extends DocumentsProvider {
 @Override public boolean onCreate(){return true;}
 @Override public boolean isChildDocument(String parent,String child){return child.startsWith(parent+"/");}
 private MatrixCursor cursor(String[] projection){return new MatrixCursor(projection==null?new String[]{Document.COLUMN_DOCUMENT_ID,Document.COLUMN_DISPLAY_NAME,Document.COLUMN_MIME_TYPE,Document.COLUMN_SIZE,Document.COLUMN_FLAGS}:projection);}
 private void add(MatrixCursor c,String id){Object[] row=new Object[c.getColumnCount()];boolean dir=!id.contains("/");for(int i=0;i<row.length;i++)switch(c.getColumnNames()[i]){
  case Document.COLUMN_DOCUMENT_ID:row[i]=id;break;
  case Document.COLUMN_DISPLAY_NAME:row[i]=dir?"Grant "+id:"01.mp3";break;
  case Document.COLUMN_MIME_TYPE:row[i]=dir?Document.MIME_TYPE_DIR:"audio/mpeg";break;
  case Document.COLUMN_SIZE:row[i]=dir?0:2048;break;
  case Document.COLUMN_FLAGS:row[i]=0;break;
 }c.addRow(row);}
 @Override public Cursor queryRoots(String[] projection){return new MatrixCursor(projection==null?new String[]{"root_id"}:projection);}
 @Override public Cursor queryDocument(String id,String[] projection)throws FileNotFoundException {if(id.equals("missing"))throw new FileNotFoundException();MatrixCursor c=cursor(projection);add(c,id);return c;}
 @Override public Cursor queryChildDocuments(String id,String[] projection,String sort)throws FileNotFoundException {if(id.equals("missing"))throw new FileNotFoundException();MatrixCursor c=cursor(projection);add(c,id+"/01.mp3");return c;}
 @Override public ParcelFileDescriptor openDocument(String id,String mode,CancellationSignal signal)throws FileNotFoundException {
  if(!mode.equals("r"))throw new SecurityException("Read-only fixture");
  try{File f=new File(getContext().getCacheDir(),"roots-"+id.hashCode());try(FileOutputStream out=new FileOutputStream(f)){out.write(new byte[2048]);}return ParcelFileDescriptor.open(f,ParcelFileDescriptor.MODE_READ_ONLY);}catch(IOException e){throw new FileNotFoundException();}
 }
}
