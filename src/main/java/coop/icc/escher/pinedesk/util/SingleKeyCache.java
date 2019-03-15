package coop.icc.escher.pinedesk.util;

import java.util.Map;
import java.util.HashMap;

public class SingleKeyCache<K, T> {
    private class Line<T> {
        public T val;
        public long lastAccessed;
    }

    public SingleKeyCache () {
        this(DEFAULT_CACHE_CAPACITY);
    }

    public SingleKeyCache (int capacity) {
        //Use a slightly larger capacity for the hash maps to avoid growth during normal operation
        int adjCapacity = (int)((1./HASH_LOAD_FACTOR) * (double)capacity);
        m_capacity = capacity;

        m_map = new HashMap<K, Line<T>> (adjCapacity, HASH_LOAD_FACTOR);
    }

    public boolean contains (K key) {
        return m_map.containsKey(key);
    }

    public T lookup (K key) { 
        Line<T> line = m_map.get(key);

        if (line == null) return null;

        line.lastAccessed = System.currentTimeMillis();
        return line.val;
    }

    public void evict (K key) { m_map.remove(key); }
    public void clear () { m_map.clear(); }

    public void insert (K key, T val) {
        if (contains(key)) return;

        Line line = new Line<T> ();
        line.lastAccessed = System.currentTimeMillis();
        line.val = val;

        if (m_map.size() == m_capacity) evictOldest();

        m_map.put(key, line);
    }

    private void evictOldest () {
        K oldestK = null;
        Line<T> oldestLine = null;

        for (Map.Entry<K, Line<T>> line : m_map.entrySet()) {
            if (oldestLine == null ||
                line.getValue().lastAccessed < oldestLine.lastAccessed) {
                oldestK = line.getKey();
                oldestLine = line.getValue();
            }
        }

        evict(oldestK);
    }

    private Map<K, Line<T>> m_map;
    private int m_capacity;
    
    private static final float HASH_LOAD_FACTOR = 0.75f;
    private static final int DEFAULT_CACHE_CAPACITY = 128;
}
