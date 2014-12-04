package org.dasein.util;

import java.lang.management.ManagementFactory;
import java.util.EnumMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.StandardMBean;

/** track usage and age of items in ConcurrentCache and ConcurrentMultiCache 
 * 
 *  various ages or load times tracked in geometric histogram with a base "size" (in milliseconds) and number of buckets.
 *  if size is 100ms, then first bucket will contain the number of values between 0 and 100ms,
 *  the second bucket between 100ms and 200ms, the third between 200ms and 400ms, and continuing to double each time.
 *  the last bucket will always be "and everything else" -- all values larger than the previous bucket. 
 *
 *  configure with System properties (min bucket size)
 *      cache-stats.jmx-enabled         register mbean when class is loaded (default false)
 *      cache-stats.enabled-classes     regex matching fully-qualified classes to be monitored (default "", no classes)
 *      cache-stats.bucket-size.load    size in milliseconds of first n-tile in load times histogram (default 5)
 *      cache-stats.bucket-size.hit     size in milliseconds of first n-tile in cache hit age histogram (default 250)
 *      cache-stats.bucket-size.collect size in milliseconds of first n-tile in GCed entry age histogram (default 250)
 *      cache-stats.bucket-size.expire  size in milliseconds of first n-tile in expired entry age histogram (default 250)
 *      cache-stats.bucket-count        number of buckets in each histogram (default 12)
 */
public class CacheStats {
    /** exposed to simplify unit testing -- updates to allow changes to some properties */
    public static void reloadProperties() {
        enabledRegex = System.getProperty("cache-stats.enabled-classes", "");
        if(Boolean.getBoolean("cache-stats.jmx-enabled")) {
            final String packageName = CacheStats.class.getPackage().getName();
            final String className = CacheStats.class.getSimpleName();
            final MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            final String mbeanName = packageName + ":type=" + className;
            try {
                final ObjectName name = new ObjectName(mbeanName);
                StandardMBean standardMBean = new StandardMBean(new CacheStatsJMX(), CacheStatsMBean.class);
                mbs.registerMBean(standardMBean, name);
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
        
        statsByType.clear();
    }
    
    private static final CacheStats NULL_INSTANCE = new NullStats();
    private static String enabledRegex;

    private static ConcurrentMap<Class,CacheStats> statsByType = new ConcurrentHashMap<Class,CacheStats>();
    public static CacheStats getInstance(Class type) {
        if(type.getName().matches(enabledRegex)) {
            final CacheStats priorEntry = statsByType.putIfAbsent(type, new CacheStats());
            return priorEntry==null ? statsByType.get(type) : priorEntry;
        } else {
            return NULL_INSTANCE;
        }
    }

    private static int getIntProperty(String property, int defaultValue) {
        final String propValue = System.getProperty(property, Integer.toString(defaultValue));
        return Integer.parseInt(propValue);
    }
    
    public static void resetAll() {
        for(CacheStats stats : statsByType.values()) {
            stats.reset();
        }
    }

    // register MBean when class is loaded
    static {
        reloadProperties();
    };

    /////////////////////
    
    enum Event { 
        LOAD(5), 
        HIT(250), 
        COLLECT(250), 
        EXPIRE(250);
        
        final long bucketSize;
        private Event(int defaultSize) { 
            this.bucketSize = getIntProperty("cache-stats.bucket-size." + name().toLowerCase(), defaultSize);
        }        
    }
    
    private static int maxIndex = getIntProperty("cache-stats.bucket-count", 12) - 1;
    
    private int missCount;
    private Map<Event,AtomicInteger[]> countsByEvent;
    
    public synchronized void reset() {
        missCount = 0;
        countsByEvent = null;
    }
    
    /** report that a value was loaded from a backing store in the given time */
    public void reportLoad(long elapsed) {
        increment(Event.LOAD, elapsed);
    }
    
    /** report that a value with given age was returned from the cache */
    public void reportHit(long age) {
        increment(Event.HIT, age);
    }
    
    /** report that a reference with the given age was found in the cache, but had been garbage collected */
    public void reportCollected(long age) {
        increment(Event.COLLECT, age);
    }
    
    /** report that a value with the given age was found in the cache but isValidForCache returned false */
    public void reportExpired(long age) {
        increment(Event.EXPIRE, age);
    }
    
    /** report that no matching reference was found in the cache */
    public synchronized void reportMiss() {
        missCount++;
    }
    
    private void increment(Event event, long value) {
        final int index = getIndex(event, value);
        final AtomicInteger count = getCount(event, index);
        count.incrementAndGet();
    }
    
    private synchronized AtomicInteger getCount(Event event, int index) {
        if(countsByEvent==null) {
            countsByEvent = new EnumMap<Event,AtomicInteger[]>(Event.class);
        }

        AtomicInteger[] eventCounts = countsByEvent.get(event);
        if(eventCounts==null) {
            eventCounts = new AtomicInteger[maxIndex+1];
            for(int i=0;i<=maxIndex;i++) { eventCounts[i] = new AtomicInteger(); }
            countsByEvent.put(event, eventCounts);
        }
        
        return eventCounts[index];
    }
    
    private static int getIndex(Event event, long value) {
        if(value < event.bucketSize) { return 0; }
        final int adjustedValue = (int)(value/event.bucketSize);
        final int index = log2plus1(adjustedValue);
        return Math.min(index, maxIndex);
    }
    
    /** http://stackoverflow.com/questions/3305059 */
    private static int log2plus1(int n){
        return 32 - Integer.numberOfLeadingZeros(n);
    }

    public String getSummary() {
        final StringBuilder msg = new StringBuilder();
        appendSummary(msg);
        return msg.toString();
    }
    
    /** NOT synchronized -- values in output could be slightly inconsistent */
    private void appendSummary(StringBuilder msg) {
        for(Event event : Event.values()) {
            msg.append(event).append(" counts:\n");
            int j=1;
            for(int i=0;i<maxIndex;i++, j*=2) {
                msg.append(event.bucketSize*(j/2)).append(" - ").append(event.bucketSize*j).append(": ").append(getCount(event, i)).append("\n");
            }
            msg.append("> ").append(event.bucketSize*j/2).append(": ").append(getCount(event, maxIndex)).append("\n");
        }
        msg.append("MISS count: ").append(missCount).append("\n");
    }
    
    public static String getSummaries() {
        final StringBuilder msg = new StringBuilder();
        for(Class key : statsByType.keySet()) {
            msg.append("cache type: ").append(key.getCanonicalName()).append("\n");
            statsByType.get(key).appendSummary(msg);
            msg.append("\n");
        }
        return msg.toString();
    }
    
    /** for caches where stats are not enabled, use this object to avoid lots of null checks in calling code */
    private static class NullStats extends CacheStats {
        @Override public void reportLoad(long elapsed) { }
        @Override public void reportHit(long age) { }
        @Override public void reportCollected(long age) { }
        @Override public void reportExpired(long age) { }
        @Override public void reportMiss() { }

        @Override
        public String getSummary() {
            return "cache stats disabled";
        }
    }
    
    /////
    
    public interface CacheStatsMBean {
        public String getSummaries();
        public String getSummary(String className);
        public void reset();
    }
    
    private static class CacheStatsJMX implements CacheStatsMBean {
        
        @Override
        public String getSummaries() {
            return CacheStats.getSummaries();
        }

        @Override
        public String getSummary(String className) {
            try {
                Class type = Class.forName(className);
                return CacheStats.getInstance(type).getSummary();
            } catch(Exception e) {
                e.printStackTrace();
                return "failed: " + e;
            }
        }
        
        public void reset() {
            resetAll();
        }
    }
}
