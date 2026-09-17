package com.example.cachestampede.service;

import com.example.cachestampede.dto.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stands in for whatever slow, expensive-to-call system sits behind the cache:
 * a heavy SQL query, an aggregation job, a call to a downstream API that
 * rate-limits you, etc. Every call sleeps for {@link #SIMULATED_LATENCY_MS}
 * and bumps a counter, so a load test can prove how many times this was
 * actually hit.
 */
@Component
public class SlowBackingStore {

	private static final Logger log = LoggerFactory.getLogger(SlowBackingStore.class);

	/** How "expensive" each backing-store call is. Tune this to make the stampede more or less dramatic. */
	public static final long SIMULATED_LATENCY_MS = 1500;

	private final AtomicLong callCount = new AtomicLong();

	public Product fetch(String productId) {
		long callNumber = callCount.incrementAndGet();
		log.info("SlowBackingStore: fetching product {} (this is call #{} to the backing store)", productId, callNumber);
		try {
			Thread.sleep(SIMULATED_LATENCY_MS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(e);
		}
		return new Product(productId, "Product " + productId, 19.99 + productId.hashCode() % 50, Instant.now());
	}

	public long getCallCount() {
		return callCount.get();
	}

	public void resetCallCount() {
		callCount.set(0);
	}
}
