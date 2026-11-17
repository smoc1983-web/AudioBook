package com.audiobookdownloader;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class SeekHistoryRulesTest {
    private final String a=ChapterIdentity.edition("Same title","https://source.example/a");
    private final String b=ChapterIdentity.edition("Same title","https://source.example/b");
    private SeekHistoryRules.Entry point(int id,String edition,int from,int to,long time) {
        return new SeekHistoryRules.Entry(String.format(Locale.ROOT,"%032x",id),edition,ChapterIdentity.chapter(edition,"01"),from,to,time);
    }
    @Test public void keepsFiftyLatestCommandsInInsertionOrderEvenWhenClockMovesBackwards() {
        List<SeekHistoryRules.Entry> rows=Collections.emptyList();
        for(int i=1;i<=60;i++)rows=SeekHistoryRules.append(rows,point(i,a,0,i,100-i));
        assertEquals(50,rows.size());assertEquals(60,rows.get(0).to);assertEquals(11,rows.get(49).to);
    }
    @Test public void zeroAndLongChapterPositionsStayExact() {
        SeekHistoryRules.Entry point=point(1,a,0,Integer.MAX_VALUE,0);
        List<SeekHistoryRules.Entry> rows=SeekHistoryRules.append(Collections.emptyList(),point);
        assertEquals(0,rows.get(0).from);assertEquals(Integer.MAX_VALUE,rows.get(0).to);assertEquals(0,rows.get(0).time);
    }
    @Test public void noOpAndDuplicateDeliveryDoNotDisplacePreviousReturnPoint() {
        SeekHistoryRules.Entry first=point(1,a,1000,30000,1);
        List<SeekHistoryRules.Entry> rows=SeekHistoryRules.append(Collections.emptyList(),first);
        rows=SeekHistoryRules.append(rows,point(2,a,50,50,2));assertEquals(1,rows.size());assertSame(first,rows.get(0));
        rows=SeekHistoryRules.append(rows,first);assertEquals(1,rows.size());assertSame(first,rows.get(0));
    }
    @Test public void newCommandsNeverMutateInputAndOutputCannotBeEdited() {
        List<SeekHistoryRules.Entry> input=new ArrayList<>();input.add(point(1,a,1,2,1));
        List<SeekHistoryRules.Entry> result=SeekHistoryRules.append(input,point(2,a,2,3,2));
        assertEquals(1,input.size());assertEquals(2,result.size());input.clear();assertEquals(2,result.size());
        try{result.clear();fail();}catch(UnsupportedOperationException expected){}
    }
    @Test public void editionsRemainSeparateDespiteIdenticalBookTitles() {
        List<SeekHistoryRules.Entry> rows=SeekHistoryRules.append(Collections.emptyList(),point(1,a,1,2,1));
        rows=SeekHistoryRules.append(rows,point(2,b,2,3,2));
        assertEquals(1,SeekHistoryRules.edition(rows,a).size());assertEquals(a,SeekHistoryRules.edition(rows,a).get(0).edition);
        assertTrue(SeekHistoryRules.edition(rows,"unknown").isEmpty());
        try{SeekHistoryRules.edition(rows,a).clear();fail();}catch(UnsupportedOperationException expected){}
    }
    @Test public void returnResolvesStableChapterAfterReorderButRefusesForeignMissingOrAmbiguousTargets() {
        SeekHistoryRules.Entry p=point(1,a,1234,9000,1);String other=ChapterIdentity.chapter(a,"02");
        assertEquals(1,SeekHistoryRules.resolve(p,a,Arrays.asList(other,p.chapter)));
        assertEquals(-1,SeekHistoryRules.resolve(p,b,Arrays.asList(p.chapter)));
        assertEquals(-1,SeekHistoryRules.resolve(p,a,Arrays.asList(other)));
        assertEquals(-1,SeekHistoryRules.resolve(p,a,Arrays.asList(p.chapter,p.chapter)));
    }
    @Test public void malformedIdsAndNegativeValuesAreRejectedAtBoundary() {
        String id=String.format(Locale.ROOT,"%032x",1),chapter=ChapterIdentity.chapter(a,"01");
        Object[][] bad={{null,a,chapter,0,1,0L},{"../file",a,chapter,0,1,0L},{id,null,chapter,0,1,0L},
            {id,"https://example.org",chapter,0,1,0L},{id,a,null,0,1,0L},{id,a,"missing",0,1,0L},
            {id,a,chapter,-1,1,0L},{id,a,chapter,0,-1,0L},{id,a,chapter,0,1,-1L}};
        for(Object[] r:bad)try{new SeekHistoryRules.Entry((String)r[0],(String)r[1],(String)r[2],(int)r[3],(int)r[4],(long)r[5]);fail(Arrays.toString(r));}catch(IllegalArgumentException expected){}
    }
}
