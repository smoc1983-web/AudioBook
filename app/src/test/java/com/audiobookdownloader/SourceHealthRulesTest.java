package com.audiobookdownloader;

import org.junit.Test;
import static org.junit.Assert.*;

public class SourceHealthRulesTest {
    @Test public void successIsOnlyAnObservedHtmlResponseAndChallengesAreNotGreen() {
        assertEquals(SourceHealthRules.Status.RESPONDED,SourceHealthRules.response(200,true,false));
        assertEquals(SourceHealthRules.Status.RESPONDED,SourceHealthRules.response(299,true,false));
        assertEquals(SourceHealthRules.Status.RESTRICTED,SourceHealthRules.response(200,true,true));
        assertEquals(SourceHealthRules.Status.UNEXPECTED,SourceHealthRules.response(204,false,false));
        assertEquals(SourceHealthRules.Status.RESTRICTED,SourceHealthRules.response(200,false,true));
    }
    @Test public void accessRateLimitServerAndOtherHttpErrorsStayDistinct() {
        for(int code:new int[]{401,403})assertEquals(SourceHealthRules.Status.RESTRICTED,SourceHealthRules.response(code,false,false));
        assertEquals(SourceHealthRules.Status.RATE_LIMIT,SourceHealthRules.response(429,true,false));
        for(int code:new int[]{500,503,599})assertEquals(SourceHealthRules.Status.SERVER_ERROR,SourceHealthRules.response(code,true,false));
        for(int code:new int[]{199,300,404,499})assertEquals(SourceHealthRules.Status.HTTP_ERROR,SourceHealthRules.response(code,true,false));
    }
    @Test public void staleOrFutureResultsCannotAppearFresh() {
        assertTrue(SourceHealthRules.fresh(0,0));assertTrue(SourceHealthRules.fresh(10,10+SourceHealthRules.TTL-1));
        assertFalse(SourceHealthRules.fresh(10,10+SourceHealthRules.TTL));assertFalse(SourceHealthRules.fresh(10,9));
        assertFalse(SourceHealthRules.fresh(-1,10));assertFalse(SourceHealthRules.fresh(0,Long.MAX_VALUE));
    }
    @Test public void manualRefreshCannotBypassCooldownOrServerWait() {
        long checked=100;
        assertTrue(SourceHealthRules.mayRequest(-1,0,0,false));
        assertFalse(SourceHealthRules.mayRequest(0,0,0,true));
        assertFalse(SourceHealthRules.mayRequest(checked,0,checked-1,true));
        assertFalse(SourceHealthRules.mayRequest(checked,0,checked+SourceHealthRules.MIN_WAIT-1,true));
        assertTrue(SourceHealthRules.mayRequest(checked,0,checked+SourceHealthRules.MIN_WAIT,true));
        assertFalse(SourceHealthRules.mayRequest(checked,checked+900000,checked+60000,true));
        assertFalse(SourceHealthRules.mayRequest(checked,0,checked+60000,false));
        assertTrue(SourceHealthRules.mayRequest(checked,0,checked+SourceHealthRules.TTL,false));
        assertTrue(SourceHealthRules.mayRequest(checked,checked+60000,checked+60000,true));
    }
    @Test public void retryAfterSecondsDatesAndMalformedValuesAreBounded() {
        assertEquals(60000,SourceHealthRules.retryWait(null,0));assertEquals(60000,SourceHealthRules.retryWait(" ",0));
        assertEquals(0,SourceHealthRules.retryWait("0",0));assertEquals(120000,SourceHealthRules.retryWait("120",0));
        assertEquals(SourceHealthRules.MAX_WAIT,SourceHealthRules.retryWait("99999999999999999999999999999",0));
        assertEquals(SourceHealthRules.MAX_WAIT,SourceHealthRules.retryWait("90000",0));
        assertEquals(60000,SourceHealthRules.retryWait("-1",0));assertEquals(60000,SourceHealthRules.retryWait("not a date",0));
        assertEquals(1000,SourceHealthRules.retryWait("Thu, 01 Jan 1970 00:00:01 GMT",0));
        assertEquals(900,SourceHealthRules.retryWait("Thu, 01 Jan 1970 00:00:01 GMT",100));
        assertEquals(0,SourceHealthRules.retryWait("Thu, 01 Jan 1970 00:00:01 GMT",1000));
        assertEquals(0,SourceHealthRules.retryWait("Thu, 01 Jan 1970 00:00:01 GMT",2000));
        assertEquals(0,SourceHealthRules.retryWait("Wed, 31 Dec 1969 23:59:59 GMT",Long.MAX_VALUE));
        assertEquals(0,SourceHealthRules.retryWait("Wed, 31 Dec 1969 23:59:59 GMT",0));
        assertEquals(SourceHealthRules.MAX_WAIT,SourceHealthRules.retryWait("Tue, 06 Jan 1970 00:00:00 GMT",0));
        try{SourceHealthRules.retryWait(null,-1);fail();}catch(IllegalArgumentException expected){}
        assertEquals(60000,SourceHealthRules.retryWait("Thu, 01 Jan 1970 00:00:01 GMT junk",0));
        assertEquals(60000,SourceHealthRules.retryWait("Thu, 40 Jan 1970 00:00:01 GMT",0));
    }
}
