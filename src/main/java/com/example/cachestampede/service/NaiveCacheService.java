package com.example.cachestampede.service;

import com.example.cachestampede.dto.Product;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
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
 *
 * <p>Cached values are stored as plain JSON strings (via Spring's own
 * auto-configured, JSR-310-aware {@code ObjectMapper} and Redisson's
 * {@code StringCodec}) rather than Redisson's generic JSON codec, to avoid
 * its default polymorphic ("@class") typing.</p>
 */
@Service
public class NaiveCacheService {

	private static final Logger log = LoggerFactory.getLogger(NaiveCacheService.class);
	static final String KEY_PREFIX = "naive:product:";
	private static final Duration TTL = Duration.ofSeconds(30);

	private final RedissonClient redissonClient;
	private final SlowBackingStore backingStore;
	private final ObjectMapper objectMapper;
	private final AtomicLong cacheHits = new AtomicLong();

	@Autowired
	public NaiveCacheService(RedissonClient redissonClient, SlowBackingStore backingStore, ObjectMapper objectMapper) {
		this.redissonClient = redissonClient;
		this.backingStore = backingStore;
		this.objectMapper = objectMapper;
	}

	public Product get(String productId) {
		RBucket<String> bucket = redissonClient.getBucket(KEY_PREFIX + productId, StringCodec.INSTANCE);

		String cachedJson = bucket.get();
		if (cachedJson != null) {
			cacheHits.incrementAndGet();
			return readValue(cachedJson);
		}

		// Cache miss: every concurrent caller falls through to here and hits the
		// backing store independently. Nothing coordinates them.
		log.info("NaiveCacheService: cache miss for {}, calling backing store directly (no coordination)", productId);
		Product product = backingStore.fetch(productId);
		bucket.set(writeValue(product), TTL.toSeconds(), TimeUnit.SECONDS);
		return product;
	}

	public long getCacheHits() {
		return cacheHits.get();
	}

	public void resetCacheHits() {
		cacheHits.set(0);
	}

	private String writeValue(Product product) {
		try {
			return objectMapper.writeValueAsString(product);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private Product readValue(String json) {
		try {
			return objectMapper.readValue(json, Product.class);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
