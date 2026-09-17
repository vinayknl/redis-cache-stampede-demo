package com.example.cachestampede.dto;

/**
 * Counters returned by /api/stats so a load test can show, in numbers,
 * how many times the "expensive" backing store was actually hit versus
 * how many requests were served from Redis. Run one scenario at a time
 * (call /api/reset in between) so backingStoreCalls reflects only that run.
 */
public record DemoStats(
		long backingStoreCalls,
		long naiveCacheHits,
		long safeCacheHits,
		long safeLockWaits
) {
}
