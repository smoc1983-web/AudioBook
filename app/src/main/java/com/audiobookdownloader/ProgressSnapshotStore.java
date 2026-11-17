package com.audiobookdownloader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Own private metadata snapshots only; never traverses or removes library/audio files. */
final class ProgressSnapshotStore {
    interface Writer { void write(File file) throws IOException; }
    interface Validator { boolean valid(File file) throws IOException; }
    static final class Saved {
        final File file;
        final int cleanupFailures;
        Saved(File file, int failures) { this.file=file; this.cleanupFailures=failures; }
    }
    private final File root;
    private final int keep;
    ProgressSnapshotStore(File directory, int keep) throws IOException {
        if(keep<1 || keep>30) throw new IllegalArgumentException("Invalid retention count");
        this.root=directory.getCanonicalFile(); this.keep=keep;
    }
    private void directory() throws IOException {
        if(!root.isDirectory() && !root.mkdirs()) throw new IOException("Cannot open snapshot directory");
    }
    private static long order(File file) {
        String name=file.getName();
        if(!name.matches("snapshot-[0-9]+-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.zip")) return -1;
        try { return Long.parseLong(name.substring(9,name.indexOf('-',9))); }
        catch(NumberFormatException error) { return -1; }
    }
    private boolean owned(File file) throws IOException {
        return order(file)>=0 && file.isFile() && file.getCanonicalFile().equals(file.getAbsoluteFile());
    }
    synchronized List<File> files() throws IOException {
        directory(); File[] all=root.listFiles();
        if(all==null) throw new IOException("Cannot list snapshots");
        List<File> result=new ArrayList<>();
        for(File file:all) if(owned(file)) result.add(file);
        Collections.sort(result,(a,b)->Long.compare(order(b),order(a)));
        return Collections.unmodifiableList(result);
    }
    private long next(long now) throws IOException {
        List<File> current=files(); long last=current.isEmpty() ? -1 : order(current.get(0));
        if(last==Long.MAX_VALUE) throw new IOException("Snapshot sequence exhausted");
        return Math.max(Math.max(0,now),last+1);
    }
    synchronized Saved save(long now, Writer writer, Validator validator) throws IOException {
        directory(); long sequence=next(now);
        File pending=File.createTempFile(".pending-",".zip",root);
        try {
            writer.write(pending);
            try(FileOutputStream stream=new FileOutputStream(pending,true)) { stream.getFD().sync(); }
            if(!validator.valid(pending)) throw new IOException("Snapshot validation failed");
            File complete=new File(root,"snapshot-"+sequence+"-"+UUID.randomUUID()+".zip");
            if(!pending.renameTo(complete)) throw new IOException("Cannot publish snapshot");
            return new Saved(complete,prune(validator));
        } finally { if(pending.exists()) pending.delete(); }
    }
    private boolean valid(File file, Validator validator) {
        try { return validator.valid(file); }
        catch(IOException error) { return false; }
    }
    private int prune(Validator validator) throws IOException {
        int accepted=0; int failures=0;
        for(File file:files()) {
            if(!valid(file,validator)) continue;
            accepted++;
            if(accepted>keep && !file.delete()) failures++;
        }
        return failures;
    }
}
