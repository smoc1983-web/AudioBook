package com.audiobookdownloader;
/** Diagnostic export boundary; implemented after red regression checks. */
public final class DiagnosticPrivacy {
    private DiagnosticPrivacy() {}
    public static String redact(String value) {return value==null?"":value;}
}
