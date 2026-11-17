package com.audiobookdownloader

import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class ShortAudioValidationTest {
    /** Even a single nonempty audio packet is valid; API23 advance() differs at EOF. */
    @Test fun validSingleAndMultiplePacketWavReachEofWithoutBeingMistakenForDamage() {
        val c=ApplicationProvider.getApplicationContext<Context>()
        for(samples in listOf(64,8000,32000)) {
            val data=samples*2
            val b=ByteBuffer.allocate(44+data).order(ByteOrder.LITTLE_ENDIAN)
            b.put("RIFF".toByteArray(Charsets.US_ASCII));b.putInt(data+36);b.put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
            b.putInt(16);b.putShort(1);b.putShort(1);b.putInt(8000);b.putInt(16000);b.putShort(2);b.putShort(16)
            b.put("data".toByteArray(Charsets.US_ASCII));b.putInt(data)
            repeat(samples){b.putShort((it%3000).toShort())}
            val file=File.createTempFile("short-audio-",".unknown",c.cacheDir)
            try {
                file.writeBytes(b.array())
                val result=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use{AbDownloadedAudio.inspectDescriptor(it.fileDescriptor,file.length())}
                assertTrue("$samples samples: ${result.error}",result.valid);assertEquals("audio/raw",result.format)
                file.writeBytes(b.array().copyOf(b.capacity()-1))
                val truncated=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use{AbDownloadedAudio.inspectDescriptor(it.fileDescriptor,-1)}
                assertFalse("truncated $samples samples accepted",truncated.valid)
            }finally{file.delete()}
        }
    }
}
