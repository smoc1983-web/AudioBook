package com.audiobookdownloader

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.ConnectException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

internal class SourceHttpFailure(val status:Int,val retryAfter:String?):IOException("Источник вернул HTTP $status")
internal object SourceRequestRetry {
    fun waitMillis(error:Exception,attempt:Int):Long? {
        if(attempt !in 0..1)return null
        if(error is SourceHttpFailure) {
            if(error.status !in setOf(408,429,502,503,504))return null
            if(!error.retryAfter.isNullOrBlank()) {
                val seconds=error.retryAfter.trim().toLongOrNull() ?: return null
                if(seconds !in 0..5)return null
                return maxOf(seconds*1000,500L shl attempt)
            }
        } else if(error !is SocketTimeoutException && error !is ConnectException)return null
        return 500L shl attempt
    }
    suspend fun <T> get(pause:suspend (Long)->Unit={delay(it)},request:suspend ()->T):T = withTimeout(20_000L) {
        var attempt=0
        while(true) {
            try {return@withTimeout request()}
            catch(cancel:kotlinx.coroutines.CancellationException){throw cancel}
            catch(error:Exception){val wait=waitMillis(error,attempt++) ?: throw error;pause(wait)}
        }
        @Suppress("UNREACHABLE_CODE") error("Unreachable")
    }
}
