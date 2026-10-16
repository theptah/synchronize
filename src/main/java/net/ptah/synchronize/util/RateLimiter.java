package net.ptah.synchronize.util;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

public final class RateLimiter {
    private static final int MAX_TRACKED_KEYS = 8192;
    private final int maxRequests;
    private final long windowMillis;
    private final ConcurrentHashMap<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public RateLimiter(int maxRequests, long windowMillis) {
        this.maxRequests = Math.max(1, maxRequests);
        this.windowMillis = Math.max(1000L, windowMillis);
    }

    public boolean tryAcquire(String key) {
        if (key == null || key.isBlank()) return false;
        if (!hits.containsKey(key) && hits.size() >= MAX_TRACKED_KEYS) {
            sweep();
            if (!hits.containsKey(key) && hits.size() >= MAX_TRACKED_KEYS) return false;
        }
        Deque<Long> deque = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        long now = System.currentTimeMillis();
        synchronized (deque) {
            trim(deque, now);
            if (deque.size() >= maxRequests) {
                return false;
            }
            deque.addLast(now);
            return true;
        }
    }

    public void sweep() {
        long now = System.currentTimeMillis();
        hits.entrySet().removeIf(entry -> {
            Deque<Long> deque = entry.getValue();
            synchronized (deque) {
                trim(deque, now);
                return deque.isEmpty();
            }
        });
    }

    private void trim(Deque<Long> deque, long now) {
        while (!deque.isEmpty() && now - deque.peekFirst() > windowMillis) {
            deque.pollFirst();
        }
    }
}
