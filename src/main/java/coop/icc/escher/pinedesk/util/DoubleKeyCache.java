package coop.icc.escher.pinedesk.util;

import java.util.Map;
import java.util.HashMap;

public class DoubleKeyCache<K1, K2, T> {
    private class Line<T> {
        public T val;
        public long lastAccessed;
    }

    public DoubleKeyCache () {
        this(DEFAULT_CACHE_CAPACITY);
    }

    public DoubleKeyCache (int capacity) {
        //Use a slightly larger capacity for the hash maps to avoid growth during normal operation
        int adjCapacity = (int)((1./HASH_LOAD_FACTOR) * (double)capacity);
        m_capacity = capacity;

        m_k1Map = new HashMap<K1, Line<T>> (adjCapacity, HASH_LOAD_FACTOR);
        m_k2Map = new HashMap<K2, Line<T>> (adjCapacity, HASH_LOAD_FACTOR);
    }

    public boolean containsK1 (K1 key) {
        return m_k1Map.containsKey(key);
    }
    public boolean containsK2 (K2 key) {
        return m_k2Map.containsKey(key);
    }

    public T lookupK1 (K1 key) { 
        Line<T> line = m_k1Map.get(key);

        if (line == null) return null;

        line.lastAccessed = System.currentTimeMillis();
        return line.val;
    }
    public T lookupK2 (K2 id) {
        Line<T> line = m_k2Map.get(id);

        if (line == null) return null;

        line.lastAccessed = System.currentTimeMillis();
        return line.val;
    }

    public void evictK1 (K1 key) {
        if (!containsK1(key)) return;

        Line<T> line = m_k1Map.get(key);
        K2 key2 = null;

        for (Map.Entry<K2, Line<T>> k2Line : m_k2Map.entrySet()) {
            if (k2Line.getValue() == line) {
                key2 = k2Line.getKey();
                break;
            }
        }

        m_k1Map.remove(key);
        m_k2Map.remove(key2);
    }
    public void evictK2 (K2 key) {
        if (!containsK2(key)) return;

        Line<T> line = m_k2Map.get(key);
        K1 key1 = null;

        for (Map.Entry<K1, Line<T>> k1Line : m_k1Map.entrySet()) {
            if (k1Line.getValue() == line) {
                key1 = k1Line.getKey();
                break;
            }
        }

        m_k1Map.remove(key1);
        m_k2Map.remove(key);
    }

    public void clear () {
        m_k1Map.clear();
        m_k2Map.clear();
    }

    public void insert (K1 key1, K2 key2, T val) {
        if (containsK1(key1)) return;

        Line line = new Line<T> ();
        line.lastAccessed = System.currentTimeMillis();
        line.val = val;

        if (m_k1Map.size() == m_capacity) evictOldest();

        m_k1Map.put(key1, line);
        m_k2Map.put(key2, line);
    }

    private void evictOldest () {
        K1 oldestK1 = null;
        Line<T> oldestLine = null;

        for (Map.Entry<K1, Line<T>> line : m_k1Map.entrySet()) {
            if (oldestLine == null ||
                line.getValue().lastAccessed < oldestLine.lastAccessed) {
                oldestK1 = line.getKey();
                oldestLine = line.getValue();
            }
        }

        evictK1(oldestK1);
    }

    private Map<K1, Line<T>> m_k1Map;
    private Map<K2, Line<T>> m_k2Map;
    private int m_capacity;
    
    private static final float HASH_LOAD_FACTOR = 0.75f;
    private static final int DEFAULT_CACHE_CAPACITY = 64;
}
