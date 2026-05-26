package com.smartbike.rental.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class CacheService {

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    // Graceful in-memory fallback cache
    private final Map<String, CacheEntry> fallbackCache = new ConcurrentHashMap<>();

    private boolean useFallback = false;

    public void put(String key, Object value, long ttlSeconds) {
        if (!useFallback && redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS);
                return;
            } catch (Exception e) {
                System.err.println("[CACHE] Redis connection failed, switching to in-memory fallback. Error: " + e.getMessage());
                useFallback = true;
            }
        }
        
        fallbackCache.put(key, new CacheEntry(value, ttlSeconds));
    }

    public Object get(String key) {
        if (!useFallback && redisTemplate != null) {
            try {
                return redisTemplate.opsForValue().get(key);
            } catch (Exception e) {
                System.err.println("[CACHE] Redis lookup failed, switching to in-memory fallback. Error: " + e.getMessage());
                useFallback = true;
            }
        }

        CacheEntry entry = fallbackCache.get(key);
        if (entry == null) {
            return null;
        }

        if (entry.isExpired()) {
            fallbackCache.remove(key);
            return null;
        }

        return entry.value;
    }

    public void delete(String key) {
        if (!useFallback && redisTemplate != null) {
            try {
                redisTemplate.delete(key);
            } catch (Exception e) {
                useFallback = true;
            }
        }
        fallbackCache.remove(key);
    }

    public void reset() {
        useFallback = false; // Attempt to reconnect to Redis next time
        fallbackCache.clear();
    }

    // Wrap objects with simulated TTL in the fallback memory cache
    private static class CacheEntry {
        final Object value;
        final long expiryTimestamp;

        CacheEntry(Object value, long ttlSeconds) {
            this.value = value;
            this.expiryTimestamp = System.currentTimeMillis() + (ttlSeconds * 1000);
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiryTimestamp;
        }
    }
}
