# Bounded native cancellation control (development only)

The tiny P6 control can finish before external mouse input reaches Cancel. That
outcome is CONVERTED, never a successful cancellation. This helper creates a
separate resource-load fixture for the unchanged public simple-content path.
It is not another supported mod, a new conversion category or a performance target.

`P6BoundedCancellationFixture.java` reads only the existing hash-pinned authored
`build/p6-public/negative-fixtures/p6-cancel.jar`. It preserves every original
entry byte, including the sole Java 8 class, and adds 24 language JSON files of
64 unused QA strings, 8,000 ASCII characters each. Original en_us/pt_br are not
replaced. Fixed Random seed, sorted entries, STORED ZIP and fixed timestamps make
the input reproducible. Aggregate size is below 13 MiB; each JSON below 1 MiB,
strings below the existing 16,384-character parser bound, with only 65 value nodes.
No limit is relaxed, game thread paused or cancellation hook injected.

Compile only this standalone Java 17 helper into an owned build directory, then
run it with the existing project root. The original JAR is never a classpath entry.
Output is restricted to `build/public-current-negatives-2026-09-07-run1`;
an existing output must be byte-identical, not overwritten. Run it twice to
check reproducibility. The helper verifies every original entry and source hash.

Before native cancellation, qualify a complete conversion with the existing CLI,
default included data, fresh output/report names and no mapping environment
override. This checks that the workload is an accepted resource control, not a
malformed archive or a disguised unsupported case. Keep this static conversion
separate from native UI acceptance; do not install either control as gameplay QA.

The bounded control retains modId `p6_cancel`: never put it beside the small
control in the same input inventory. Move only the previously operation-owned
copy of the small control into the QA archive before adding the bounded copy.
Never move/overwrite the authored source under `build/p6-public/negative-fixtures`.
All other originals, staged outputs, loaded JARs and journals stay untouched.

Native oracle: real UI Cancel or closing the conversion screen must yield an
accepted CANCELLED result with no output for that job and no orphan partial.
A fully completed conversion is a lost cancellation race, not a false PASS.
No original class may resolve in the class-loading trace. Window/thread timing
remains an observed condition, not a universal latency promise.
