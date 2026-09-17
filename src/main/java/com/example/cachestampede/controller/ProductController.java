package com.example.cachestampede.controller;

import com.example.cachestampede.dto.DemoStats;
import com.example.cachestampede.dto.Product;
import com.example.cachestampede.service.LockedCacheService;
import com.example.cachestampede.service.NaiveCacheService;
import com.example.cachestampede.service.SlowBackingStore;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * Two endpoints fetching the exact same kind of data through the exact same
 * kind of "expensive" backing store - the only difference is whether the
 * cache-miss path is guarded by a distributed lock. Fire concurrent requests
 * at /naive/{id} vs /safe/{id} for the same id right after /reset and watch
 * `backingStoreCalls` in /stats to see the difference.
 */
@RestController
@RequestMapping("/api")
public class ProductController {

	private final NaiveCacheService naiveCacheService;
	private final LockedCacheService lockedCacheService;
	private final SlowBackingStore backingStore;
	private final RedissonClient redissonClient;

	@Autowired
	public ProductController(NaiveCacheService naiveCacheService, LockedCacheService lockedCacheService,
			SlowBackingStore backingStore, RedissonClient redissonClient) {
		this.naiveCacheService = naiveCacheService;
		this.lockedCacheService = lockedCacheService;
		this.backingStore = backingStore;
		this.redissonClient = redissonClient;
	}

	/** No stampede protection - concurrent misses all hit the backing store. */
	@GetMapping("/naive/{id}")
	public Product getNaive(@PathVariable String id) {
		return naiveCacheService.get(id);
	}

	/** Redis distributed-lock protected - only one caller recomputes per key. */
	@GetMapping("/safe/{id}")
	public Product getSafe(@PathVariable String id) {
		return lockedCacheService.get(id);
	}

	@GetMapping("/stats")
	public DemoStats stats() {
		return new DemoStats(
				backingStore.getCallCount(),
				naiveCacheService.getCacheHits(),
				lockedCacheService.getCacheHits(),
				lockedCacheService.getLockWaits()
		);
	}

	/**
	 * Clears both caches and resets counters so a load test can be repeated
	 * from a clean, comparable state. Only deletes this demo's own keys, not
	 * the whole Redis instance.
	 */
	@PostMapping("/reset")
	public void reset() {
		redissonClient.getKeys().deleteByPattern("naive:product:*");
		redissonClient.getKeys().deleteByPattern("safe:product:*");
		redissonClient.getKeys().deleteByPattern("lock:product:*");
		backingStore.resetCallCount();
		naiveCacheService.resetCacheHits();
		lockedCacheService.resetCounters();
	}
}
