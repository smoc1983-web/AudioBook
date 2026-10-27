package com.audiobookdownloader;

import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class CollectionNoticePolicyTest {
    @Test public void collectionSeedIsAllowed() {
        assertTrue(CollectionNoticePolicy.allows("hotel", Collections.singleton("hotel")));
    }
    @Test public void unrelatedBookCannotNotify() {
        assertFalse(CollectionNoticePolicy.allows("novgorod", Collections.singleton("hotel")));
    }
    @Test public void generalLitresOptInNeverReplacesCollectionMembership() {
        assertFalse(CollectionNoticePolicy.allows("litres", Collections.singleton("hotel")));
    }
    @Test public void emptyOrRemovedCollectionCannotNotify() {
        assertFalse(CollectionNoticePolicy.allows("hotel", Collections.emptySet()));
        assertFalse(CollectionNoticePolicy.allows("litres", Collections.emptySet()));
    }
}
