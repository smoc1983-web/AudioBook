package com.audiobookdownloader;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.regex.*;

/** Seekable metadata reads: bounded byte ranges, redirects/timeouts and no whole-file fallback. */
public final class AbDurationRanges implements Closeable {
    public interface Connections { HttpURLConnection open(URL url) throws IOException; }
    public static final int BLOCK=32768, MAX_BYTES=524288;
    private final URL original;
    private final Map<String,String> headers;
    private final Connections connections;
    private final AtomicBoolean closed=new AtomicBoolean();
    private final AtomicReference<HttpURLConnection> active=new AtomicReference<>();
    private final long deadline=System.nanoTime()+12_000_000_000L;
    private long length=-1;
    private int consumed;
    private final LinkedHashMap<Long,byte[]> blocks=new LinkedHashMap<Long,byte[]>(8,.75f,true){
        protected boolean removeEldestEntry(Map.Entry<Long,byte[]> entry){return size()>8;}
    };
    public AbDurationRanges(String source,Map<String,String> headers)throws IOException {this(source,headers,url->(HttpURLConnection)url.openConnection());}
    public AbDurationRanges(String source,Map<String,String> headers,Connections connections)throws IOException {
        original=new URL(source);this.headers=new HashMap<>(headers);this.connections=connections;checkUrl(original);
    }
    private void check()throws IOException {if(closed.get())throw new IOException("Metadata read cancelled");if(System.nanoTime()>deadline)throw new IOException("Metadata read timed out");}
    private static void checkUrl(URL url)throws IOException {if(!Arrays.asList("https","http").contains(url.getProtocol())||url.getUserInfo()!=null)throw new IOException("Invalid audio URL");}
    private static boolean sameOrigin(URL a,URL b){return a.getProtocol().equals(b.getProtocol())&&a.getHost().equalsIgnoreCase(b.getHost())&&a.getPort()==b.getPort();}
    private byte[] block(long start)throws IOException {
        check();byte[] cached=blocks.get(start);if(cached!=null)return cached;
        if(consumed>=MAX_BYTES)throw new IOException("Metadata byte budget exceeded");
        long end=length>0?Math.min(length-1,start+BLOCK-1):start+BLOCK-1;
        URL address=original;
        for(int redirect=0;redirect<5;redirect++){
            check();HttpURLConnection c=connections.open(address);active.set(c);
            try {
                check();c.setInstanceFollowRedirects(false);c.setConnectTimeout(3000);c.setReadTimeout(3000);
                for(Map.Entry<String,String> entry:headers.entrySet()){
                    String name=entry.getKey();
                    if(!sameOrigin(original,address)&&(name.equalsIgnoreCase("Cookie")||name.equalsIgnoreCase("Authorization")))continue;
                    c.setRequestProperty(name,entry.getValue());
                }
                c.setRequestProperty("Accept-Encoding","identity");c.setRequestProperty("Range","bytes="+start+"-"+end);
                int code=c.getResponseCode();check();
                if(Arrays.asList(301,302,303,307,308).contains(code)){
                    String target=c.getHeaderField("Location");if(target==null)throw new IOException("Missing redirect");
                    URL next=new URL(address,target);checkUrl(next);
                    if(address.getProtocol().equals("https")&&!next.getProtocol().equals("https"))throw new IOException("Insecure redirect");
                    address=next;continue;
                }
                String encoding=c.getHeaderField("Content-Encoding");
                if(encoding!=null&&!encoding.isEmpty()&&!encoding.equalsIgnoreCase("identity"))throw new IOException("Encoded range");
                String type=c.getContentType();if(type!=null)type=type.toLowerCase(Locale.ROOT);
                if(type!=null&&(type.startsWith("text/")||type.contains("json")))throw new IOException("Not audio metadata");
                int wanted;
                if(code==206){
                    Matcher m=Pattern.compile("(?i)bytes ([0-9]+)-([0-9]+)/([0-9]+)").matcher(String.valueOf(c.getHeaderField("Content-Range")));
                    if(!m.matches())throw new IOException("Invalid Content-Range");
                    long first=Long.parseLong(m.group(1)),last=Long.parseLong(m.group(2)),total=Long.parseLong(m.group(3));
                    if(first!=start||last<first||last>end||total<=last||(length>0&&length!=total))throw new IOException("Inconsistent range");
                    length=total;wanted=(int)(last-first+1);
                }else if(code==200&&start==0){
                    long total=Long.parseLong(String.valueOf(c.getHeaderField("Content-Length")));if(total<=0)throw new IOException("Audio size unavailable");
                    if(length>0&&length!=total)throw new IOException("Changed audio size");
                    length=total;wanted=(int)Math.min(total,BLOCK);
                }else throw new IOException("Audio range unavailable: HTTP "+code);
                wanted=Math.min(wanted,MAX_BYTES-consumed);
                byte[] data=new byte[wanted];int read=0;
                try(InputStream input=c.getInputStream()){
                    while(read<wanted){check();int n=input.read(data,read,wanted-read);if(n<0)throw new EOFException("Truncated metadata range");if(n==0)continue;read+=n;consumed+=n;}
                }
                blocks.put(start,data);return data;
            }catch(NumberFormatException error){throw new IOException("Invalid audio size",error);}
            finally {active.compareAndSet(c,null);c.disconnect();}
        }
        throw new IOException("Too many metadata redirects");
    }
    public synchronized long size()throws IOException {check();if(length<0)block(0);return length;}
    public synchronized int read(long position,byte[] target,int offset,int count)throws IOException {
        check();if(position<0||offset<0||count<0||offset>target.length-count)throw new IOException("Invalid metadata read");
        if(count==0)return 0;if(position>=size())return -1;
        long start=position/BLOCK*BLOCK;byte[] data=block(start);int within=(int)(position-start);
        if(within>=data.length)throw new EOFException("Short metadata block");
        int n=Math.min(count,data.length-within);System.arraycopy(data,within,target,offset,n);return n;
    }
    public int bytesRead(){return consumed;}
    @Override public void close(){closed.set(true);HttpURLConnection c=active.getAndSet(null);if(c!=null)c.disconnect();}
}
