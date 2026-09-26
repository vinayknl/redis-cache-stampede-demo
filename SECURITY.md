# Security Policy

This is a small educational/demo project (showing cache-stampede protection
with a Redis distributed lock), not a production library, but real issues
are still worth reporting responsibly.

## Reporting a Vulnerability

If you find a security issue (for example, something that would let an
attacker bypass the lock in a way that has real security impact, or an
unsafe default in the Docker setup), please **do not open a public issue**.

Instead, report it privately via GitHub's "Report a vulnerability" feature
on this repository's Security tab, or by contacting the maintainer directly
through their GitHub profile (@vinayknl).

Please include:
- A description of the issue and its potential impact
- Steps to reproduce it
- Any suggested fix, if you have one

## Scope

Keep in mind this project intentionally includes a demo endpoint
(`/api/naive/{id}`) that is *unprotected by design*, to illustrate the
cache-stampede problem. That is expected behavior, not a vulnerability -
`/api/safe/{id}` is the protected counterpart.

## Response

As a solo-maintained project, response times aren't guaranteed, but reports
will be looked at as soon as possible.
