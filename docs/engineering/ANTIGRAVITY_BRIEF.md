# Netfetch: Antigravity Implementation Brief

## Objective
Inspect the actual Netfetch Android project and improve its networking
reliability and performance using evidence, tests, and reproducible
benchmarks.

## Existing local research
Two installed applications were exported for offline inspection:

1. netshare.wifihotspot
   - APK: research/netshare/netshare-wifihotspot.apk
   - Version observed: 53-rm
   - Target SDK observed: 36
   - APK size observed: approximately 648 KB

2. com.app.netshare
   - APK: research/netshare/com-app-netshare.apk
   - Version observed: 2.2.0
   - Target SDK observed: 36
   - APK size observed: approximately 66 MB

JADX produced partial source output for both packages but reported
decompilation errors. Verify important conclusions against actual output.
The package metadata and permissions do not establish their internal
networking algorithms.

## Required investigation
A. Inspect Netfetch's manifest, Gradle configuration, services, UI,
   networking classes, lifecycle handling, and tests.
B. Inspect the decompiled research for relevant, readable implementations:
   - VPN and proxy architecture
   - socket creation, timeouts, and cleanup
   - DNS and connection failure handling
   - Android connectivity callbacks
   - foreground service lifecycle
   - buffering, concurrency, and retry policy
C. Compare findings with Netfetch. Record file paths and concrete evidence.
D. Identify the highest-impact defects before adding new architecture.
E. Implement focused fixes with tests and run the available Android build.
F. Report actual build/test output and unresolved limitations.

## Performance and correctness measurements
Where applicable, measure:
- DNS resolution success and latency
- TCP connection establishment time
- HTTP request success and time to first byte
- transfer throughput under comparable conditions
- recovery time after connectivity returns
- CPU/memory behavior and repeated start/stop stability

Separate DNS latency, connection latency, server latency, and throughput.
Use multiple trials and report failures and variability. Do not promise
zero lag: performance also depends on radio signal, congestion, ISP,
server distance, and the remote service.

## Constraints
- Preserve the existing application architecture unless evidence justifies
  a change.
- Do not introduce unbounded threads, retries, queues, or buffers.
- Do not bypass Android VPN consent or security controls.
- Do not copy proprietary implementation code into Netfetch.
- Do not commit APKs, decompiled source, credentials, or test data.
- Do not report an unexecuted test as passing.
- Inspect git diff and git status before finishing.

## Expected deliverables
1. Architecture and defect summary.
2. Prioritized implementation plan.
3. Focused production code changes.
4. Unit tests and any practical integration tests.
5. Build/test results and reproducible benchmark instructions.
6. Final diff summary and remaining risks.
