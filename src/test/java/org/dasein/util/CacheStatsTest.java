package org.dasein.util;

import java.io.BufferedReader;
import java.io.StringReader;
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
    
    public void testBuckets() throws Exception {
        System.setProperty("cache-stats.enabled-classes", String.class.getName());
        CacheStats.reloadProperties();
        CacheStats stats1 = CacheStats.getInstance(String.class);
        
        stats1.reportCollected(0L);
        stats1.reportCollected(1L);
        stats1.reportCollected(249L);
        stats1.reportCollected(250L);
        stats1.reportCollected(251L);
        stats1.reportCollected(499L);
        stats1.reportCollected(500L);
        stats1.reportCollected(501L);
        stats1.reportCollected(999L);
        stats1.reportCollected(1000L);
        stats1.reportCollected(1001L);
        
        final String summaries = CacheStats.getSummaries();
        assertNotNull(summaries);
        assertTrue(summaries.length()>0);
        System.out.println(summaries);
        
        BufferedReader in = new BufferedReader(new StringReader(summaries));
        String line;
        while((line=in.readLine())!=null) {
            if(line.equals("COLLECT counts:")) break;
        }
        if(line==null) fail("did not find COLLECT counts");
        
        assertEquals("0 - 250: 3", in.readLine());
        assertEquals("250 - 500: 3", in.readLine());
        assertEquals("500 - 1000: 3", in.readLine());
        assertEquals("1000 - 2000: 2", in.readLine());
        assertEquals("2000 - 4000: 0", in.readLine());
        
    }
    

}
