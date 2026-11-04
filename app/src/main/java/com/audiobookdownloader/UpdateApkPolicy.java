package com.audiobookdownloader;

import java.io.*;
import java.security.MessageDigest;

/** Policy applied to identities returned by Android PackageManager, never APK-supplied certificate text. */
final class UpdateApkPolicy {
    static void identity(String packageName,long code,String name,String signer,String installedSigner,
                         long installedCode,PreviewUpdateRules expected)throws IOException {
        if(!PreviewUpdateRules.PACKAGE.equals(packageName)||code<=installedCode||code!=expected.versionCode||!expected.versionName.equals(name))
            throw new IOException("APK не соответствует приложению или версии выпуска");
        if(signer==null||!signer.equals(installedSigner)||!signer.equals(expected.certificateSha256))
            throw new IOException("Подпись APK отличается от установленного приложения. Установка отменена.");
    }
    static void file(File file,PreviewUpdateRules expected)throws Exception {
        if(!file.isFile()||file.length()!=expected.size)throw new IOException("Размер APK изменился. Загрузите обновление заново.");
        MessageDigest digest=MessageDigest.getInstance("SHA-256");long size=0;
        try(InputStream input=new FileInputStream(file)){
            byte[] buffer=new byte[65536];int n;
            while((n=input.read(buffer))!=-1){
                if(Thread.currentThread().isInterrupted())throw new IOException("Проверка отменена");
                size+=n;if(size>expected.size)throw new IOException("Размер APK изменился");digest.update(buffer,0,n);
            }
        }
        if(size!=expected.size||!expected.sha256.equals(PreviewUpdateRules.hex(digest.digest())))throw new IOException("SHA-256 APK изменился. Загрузите обновление заново.");
    }
}
