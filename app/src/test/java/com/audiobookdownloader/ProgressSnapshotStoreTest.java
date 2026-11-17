package com.audiobookdownloader;

import org.junit.*;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class ProgressSnapshotStoreTest {
    private File root;
    private ProgressSnapshotStore store;
    private final ProgressSnapshotStore.Validator valid=f->Files.readString(f.toPath()).startsWith("valid:");
    @Before public void setup() throws Exception {root=Files.createTempDirectory("snapshot-contract-").toFile();store=new ProgressSnapshotStore(root,7);}
    @After public void cleanup() throws Exception {try(var paths=Files.walk(root.toPath())){paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(IOException e){throw new RuntimeException(e);}});}}
    private File save(long at) throws Exception {return store.save(at,f->Files.writeString(f.toPath(),"valid:"+at),valid).file;}
    @Test public void retainsSevenValidatedSnapshotsIncludingNewestAfterClockRollback() throws Exception {
        File first=save(100); for(int i=0;i<8;i++)save(50+i);
        assertEquals(7,store.files().size());assertFalse(first.exists());
        assertEquals("valid:57",Files.readString(store.files().get(0).toPath()));
    }
    @Test public void interruptedWriteNeverReplacesPreviousOrPublishesPartialData() throws Exception {
        File previous=save(1);
        try {store.save(2,f->{Files.writeString(f.toPath(),"partial");throw new IOException("interrupted");},valid);fail();}catch(IOException expected){}
        assertEquals(List.of(previous),store.files());assertEquals("valid:1",Files.readString(previous.toPath()));
        assertEquals(1,Objects.requireNonNull(root.list()).length);
    }
    @Test public void rejectedNewSnapshotCannotRemoveAnyOldSnapshot() throws Exception {
        File previous=save(1);
        try {store.save(2,f->Files.writeString(f.toPath(),"broken"),valid);fail();}catch(IOException expected){}
        assertEquals(List.of(previous),store.files());assertEquals("valid:1",Files.readString(previous.toPath()));
    }
    @Test public void damagedPreviousCopyAndForeignFilesAreRetained() throws Exception {
        File broken=save(1);Files.writeString(broken.toPath(),"corrupt original");
        File foreign=new File(root,"my-backup.zip");Files.writeString(foreign.toPath(),"do not delete");
        for(int i=2;i<12;i++)save(i);
        assertTrue(broken.exists());assertEquals("corrupt original",Files.readString(broken.toPath()));
        assertEquals("do not delete",Files.readString(foreign.toPath()));
        assertEquals(8,store.files().size());
    }
    @Test public void symlinksNeverBecomeRotationCandidates() throws Exception {
        File foreign=new File(root,"outside-data");Files.writeString(foreign.toPath(),"valid:original");
        Path link=new File(root,"snapshot-1-00000000-0000-0000-0000-000000000000.zip").toPath();Files.createSymbolicLink(link,foreign.toPath());
        assertTrue(store.files().isEmpty());for(int i=0;i<10;i++)save(i);
        assertTrue(Files.isSymbolicLink(link));assertEquals("valid:original",Files.readString(foreign.toPath()));
    }
    @Test public void invalidRetentionAndNonDirectoryAreRejected() throws Exception {
        try {new ProgressSnapshotStore(root,0);fail();}catch(IllegalArgumentException expected){}
        try {new ProgressSnapshotStore(root,31);fail();}catch(IllegalArgumentException expected){}
        File file=new File(root,"regular");Files.writeString(file.toPath(),"data");
        try {new ProgressSnapshotStore(file,7).files();fail();}catch(IOException expected){}
    }
    @Test public void overflowAndInvalidOwnedNamesCannotOverwriteCopies() throws Exception {
        File invalid=new File(root,"snapshot-9999999999999999999999999-00000000-0000-0000-0000-000000000000.zip");Files.writeString(invalid.toPath(),"data");
        assertTrue(store.files().isEmpty());File max=save(Long.MAX_VALUE);
        try {save(0);fail();}catch(IOException expected){}
        assertTrue(max.exists());assertTrue(invalid.exists());assertEquals(1,store.files().size());
    }
}
