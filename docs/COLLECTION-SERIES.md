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
