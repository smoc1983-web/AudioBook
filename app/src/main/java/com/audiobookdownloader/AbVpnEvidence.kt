package com.audiobookdownloader

import android.content.Context

/** The user declares network mode; this does not detect a system-wide VPN tunnel. */
internal enum class AbVpnCheckMode { DIRECT, VPN }
internal data class AbVpnCheck(val status:SourceHealthRules.Status,val checked:Long)

/** Same-device evidence of a better site response via VPN; not proof of geo-blocking or book availability. */
internal object AbVpnEvidenceRules {
    const val PAIR_WINDOW_MS=30L*60*1000
    const val VALIDITY_MS=24L*60*60*1000
    fun potentiallyNetworkRelated(status:SourceHealthRules.Status)=status in setOf(
        SourceHealthRules.Status.RESTRICTED,SourceHealthRules.Status.TIMEOUT,
        SourceHealthRules.Status.DNS,SourceHealthRules.Status.CONNECTION)
    fun vpnHelped(direct:AbVpnCheck?,vpn:AbVpnCheck?,now:Long):Boolean {
        if(direct==null||vpn==null||now<0)return false
        if(!potentiallyNetworkRelated(direct.status)||vpn.status!=SourceHealthRules.Status.RESPONDED)return false
        if(direct.checked<=0||vpn.checked<direct.checked||vpn.checked-direct.checked>PAIR_WINDOW_MS)return false
        return now>=vpn.checked&&now-vpn.checked<=VALIDITY_MS
    }
}

internal class AbVpnEvidenceStore(context:Context) {
    private val prefs=context.applicationContext.getSharedPreferences("source_vpn_evidence_v1",0)
    private fun key(target:SourceHealthTarget,mode:AbVpnCheckMode) = mode.name+"_"+target.key
    private fun read(target:SourceHealthTarget,mode:AbVpnCheckMode):AbVpnCheck? = try {
        val parts=prefs.getString(key(target,mode),null)?.split(':',limit=2) ?: return null
        if(parts.size!=2)return null
        AbVpnCheck(SourceHealthRules.Status.valueOf(parts[0]),parts[1].toLong().also {require(it>0)})
    } catch(_:Exception) {null}
    fun record(target:SourceHealthTarget,mode:AbVpnCheckMode,result:SourceHealthObservation) {
        // Only observations from a new request count; a cached response never proves VPN impact.
        prefs.edit().putString(key(target,mode),result.status.name+":"+result.checked).apply()
    }
    fun vpnHelped(target:SourceHealthTarget,now:Long):Boolean =
        AbVpnEvidenceRules.vpnHelped(read(target,AbVpnCheckMode.DIRECT),read(target,AbVpnCheckMode.VPN),now)
}
