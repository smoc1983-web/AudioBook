package com.audiobookdownloader;

import org.junit.*;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class ProgressSnapshotStoreTest {
    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path),java.nio.charset.StandardCharsets.UTF_8);
    }
    private static void write(Path path,String text) throws IOException {
        Files.write(path,text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private File root;
    private ProgressSnapshotStore store;
    private final ProgressSnapshotStore.Validator valid=f->read(f.toPath()).startsWith("valid:");
    @Before public void setup() throws Exception {root=Files.createTempDirectory("snapshot-contract-").toFile();store=new ProgressSnapshotStore(root,7);}
    @After public void cleanup() throws Exception {try(var paths=Files.walk(root.toPath())){paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(IOException e){throw new RuntimeException(e);}});}}
    private File save(long at) throws Exception {return store.save(at,f->write(f.toPath(),"valid:"+at),valid).file;}
    @Test public void retainsSevenValidatedSnapshotsIncludingNewestAfterClockRollback() throws Exception {
        File first=save(100); for(int i=0;i<8;i++)save(50+i);
        assertEquals(7,store.files().size());assertFalse(first.exists());
        assertEquals("valid:57",read(store.files().get(0).toPath()));
    }
    @Test public void interruptedWriteNeverReplacesPreviousOrPublishesPartialData() throws Exception {
        File previous=save(1);
        try {store.save(2,f->{write(f.toPath(),"partial");throw new IOException("interrupted");},valid);fail();}catch(IOException expected){}
        assertEquals(List.of(previous),store.files());assertEquals("valid:1",read(previous.toPath()));
        assertEquals(1,Objects.requireNonNull(root.list()).length);
    }
    @Test public void rejectedNewSnapshotCannotRemoveAnyOldSnapshot() throws Exception {
        File previous=save(1);
        try {store.save(2,f->write(f.toPath(),"broken"),valid);fail();}catch(IOException expected){}
        assertEquals(List.of(previous),store.files());assertEquals("valid:1",read(previous.toPath()));
    }
    @Test public void damagedPreviousCopyAndForeignFilesAreRetained() throws Exception {
        File broken=save(1);write(broken.toPath(),"corrupt original");
        File foreign=new File(root,"my-backup.zip");write(foreign.toPath(),"do not delete");
        for(int i=2;i<12;i++)save(i);
        assertTrue(broken.exists());assertEquals("corrupt original",read(broken.toPath()));
        assertEquals("do not delete",read(foreign.toPath()));
        assertEquals(8,store.files().size());
    }
    @Test public void ioFailureReadingOldCopyCannotMakeItRotationEligible() throws Exception {
        File unreadable=save(1);
        ProgressSnapshotStore.Validator failing=f->{if(f.equals(unreadable))throw new IOException("temporarily unreadable");return valid.valid(f);};
        for(int i=2;i<12;i++){final int value=i;store.save(i,f->write(f.toPath(),"valid:"+value),failing);}
        assertTrue(unreadable.exists());assertEquals("valid:1",read(unreadable.toPath()));
        assertEquals(8,store.files().size());
    }
    @Test public void symlinksNeverBecomeRotationCandidates() throws Exception {
        File foreign=new File(root,"outside-data");write(foreign.toPath(),"valid:original");
        Path link=new File(root,"snapshot-1-00000000-0000-0000-0000-000000000000.zip").toPath();Files.createSymbolicLink(link,foreign.toPath());
        assertTrue(store.files().isEmpty());for(int i=0;i<10;i++)save(i);
        assertTrue(Files.isSymbolicLink(link));assertEquals("valid:original",read(foreign.toPath()));
    }
    @Test public void firstUseCreatesThePrivateSnapshotDirectory() throws Exception {
        File nested=new File(root,"new/private-copies");
        ProgressSnapshotStore fresh=new ProgressSnapshotStore(nested,7);
        File saved=fresh.save(0,f->write(f.toPath(),"valid:first"),valid).file;
        assertEquals(List.of(saved),fresh.files());assertEquals(nested.getCanonicalFile(),saved.getParentFile());
    }
    @Test public void zeroNegativeAndRepeatedClockValuesStillHaveStrictPublicationOrder() throws Exception {
        File first=save(-1);assertTrue(first.getName().startsWith("snapshot-0-"));assertEquals(List.of(first),store.files());
        File second=save(0);assertTrue(second.getName().startsWith("snapshot-1-"));
        File third=save(0);assertTrue(third.getName().startsWith("snapshot-2-"));
        assertEquals(List.of(third,second,first),store.files());
    }
    @Test public void successfulPruningReportsNoCleanupFailures() throws Exception {
        ProgressSnapshotStore one=new ProgressSnapshotStore(root,1);
        var first=one.save(1,f->write(f.toPath(),"valid:first"),valid);
        assertEquals(0,first.cleanupFailures);
        var second=one.save(2,f->write(f.toPath(),"valid:second"),valid);
        assertEquals(0,second.cleanupFailures);assertFalse(first.file.exists());assertTrue(second.file.exists());
        assertEquals(List.of(second.file),one.files());
    }
    @Test public void invalidRetentionAndNonDirectoryAreRejected() throws Exception {
        try {new ProgressSnapshotStore(root,0);fail();}catch(IllegalArgumentException expected){}
        try {new ProgressSnapshotStore(root,31);fail();}catch(IllegalArgumentException expected){}
        File file=new File(root,"regular");write(file.toPath(),"data");
        try {new ProgressSnapshotStore(file,7).files();fail();}catch(IOException expected){}
    }
    @Test public void overflowAndInvalidOwnedNamesCannotOverwriteCopies() throws Exception {
        File invalid=new File(root,"snapshot-9999999999999999999999999-00000000-0000-0000-0000-000000000000.zip");write(invalid.toPath(),"data");
        assertTrue(store.files().isEmpty());File max=save(Long.MAX_VALUE);
        try {save(0);fail();}catch(IOException expected){}
        assertTrue(max.exists());assertTrue(invalid.exists());assertEquals(1,store.files().size());
    }
}
