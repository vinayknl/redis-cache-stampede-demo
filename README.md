# Redis Distributed Cache-Locking Demo (Spring Boot + Gradle)

A small, runnable demo of **cache stampede** (a.k.a. the "dogpile effect" or
"thundering herd") and how to prevent it with a **Redis-backed distributed
lock**. Built with Spring Boot 3 / Java 21 / Gradle, and packaged with Docker.

## The problem

With a plain cache-aside pattern (check cache → miss → fetch from the real
source → write to cache), if a popular key expires or is cold, **every
concurrent request that misses at the same time calls the expensive backing
store**. If that backing store is a database or a downstream API, a spike of
concurrent misses can take it down — this is a cache stampede.

## The fix

Guard the "recompute" step with a **distributed lock** so only one caller,
across your entire fleet of app instances, recomputes a given key at a time.
Everyone else either:
- gets served straight from the cache (if it wins the race after the lock
  holder finishes), or
- waits briefly and then reads the value the lock holder just wrote.

A distributed lock is required (not a JVM `synchronized`/local lock) because
in production you typically run more than one instance of the service — a
local lock only stops the stampede within a single process. This demo uses
[Redisson](https://github.com/redisson/redisson)'s `RLock`, which implements
a robust distributed lock on top of Redis (lease/expiry so a crashed holder
doesn't deadlock everyone else, re-entrancy, etc.).

## What's in this repo

| Class | Role |
|---|---|
| `SlowBackingStore` | Simulates the expensive thing behind the cache (1.5s "DB call") and counts how many times it was actually hit. |
| `NaiveCacheService` | Cache-aside **with no protection**. Every concurrent miss calls the backing store. Used to demonstrate the problem. |
| `LockedCacheService` | Cache-aside **with a Redisson distributed lock** around the recompute step, plus double-checked locking and a wait-and-poll fallback. This is the fix. |
| `ProductController` | Exposes `/api/naive/{id}` and `/api/safe/{id}` — same data, only the locking differs — plus `/api/stats` and `/api/reset` for the demo. |

### How `LockedCacheService` works

```
read cache
  hit  -> return
  miss -> tryLock("lock:product:<id>", waitTime=5s, leaseTime=10s)
            acquired:
              re-check cache (another request may have just filled it)
              still missing -> call backing store, write cache
              unlock
            not acquired (someone else is already recomputing):
              poll the cache every 50ms until it appears, or give up
              and compute directly as a last-resort fallback
```

The `leaseTime` is a safety net: if the lock holder crashes mid-recompute,
Redis force-releases the lock after 10s instead of leaving every other
instance stuck waiting forever.

## Running it

### Option A: Docker Compose (recommended, no local Java/Gradle needed)

```bash
docker compose up --build
```

This starts:
- `redis` on `localhost:6379`
- the app on `localhost:8080`

### Option B: locally with Gradle

You'll need a local Gradle install (or generate the wrapper once with
`gradle wrapper`, which requires internet access) and a Redis instance:

```bash
docker run -p 6379:6379 redis:7-alpine
gradle bootRun   # or ./gradlew bootRun once you've generated the wrapper
```

## Seeing the stampede, then seeing the fix

With the app running, use the included load test script to fire many
concurrent requests for the *same* product id and compare how many times the
backing store actually got called:

```bash
# The bug: every concurrent request misses and calls the backing store.
./scripts/load-test.sh naive 20

# The fix: only one request calls the backing store; the rest wait or hit cache.
./scripts/load-test.sh safe 20
```

Expected output (abbreviated):

```
mode=naive, concurrency=20 -> backingStoreCalls: ~20   (stampede)
mode=safe,  concurrency=20 -> backingStoreCalls: 1     (protected)
```

You can also drive it manually:

```bash
curl -X POST http://localhost:8080/api/reset
curl http://localhost:8080/api/naive/demo-1     # or /api/safe/demo-1
curl http://localhost:8080/api/stats
```

Watch the app logs too — `NaiveCacheService` logs a backing-store call for
every concurrent miss, while `LockedCacheService` logs exactly one "lock
acquired, calling backing store" line and N-1 "lock busy, waiting" lines.

## Notes / things to try next

- Reduce `SlowBackingStore.SIMULATED_LATENCY_MS` and TTLs to make the race
  window tighter or looser and see how it changes the numbers.
- Scale the app to multiple instances (`docker compose up --scale app=3
  --build`, behind a load balancer of your choice) to see the lock coordinate
  across processes, not just within one JVM — that's the whole point of using
  Redis instead of a local lock.
- An alternative/complementary technique not implemented here is **logical
  (soft) expiration with background refresh**: store a `expiresAt` field
  inside the cached value itself, serve stale data immediately past that
  time while a single request (again gated by a lock) refreshes it in the
  background. That trades a moment of staleness for zero caller-facing
  latency spikes, which is worth it for many read-heavy workloads.
