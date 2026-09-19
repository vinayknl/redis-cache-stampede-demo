package com.example.cachestampede.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires up a single Redisson client used for the distributed lock
 * ({@code RLock}) that protects the "expensive" backing store from a cache
 * stampede (a.k.a. dogpile effect / thundering herd) when many requests miss
 * the cache for the same key at once.
 *
 * <p>Cache VALUES themselves are stored as plain JSON strings (see
 * {@link com.example.cachestampede.service.NaiveCacheService} /
 * {@link com.example.cachestampede.service.LockedCacheService}, which use
 * Spring's own auto-configured {@code ObjectMapper} - already JSR-310 aware -
 * plus Redisson's built-in {@code StringCodec}) rather than Redisson's
 * generic JSON codec, to sidestep its default polymorphic ("@class") typing,
 * which is easy to get subtly wrong for record types.</p>
 */
@Configuration
public class RedissonConfig {

	@Value("${app.redis.host:localhost}")
	private String redisHost;

	@Value("${app.redis.port:6379}")
	private int redisPort;

	@Bean(destroyMethod = "shutdown")
	public RedissonClient redissonClient() {
		Config config = new Config();
		config.useSingleServer()
				.setAddress("redis://" + redisHost + ":" + redisPort)
				.setConnectionPoolSize(20)
				.setConnectionMinimumIdleSize(5);
		return Redisson.create(config);
	}
}
