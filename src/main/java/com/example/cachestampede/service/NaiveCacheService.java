package com.example.cachestampede.service;

import com.example.cachestampede.dto.Product;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The textbook "cache-aside" pattern with NO stampede protection:
 *
 * <pre>
 *   1. read from Redis
 *   2. if present, return it
 *   3. if absent, compute it from the backing store and write it back
 * </pre>
 *
 * The bug: if N requests for the same key arrive while the cache is empty
 * (cold start, TTL just expired, key evicted, ...), ALL N of them will miss
 * the cache and hammer the backing store at once. That's cache stampede /
 * the "thundering herd" / "dogpile effect". This class exists so the demo
 * can show that failure mode before showing the fix in {@link LockedCacheService}.
 */
@Service
public class NaiveCacheService {

	private static final Logger log = LoggerFactory.getLogger(NaiveCacheService.class);
	static final String KEY_PREFIX = "naive:product:";
	private static final Duration TTL = Duration.ofSeconds(30);

	private final RedissonClient redissonClient;
	private final SlowBackingStore backingStore;
	private final AtomicLong cacheHits = new AtomicLong();

	@Autowired
	public NaiveCacheService(RedissonClient redissonClient, SlowBackingStore backingStore) {
		this.redissonClient = redissonClient;
		this.backingStore = backingStore;
	}

	public Product get(String productId) {
		RBucket<Product> bucket = redissonClient.getBucket(KEY_PREFIX + productId);

		Product cached = bucket.get();
		if (cached != null) {
			cacheHits.incrementAndGet();
			return cached;
		}

		// Cache miss: every concurrent caller falls through to here and hits the
		// backing store independently. Nothing coordinates them.
		log.info("NaiveCacheService: cache miss for {}, calling backing store directly (no coordination)", productId);
		Product product = backingStore.fetch(productId);
		bucket.set(product, TTL.toSeconds(), TimeUnit.SECONDS);
		return product;
	}

	public long getCacheHits() {
		return cacheHits.get();
	}

	public void resetCacheHits() {
		cacheHits.set(0);
	}
}
