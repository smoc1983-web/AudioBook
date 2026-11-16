package com.audiobookdownloader;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CharacterCodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/** Integrity envelope for local queue snapshots; checksum is not an authenticity signature. */
public final class QueueSnapshotRules {
    public static final int MAX_BYTES=8*1024*1024;
    private static final byte[] MAGIC="ABQUEUE1".getBytes(StandardCharsets.US_ASCII);
    private static final int HEADER=44;
    private QueueSnapshotRules() {}
    public static byte[] encode(String json) {
        byte[] bytes=json.getBytes(StandardCharsets.UTF_8);
        if(bytes.length>MAX_BYTES)throw new IllegalArgumentException("Queue snapshot too large");
        return ByteBuffer.allocate(HEADER+bytes.length).put(MAGIC).putInt(bytes.length).put(hash(bytes)).put(bytes).array();
    }
    public static String decode(byte[] snapshot) {
        if(snapshot.length<HEADER)throw new IllegalArgumentException("Incomplete queue header");
        ByteBuffer input=ByteBuffer.wrap(snapshot);
        byte[] magic=new byte[8];input.get(magic);
        if(!Arrays.equals(MAGIC,magic))throw new IllegalArgumentException("Unknown queue version");
        int size=input.getInt();
        if(size<0 || size>MAX_BYTES || snapshot.length!=HEADER+size)
            throw new IllegalArgumentException("Incomplete queue payload");
        byte[] checksum=new byte[32];input.get(checksum);
        byte[] bytes=new byte[size];input.get(bytes);
        if(!MessageDigest.isEqual(checksum,hash(bytes)))throw new IllegalArgumentException("Queue checksum mismatch");
        return text(bytes);
    }
    private static String text(byte[] bytes) {
        try {return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();}
        catch(CharacterCodingException e){throw new IllegalArgumentException("Invalid queue encoding",e);}
    }
    private static byte[] hash(byte[] bytes) {
        try {return MessageDigest.getInstance("SHA-256").digest(bytes);}
        catch(NoSuchAlgorithmException e){throw new AssertionError(e);}
    }
}
