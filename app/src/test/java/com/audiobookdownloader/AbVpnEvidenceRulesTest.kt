package com.audiobookdownloader

import org.junit.Assert.*
import org.junit.Test

class AbVpnEvidenceRulesTest {
    private fun sample(s:SourceHealthRules.Status,t:Long)=AbVpnCheck(s,t)
    @Test fun directRestrictedVpnRespondedMarksSite() {
        assertTrue(AbVpnEvidenceRules.vpnHelped(
            sample(SourceHealthRules.Status.RESTRICTED,100_000),
            sample(SourceHealthRules.Status.RESPONDED,170_000),190_000))
    }
    @Test fun dnsAndTimeoutMayBenefitButRateLimitOrServerFailureDoesNotProveVpn() {
        val via=sample(SourceHealthRules.Status.RESPONDED,170_000)
        for(status in listOf(SourceHealthRules.Status.DNS,SourceHealthRules.Status.TIMEOUT,SourceHealthRules.Status.CONNECTION))
            assertTrue(AbVpnEvidenceRules.vpnHelped(sample(status,100_000),via,190_000))
        for(status in listOf(SourceHealthRules.Status.RATE_LIMIT,SourceHealthRules.Status.SERVER_ERROR,
            SourceHealthRules.Status.HTTP_ERROR,SourceHealthRules.Status.TLS,SourceHealthRules.Status.RESPONDED))
            assertFalse(AbVpnEvidenceRules.vpnHelped(sample(status,100_000),via,190_000))
    }
    @Test fun missingOrFailedVpnDoesNotCreateBadge() {
        val direct=sample(SourceHealthRules.Status.DNS,100_000)
        assertFalse(AbVpnEvidenceRules.vpnHelped(null,sample(SourceHealthRules.Status.RESPONDED,170_000),190_000))
        assertFalse(AbVpnEvidenceRules.vpnHelped(direct,null,190_000))
        assertFalse(AbVpnEvidenceRules.vpnHelped(direct,sample(SourceHealthRules.Status.RESTRICTED,170_000),190_000))
    }
    @Test fun wrongOrderDistantOrStaleObservationsAreRejected() {
        val direct=sample(SourceHealthRules.Status.DNS,200_000)
        assertFalse(AbVpnEvidenceRules.vpnHelped(direct,sample(SourceHealthRules.Status.RESPONDED,100_000),210_000))
        assertFalse(AbVpnEvidenceRules.vpnHelped(direct,sample(SourceHealthRules.Status.RESPONDED,2_100_001),2_100_001))
        val via=sample(SourceHealthRules.Status.RESPONDED,260_000)
        assertFalse(AbVpnEvidenceRules.vpnHelped(direct,via,via.checked+AbVpnEvidenceRules.VALIDITY_MS+1))
        assertFalse(AbVpnEvidenceRules.vpnHelped(direct,via,via.checked-1))
    }
}
