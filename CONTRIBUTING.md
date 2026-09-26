# Contributing

Thanks for taking a look at this project. It's a small demo, so the bar for
contributing is low, but here's how to make a change effectively.

## Getting set up

```bash
git clone https://github.com/vinayknl/redis-cache-stampede-demo.git
cd redis-cache-stampede-demo
docker compose up --build
```

That starts Redis and the app on `localhost:8080`. See the [README](README.md)
for the endpoints and the load-test script that demonstrates the stampede vs.
protected behavior.

For local (non-Docker) development you'll need a JDK 21 and either a local
Gradle install or a generated wrapper (`gradle wrapper`), plus a Redis
instance (`docker run -p 6379:6379 redis:7-alpine` is the easiest way).

## Making a change

1. Fork the repo and create a branch off `main`:
   `git checkout -b my-change`
2. Make your change. Keep it focused - one logical change per PR is much
   easier to review than a grab-bag.
3. Make sure it builds: `docker compose up --build`, and manually exercise
   `/api/naive/{id}` and `/api/safe/{id}` (see the README).
4. If you're changing behavior, update the README so it stays accurate -
   this project's whole value is being an easy-to-follow, correct reference.
5. Open a pull request against `main`. Describe *what* changed and *why*.
   CI (a Gradle build) runs automatically on every PR; it needs to pass
   before the PR can be merged.

## What kinds of contributions are welcome

- Bug fixes (including in the demo logic itself - if you find a scenario
  where the "safe" endpoint still stampedes, that's a real bug worth fixing)
- Clearer comments/README explanations
- Additional or improved tests
- Small, well-scoped feature additions that stay true to the project's
  purpose: a clear, correct demonstration of distributed cache-locking with
  Redis. This isn't the place for turning it into a general-purpose caching
  framework - if you want to go big, open an issue first to discuss it.

## Reporting bugs / suggesting changes

Open a GitHub issue using the provided templates. Include repro steps for
bugs (ideally exact `curl` commands and what you expected vs. saw).

## Code style

Plain, idiomatic Java. No new dependencies without a good reason (the point
of this repo is to be easy to read end-to-end). Match the existing style in
files you're editing.

## Code of Conduct

This project follows the [Contributor Covenant](CODE_OF_CONDUCT.md). Be kind.
