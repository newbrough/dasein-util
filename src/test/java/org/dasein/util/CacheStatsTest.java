package org.dasein.util;

import java.util.Random;

import junit.framework.TestCase;

public class CacheStatsTest extends TestCase {
    Random random  = new Random();
    
    public void testUseCase() {
        System.setProperty("cache-stats.enabled-classes", ".*");
        CacheStats.reloadProperties();
        CacheStats stats1 = CacheStats.getInstance(String.class);
        CacheStats stats2 = CacheStats.getInstance(Number.class);
        
        for(int i=0;i<100;i++) {
            stats1.reportCollected(random.nextLong() % 10000L);
            stats2.reportCollected(random.nextLong() % 10000L);
            stats1.reportExpired(random.nextLong() % 10000L);
            stats2.reportExpired(random.nextLong() % 10000L);
            stats1.reportHit(random.nextLong() % 10000L);
            stats1.reportLoad(random.nextLong() % 10000L);
            stats1.reportMiss();
        }
        
        final String summaries = CacheStats.getSummaries();
        assertNotNull(summaries);
        assertTrue(summaries.length()>0);
        System.out.println(summaries);
    }
    
    public void testDefaultDisabled() {
        System.clearProperty("cache-stats.enabled-classes");
        CacheStats.reloadProperties();
        
        CacheStats stats1 = CacheStats.getInstance(String.class);
        CacheStats stats2 = CacheStats.getInstance(Number.class);
        
        for(int i=0;i<100;i++) {
            stats1.reportCollected(random.nextLong() % 10000L);
            stats2.reportCollected(random.nextLong() % 10000L);
            stats1.reportExpired(random.nextLong() % 10000L);
            stats2.reportExpired(random.nextLong() % 10000L);
            stats1.reportHit(random.nextLong() % 10000L);
            stats1.reportLoad(random.nextLong() % 10000L);
            stats1.reportMiss();
        }
        
        final String summaries = CacheStats.getSummaries();
        assertEquals("", summaries);
    }
}
