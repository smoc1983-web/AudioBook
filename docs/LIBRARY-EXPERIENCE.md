# Library/player improvements — work in progress

Owner authorized all nine suggestions and declined ub-mode2026-10-30. Item9 is
limited to readability/accessibility, wallpaper dim/text/card opacity and one extra
flat no-glow theme. No additional animation/gesture changes requested.

Baseline public4.2.74 /100074, source5140e74f05d3148a9579771b6f07aae07b53b8b1.
Preserve .preview package and3a4cecd1… certificate, original stable and user library.
ForTanya/SimpMusic deletion was separately blocked by missing integration rights;
these repositories are not part of this task. Owner-approved old Actions cleanup
already completed215/2162324626bytes; do not repeat it.

Implementation staged, not yet verified:
1. Compact collection list/cover switch and persisted collapsible shelves.
2. Common cover description expanded into a book card with listen/collection,
   edition chapter/size/duration and series actions, without changing playback.
3. Durable bounded book/chapter queue using WorkManager+DownloadManager, pending
   intent persisted before enqueue; progress/errors/retry, per-book queue pause,
   Wi-Fi for newly submitted chapters, completed chapter preservation.
   Manual pause allows current chapters to finish; it does not claim byte-range
   pause/resume. DownloadManager handles network interruptions itself.
4. Explicit ZIP export/import: typed allowlisted settings, shelves/favorites/
   positions/bookmarks, optional streamed audio. Preview+confirmation; no keys,
   tokens/Android jobs/URI grants. Existing audio is never overwritten. SAF temp
   document+rename, hostile path/size/count checks; no shared folder recursive wipe.
5. Storage list by actual known audio bytes, partial and newly completed books;
   confirmed safe existing deletion pipeline for individual or listened books.
6. Official series list gets device/completed/text-only status; title-only local
   matches stay labelled as such, existing authority/conflict rules retained.
7. Configurable seek distances; existing long-pause rewind retained; configurable
   sleep fade and pre-sleep bookmark. Completion recorded only at actual last end.
8. One contextual update action, release size/notes and readable network errors;
   package/version/fullhash/signer/provider/Android-confirmation checks retained.
9. Appearance dim0–80%, extra font scale85–150% respecting Android scale, card
   fill65–100%, theme10Graphite with no glow/reflections/wallpaper by default.
   Matching widget palette added, prior theme IDs remain stable.

Remaining: compile/fix, meaningful native/UI/provider/queue/backup checks on23/36,
regular source/permission/Lint/dependency/upgrade gates, inspect signed release,
source/manifest/proofs and ai-project-memory checkpoint. Public Site17 already
near256MiB raw limit: next APK requires measured old-hosting-asset retention,
keep current74/previous73/72/manual69 and back up any unreferenced removed assets.
No fresh Site or secrets in source; ordinary Git CAS/no force push.

Initial scoped41080992681 failed compilation because exposing the full AbActions
for a shared panel also exposed a private genre type. Replaced that dependency
with narrow book callbacks and kept screen/actions models private. Run41082049765
compiled and API36 executed24 tests:22 pass,2 failures were old cover-layout
assertions running against the new default compact list. Cover-mode regressions
now explicitly select cover mode; new compact-mode test covers collapse. New
backup (including real SAF write/rename/no overwrite), queue and flat theme cases
passed on36;23 outcome/final newer source remain to be verified.
Bulk collection requests now use an atomic batch persistence path; closing the
metadata dialog does not cancel accepted books. Corresponding UI regression is
updated to assert both accepted book requests survive closure. APK publication
still forbidden until normal correctness/upgrade gates finish.
