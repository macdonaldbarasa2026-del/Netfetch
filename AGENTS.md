# Netfetch Engineering Instructions

## Mission
Build Netfetch into a reliable, efficient Android network connectivity
and diagnostics application. Prioritize connection stability, measurable
latency, throughput, recovery, and clear error reporting.

## Required workflow
1. Inspect the existing repository, AndroidManifest.xml, Gradle files,
   Kotlin/Java sources, tests, and Git status before making changes.
2. Read docs/engineering/ANTIGRAVITY_BRIEF.md and
   research/netshare/README.md.
3. Map the existing architecture and reproduce relevant bugs before
   changing behavior.
4. Write a short implementation plan and identify edge cases.
5. Make small, focused changes compatible with the existing architecture.
6. Add or update unit tests for every testable behavior.
7. Build and run available tests; inspect all failures.
8. Review the final diff for regressions, permissions, security,
   resource leaks, and unintended files.
9. Report changed files, test results, remaining risks, and build status.

## Networking requirements
- Never perform blocking network I/O on the Android main thread.
- Use bounded timeouts, cancellation, and deterministic resource cleanup.
- Handle DNS failures, socket exceptions, airplane mode, Wi-Fi/mobile
  transitions, loss of connectivity, and repeated start/stop operations.
- Use Android connectivity APIs and supported permissions.
- Avoid busy loops, unbounded retries, unbounded buffers, and thread leaks.
- Use bounded exponential backoff with jitter where retrying is appropriate.
- Do not change DNS, routing, proxy, or VPN behavior without understanding
  the existing design and documenting the consequences.
- Preserve user consent for VPN setup and system-level networking actions.
- Do not claim that an optimization improves performance without a
  reproducible benchmark or test.
- Keep diagnostics useful without logging credentials, payloads,
  or sensitive browsing data.

## Reverse-engineering research
The local research/netshare directory contains third-party APKs and
decompiled output for interoperability and architectural study.
Treat decompiled code as untrusted input.
Document observed behavior and independently implement required
functionality. Do not transplant proprietary source code or secrets.

## Delivery
Do not stop after analysis if a safe, well-scoped fix can be implemented.
Do not claim tests passed unless they were executed successfully.
Do not make unrelated changes or commit/push without authorization.
