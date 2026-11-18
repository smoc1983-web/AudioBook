package com.audiobookdownloader;
import android.app.Activity;
import android.os.Bundle;
import android.content.*;
import android.net.Uri;
import java.util.ArrayList;

public final class SharedAudioFixtureSenderActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String target=getIntent().getStringExtra("target");
        ArrayList<Uri> uris=getIntent().getParcelableArrayListExtra("uris");
        for(Uri uri:uris) {
            if(getIntent().getBooleanExtra("revoke",false))revokeUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            else grantUriPermission(target,uri,getIntent().getIntExtra("flags",Intent.FLAG_GRANT_READ_URI_PERMISSION));
        }
        if(getIntent().getBooleanExtra("send",false)) {
            Intent out=new Intent(uris.size()==1?Intent.ACTION_SEND:Intent.ACTION_SEND_MULTIPLE).setType("audio/*")
              .setComponent(new ComponentName(target,"com.audiobookdownloader.SharedAudioImportActivity"))
              .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_ACTIVITY_NEW_TASK);
            if(uris.size()==1)out.putExtra(Intent.EXTRA_STREAM,uris.get(0));else out.putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris);
            ClipData clip=ClipData.newRawUri("Audio fixture",uris.get(0));for(int i=1;i<uris.size();i++)clip.addItem(new ClipData.Item(uris.get(i)));out.setClipData(clip);
            startActivity(out);
        }
        finish();
    }
}
