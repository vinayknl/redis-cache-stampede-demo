package com.example.cachestampede.service;

import com.example.cachestampede.dto.Product;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The fixed version of {@link NaiveCacheService}: cache-aside, but with a
 * Redis-backed distributed lock guarding the "recompute" step so that only
 * ONE process, on ONE instance, ever recomputes a given key at a time -
 * no matter how many app instances are running or how many requests arrive
 * concurrently.
 *
 * <p>Flow per request:</p>
 * <ol>
 *   <li>Read from Redis. Cache hit? Return immediately - no locking involved.</li>
 *   <li>Cache miss: try to acquire a short-lived distributed lock named
 *       {@code lock:product:<id>} via Redisson's {@link RLock#tryLock}.</li>
 *   <li>If the lock is acquired:
 *       <b>double-check the cache again</b> (someone may have just populated it
 *       while we were waiting for the lock), and only call the expensive
 *       backing store if it's still missing. Populate the cache, then release
 *       the lock in a {@code finally} block.</li>
 *   <li>If the lock is NOT acquired within the wait timeout (another instance
 *       is already recomputing this key): poll the cache briefly, because the
 *       lock holder is expected to populate it very soon. This turns "N
 *       concurrent backing-store calls" into "1 backing-store call + N-1
 *       cheap Redis reads".</li>
 * </ol>
 *
 * <p>Why a distributed lock and not a plain {@code synchronized} block? A
 * {@code synchronized} block only coordinates threads inside a single JVM.
 * The moment you run more than one instance of this service (which is the
 * normal case in production), each instance would independently miss the
 * cache and stampede the backing store - so the lock has to live somewhere
 * shared, i.e. in Redis.</p>
 */
@Service
public class LockedCacheService {

	private static final Logger log = LoggerFactory.getLogger(LockedCacheService.class);
	static final String KEY_PREFIX = "safe:product:";
	static final String LOCK_PREFIX = "lock:product:";
	private static final Duration TTL = Duration.ofSeconds(30);

	/** How long a request is willing to wait to either acquire the lock or see the cache populated by someone else. */
	private static final long LOCK_WAIT_TIME_MS = 5_000;
	/** Safety net: max time the lock is held before Redis force-releases it, in case the holder crashes mid-recompute. */
	private static final long LOCK_LEASE_TIME_MS = 10_000;
	/** How often a waiting request re-checks the cache while another request holds the lock. */
	private static final long POLL_INTERVAL_MS = 50;

	private final RedissonClient redissonClient;
	private final SlowBackingStore backingStore;

	private final AtomicLong cacheHits = new AtomicLong();
	private final AtomicLong lockWaits = new AtomicLong();

	@Autowired
	public LockedCacheService(RedissonClient redissonClient, SlowBackingStore backingStore) {
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

		RLock lock = redissonClient.getLock(LOCK_PREFIX + productId);
		boolean acquired = false;
		try {
			acquired = lock.tryLock(LOCK_WAIT_TIME_MS, LOCK_LEASE_TIME_MS, TimeUnit.MILLISECONDS);

			if (acquired) {
				// Double-checked locking: someone else may have populated the cache
				// between our first read and acquiring the lock.
				Product recheck = bucket.get();
				if (recheck != null) {
					cacheHits.incrementAndGet();
					return recheck;
				}

				log.info("LockedCacheService: lock acquired for {}, this request will call the backing store", productId);
				Product product = backingStore.fetch(productId);
				bucket.set(product, TTL.toSeconds(), TimeUnit.SECONDS);
				return product;
			}

			// Could not get the lock in time: someone else is already recomputing
			// this key. Rather than also hammering the backing store, wait for
			// them to finish and read what they wrote.
			log.info("LockedCacheService: lock busy for {}, waiting for the in-flight recompute to finish", productId);
			lockWaits.incrementAndGet();
			return waitForCacheToBePopulated(bucket, productId);

		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(e);
		} finally {
			if (acquired && lock.isHeldByCurrentThread()) {
				lock.unlock();
			}
		}
	}

	/**
	 * Polls Redis while another request holds the lock. If the holder doesn't
	 * finish before our own wait budget runs out (e.g. it crashed and the lock
	 * is about to be force-released), we fall back to computing it ourselves
	 * rather than failing the request outright.
	 */
	private Product waitForCacheToBePopulated(RBucket<Product> bucket, String productId) {
		long deadline = System.currentTimeMillis() + LOCK_WAIT_TIME_MS;
		while (System.currentTimeMillis() < deadline) {
			Product cached = bucket.get();
			if (cached != null) {
				cacheHits.incrementAndGet();
				return cached;
			}
			sleepQuietly(POLL_INTERVAL_MS);
		}

		log.warn("LockedCacheService: gave up waiting for {}; falling back to a direct backing-store call", productId);
		Product product = backingStore.fetch(productId);
		bucket.set(product, TTL.toSeconds(), TimeUnit.SECONDS);
		return product;
	}

	private void sleepQuietly(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	public long getCacheHits() {
		return cacheHits.get();
	}

	public long getLockWaits() {
		return lockWaits.get();
	}

	public void resetCounters() {
		cacheHits.set(0);
		lockWaits.set(0);
	}
}
