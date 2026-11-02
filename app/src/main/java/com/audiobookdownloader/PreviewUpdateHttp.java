package com.audiobookdownloader;

import java.io.*;
import java.net.*;
import java.security.MessageDigest;

/** Public HTTPS transport, with an explicit redirect boundary and bounded verified downloads. */
final class PreviewUpdateHttp {
    interface Connections { HttpURLConnection open(URI uri)throws Exception; }
    interface Progress { void value(int percent); }
    private final Connections connections;
    PreviewUpdateHttp(){this(uri->(HttpURLConnection)uri.toURL().openConnection());}
    PreviewUpdateHttp(Connections connections){this.connections=connections;}
    HttpURLConnection open(String address,String accept) throws Exception {
        URI initial=new URI(address),uri=initial;
        if(!PreviewUpdateRules.acceptsUrl(initial))throw new IOException("Недопустимый адрес обновления");
        for(int i=0;i<6;i++){
            if(!initial.equals(uri)&&!PreviewUpdateRules.acceptsRedirect(initial,uri))throw new IOException("Недопустимый адрес обновления");
            HttpURLConnection c=(HttpURLConnection)connections.open(uri);
            c.setUseCaches(false);c.setRequestProperty("Cache-Control","no-cache");
            c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent","Audiobook-Updater/"+BuildConfig.VERSION_NAME);
            c.setRequestProperty("Accept",accept);c.setRequestProperty("Accept-Encoding","identity");
            int status=c.getResponseCode();
            if(status==301||status==302||status==303||status==307||status==308){
                String location=c.getHeaderField("Location");c.disconnect();
                if(location==null)throw new IOException("Отсутствует адрес файла");
                uri=uri.resolve(location);continue;
            }
            if(status!=200){c.disconnect();
                if(status==401||status==403)throw new IOException("Канал обновлений пока закрыт для загрузки. Попробуйте позже.");
                if(status==404)throw new IOException("Новый выпуск этого канала пока не опубликован");
                throw new IOException("Сервер обновлений ответил HTTP "+status);
            }
            return c;
        }
        throw new IOException("Слишком много перенаправлений");
    }
    String read(String url,String accept,int max) throws Exception {
        HttpURLConnection c=open(url,accept);
        try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];int n;
            while((n=in.read(buffer))!=-1){if(out.size()+n>max)throw new IOException("Слишком большой ответ сервера");out.write(buffer,0,n);}
            return out.toString("UTF-8");
        }finally{c.disconnect();}
    }
    void download(PreviewUpdateRules info,OutputStream out,Progress progress)throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");long total=0;int previous=-1;

                for(PreviewUpdateRules.Part part:info.parts){
                    HttpURLConnection c=open(part.url,"application/octet-stream");
                    try {
                        String length=c.getHeaderField("Content-Length");long header=length==null?-1:Long.parseLong(length);if(header>=0&&header!=part.size)throw new IOException("Размер части APK не совпал");
                        MessageDigest hash=MessageDigest.getInstance("SHA-256");long count=0;
                        try(InputStream in=c.getInputStream()){
                            byte[] buffer=new byte[65536];int n;
                            while((n=in.read(buffer))!=-1){
                                if(Thread.currentThread().isInterrupted())throw new IOException("Загрузка отменена");
                                count+=n;total+=n;
                                if(count>part.size||total>info.size)throw new IOException("Превышен размер APK");
                                out.write(buffer,0,n);digest.update(buffer,0,n);hash.update(buffer,0,n);
                                int percent=(int)(total*100/info.size);if(percent!=previous){progress.value(percent);previous=percent;}
                            }
                        }
                        if(count!=part.size||!PreviewUpdateRules.hex(hash.digest()).equals(part.sha256))throw new IOException("Часть APK повреждена: SHA-256 не совпал");
                    }finally{c.disconnect();}
                }
            if(total!=info.size||!PreviewUpdateRules.hex(digest.digest()).equals(info.sha256))throw new IOException("APK повреждён: SHA-256 не совпал");
    }
}
