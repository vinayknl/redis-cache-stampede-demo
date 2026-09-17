package com.example.cachestampede.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires up a single Redisson client used for two things in this demo:
 * <ul>
 *   <li>reading/writing cached values (via {@code RBucket}), and</li>
 *   <li>the distributed lock ({@code RLock}) that protects the "expensive"
 *       backing store from a cache stampede (a.k.a. dogpile effect / thundering
 *       herd) when many requests miss the cache for the same key at once.</li>
 * </ul>
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
		// JSON codec so cached values are human-readable in redis-cli / Redis
		// Commander, and so we can store the Product record directly without
		// hand-rolling our own serialization.
		config.setCodec(new JsonJacksonCodec());
		config.useSingleServer()
				.setAddress("redis://" + redisHost + ":" + redisPort)
				.setConnectionPoolSize(20)
				.setConnectionMinimumIdleSize(5);
		return Redisson.create(config);
	}
}
