package com.audiobookdownloader;

import java.io.*;
import java.net.*;
import java.security.MessageDigest;

/** Public HTTPS transport, with an explicit redirect boundary and bounded verified downloads. */
final class PreviewUpdateHttp {
    interface Connections { HttpURLConnection open(URI uri)throws Exception; }
    interface Progress { void value(int percent); }
    private final Connections connections;
    private volatile HttpURLConnection active;
    private volatile boolean cancelled;
    void cancel(){cancelled=true;HttpURLConnection c=active;if(c!=null)c.disconnect();}
    private void checkCancelled()throws IOException{if(cancelled||Thread.currentThread().isInterrupted())throw new IOException("Загрузка отменена");}
    PreviewUpdateHttp(){this(uri->(HttpURLConnection)uri.toURL().openConnection());}
    PreviewUpdateHttp(Connections connections){this.connections=connections;}
    HttpURLConnection open(String address,String accept) throws Exception {
        URI initial=new URI(address),uri=initial;
        if(!PreviewUpdateRules.acceptsUrl(initial))throw new IOException("Недопустимый адрес обновления");
        java.util.Set<URI> visited=new java.util.HashSet<>();
        for(int i=0;i<6;i++){
            if(!visited.add(uri))throw new IOException("Повторное перенаправление обновления");
            if(!initial.equals(uri)&&!PreviewUpdateRules.acceptsRedirect(initial,uri))throw new IOException("Недопустимый адрес обновления");
            checkCancelled();
            HttpURLConnection c;
            try{c=connections.open(uri);}catch(Exception failure){throw new IOException("Не удалось подключиться к серверу обновлений");}
            active=c;
            boolean returned=false;
            try {
            checkCancelled();
            c.setUseCaches(false);c.setRequestProperty("Cache-Control","no-cache");
            c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent","Audiobook-Updater/"+BuildConfig.VERSION_NAME);
            c.setRequestProperty("X-GitHub-Api-Version","2022-11-28");
            c.setRequestProperty("Accept",accept);c.setRequestProperty("Accept-Encoding","identity");
            int status;
            try{status=c.getResponseCode();}catch(SocketTimeoutException timeout){throw timeout;}
            catch(IOException failure){throw new IOException("Нет соединения с сервером обновлений. Проверьте интернет.");}
            if(status==301||status==302||status==303||status==307||status==308){
                String location=c.getHeaderField("Location");c.disconnect();
                if(location==null)throw new IOException("Отсутствует адрес файла");
                try{uri=uri.resolve(location);}catch(IllegalArgumentException bad){throw new IOException("Некорректное перенаправление обновления");}continue;
            }
            if(status!=200){
                if(status==429||(status==403&&"0".equals(c.getHeaderField("X-RateLimit-Remaining")))){
                    String retry=c.getHeaderField("Retry-After");
                    throw new IOException("Лимит запросов GitHub исчерпан. "+(retry!=null&&retry.matches("[0-9]{1,5}")?"Повторите через "+retry+" с.":"Повторите проверку позже."));
                }
                if(status==401||status==403)throw new IOException("Канал обновлений пока закрыт для загрузки. Попробуйте позже.");
                if(status==404)throw new IOException("Новый выпуск этого канала пока не опубликован");
                throw new IOException("Сервер обновлений ответил HTTP "+status);
            }
            String encoding=c.getHeaderField("Content-Encoding");
            if(encoding!=null&&!encoding.equalsIgnoreCase("identity"))throw new IOException("Неожиданное сжатие ответа обновлений");
            returned=true;return c;
            }catch(SocketTimeoutException timeout){throw new IOException("Сервер обновлений не ответил вовремя. Повторите попытку.");}
            finally{if(!returned)c.disconnect();}
        }
        throw new IOException("Слишком много перенаправлений");
    }
    private InputStream input(HttpURLConnection c)throws IOException {
        try{
            return new FilterInputStream(c.getInputStream()){
                @Override public void close()throws IOException {
                    try{in.close();}catch(IOException failure){throw new IOException("Не удалось завершить загрузку обновления");}
                }
                @Override public int read(byte[] bytes,int offset,int length)throws IOException {
                    checkCancelled();
                    try{return in.read(bytes,offset,length);}
                    catch(SocketTimeoutException failure){throw new IOException("Загрузка приостановлена: сервер не ответил вовремя. Повторите попытку.");}
                    catch(IOException failure){throw new IOException("Соединение прервано. Загрузите обновление заново.");}
                }
            };
        }catch(IOException failure){throw new IOException("Не удалось получить файл обновления. Повторите попытку.");}
    }
    private static long contentLength(HttpURLConnection c)throws IOException {
        String value=c.getHeaderField("Content-Length");if(value==null)return -1;
        if(!value.matches("[0-9]{1,10}"))throw new IOException("Некорректный размер ответа сервера");
        return Long.parseLong(value);
    }
    String read(String url,String accept,int max) throws Exception {
        HttpURLConnection c=open(url,accept);
        try(InputStream in=input(c);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];int n;
            while((n=in.read(buffer))!=-1){checkCancelled();if(out.size()+n>max)throw new IOException("Слишком большой ответ сервера");out.write(buffer,0,n);}
            checkCancelled();
            long length=contentLength(c);if(length>=0&&length!=out.size())throw new IOException("Неполный ответ сервера обновлений");
            return out.toString("UTF-8");
        }finally{c.disconnect();}
    }
    void download(PreviewUpdateRules info,OutputStream out,Progress progress)throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");long total=0;int previous=-1;

                for(PreviewUpdateRules.Part part:info.parts){
                    HttpURLConnection c=open(part.url,"application/octet-stream");
                    try {
                        long header=contentLength(c);if(header>=0&&header!=part.size)throw new IOException("Размер части APK не совпал");
                        MessageDigest hash=MessageDigest.getInstance("SHA-256");long count=0;
                        try(InputStream in=input(c)){
                            byte[] buffer=new byte[65536];int n;
                            while((n=in.read(buffer))!=-1){
                                checkCancelled();
                                count+=n;total+=n;
                                if(count>part.size||total>info.size)throw new IOException("Превышен размер APK");
                                out.write(buffer,0,n);digest.update(buffer,0,n);hash.update(buffer,0,n);
                                int percent=(int)Math.min(99,total*100/info.size);if(percent!=previous){progress.value(percent);previous=percent;}
                            }
                        }
                        if(count!=part.size||!PreviewUpdateRules.hex(hash.digest()).equals(part.sha256))throw new IOException("Часть APK повреждена: SHA-256 не совпал");
                    }finally{c.disconnect();}
                }
            checkCancelled();
            if(total!=info.size||!PreviewUpdateRules.hex(digest.digest()).equals(info.sha256))throw new IOException("APK повреждён: SHA-256 не совпал");
            progress.value(100);
    }
}
