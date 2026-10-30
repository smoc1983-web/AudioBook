# Inline collection continuations — work log

2026-10-30: user approved ub-mode for inline continuation cards and simpler menus.
Baseline released72; preserve package/signature/downloads/settings and notification scope.
Extracted the existing poster into a testable card without adding controls yet.
Added a behavioral Compose test: pressing Continuations must expand in-place,
collapse, keep the player unchanged, and keep open/menu actions separate.
Scoped CI is test-only; no signing or publication.

Implemented locally while the original no-control test runs: collection poster
buttons expand a bounded, compact panel within the shelf; the selected-shelf list
has the same control. Existing OfficialSeriesCatalog/Panel owns Litres/AT parsing,
cache, conflict checks and text/audio grouping. Source availability is bound to
this collected title's seed IDs, independent of notification mute. No global feed
or automatic player selection. Filename siblings are explicitly unverified.
LazyRow now includes all books instead of take(5); only the selected panel loads.
Shelf gear/X replaced by a named overflow menu; top collection transfer/notification
controls moved to Management. Book menu keeps listen/download/collection/delete
visible, with favorites/queue/reference details under Other actions. Delete still
requires its existing confirmation and preserves collection/preferences.

Pinned Crapper/Dryer/Mutator were actually invoked on changed Kotlin: unsupported,
empty scans are N/A, not quality passes. current/check.py structural check passes;
this is not compilation. Android/Compose and existing official-series tests remain
the meaningful behavior gate. New tests cover actual shelf + real seeded stores,
muted-notification display, audio/text links, unrelated-feed exclusion, menu action
routing, cards beyond fifth, inline collapse and player preservation.

Focused green workflow compiles debug/native-test APKs and runs real API23/36 cases. Full JVM, lint, JS, dependency and signed-upgrade checks belong to the subsequent normal release workflow; they are not repeated redundantly in both emulator jobs.

Original API36 run41003278510 compiled successfully and failed the single real control test at the missing Продолжения node. Artifact12955061682 retained as red-api36.xml/red.json. Green source0aa796957a8894f64b659132c45280c60ea63ed8 runs independently; no passing claim yet.

Green run41004363928 compiled both APIs and executed20 cases each. All four new
collection cases, six official-series cases, two recovery cases and download
regressions passed. One existing notification fixture failed on both APIs at
CollectionNotificationTest.kt:49: it waited for orphan notifications to remain
visible even though the real application automatically removes them on shelf
changes. Corrected only that unstable precondition to await its independent media
notice; kept every final assertion (own alert present, orphan/global absent,
media retained, history/routes/removal scopes correct). No notification production
code or assertions of final behavior weakened. Repeat native matrix required.

Final focused run41005118622 on source5a5d17acb690941ab2fba6c3acd881b63d47e2ae passed20/20 cases on each API23 and36, no failures/errors/skips. Actual XML and JSON saved. Next: normal signed build73 and upgrade/data/identity validation before public feed changes. Physical Samsung not tested.

Completed: normal41005934995 signed73 passed; actual release72→73 and public same-Site publication/whole-APK HTTPS hashes verified. Full receipts in release-4.2.73. No physical-phone verification claimed.
