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

Further safety pass found cover revision values are Long, not generic book-cover
strings. Codec now preserves their type; roundtrip seeds/asserts that exact value.
Malformed collection structures and missing declared audio cannot replace prefs.
Preference disk-write failure restores previous in-memory selected values.
Readability controls extracted with their own preference listener and a large-font
UI check. Debug-only loopback network policy/test exercises real DownloadManager
on API29+ (focused36); production cleartext policy remains unchanged.

Scoped41083667680 on7efd723:API36 passed29/29;API23 passed28/29, widget RemoteViews
setAlpha(float) is not remotely callable on23. Fixed renderer to setImageAlpha(int),
keeping API23 in the suite. This was an actual compatibility defect, not a skipped
assertion. Recovery periodic work is registered on application creation so an
interruption between saved intents and immediate work scheduling is eventually
recovered, including after reboot (subject to Android scheduling/force-stop rules).

Audio ZIP manifest now records each expected entry name and byte length before
writing. Both export and restore verify lengths, preventing a cancelled partial
ZIP entry with a valid CRC from looking like a full chapter. Duplicate/unlisted
entries, truncated chapters and missing audio fail before preference commit;
partial SAF documents are removed. Copy loops honor cancellation, including
before preference commit. Existing fully copied files remain; existing user
chapters never overwritten. UI discloses actual bounds50GiB/2GiB-per-chapter.
Metadata-only restore needs no URI grants; gallery wallpaper/folder access may
need reselection. No signing/credentials/Android-job grants included.

Scoped41087406048 on0a3b9bd: API36 passed33/33 including actual Android
DownloadManager HTTP bytes, queue reload and preserving the finished file on cancel.
API23 ran32 tests (the API29+ network probe is excluded): two failures, one old
test image exporter wrongly referenced MediaStore.Downloads (29+), and the absent
source description missed its5s deadline. Widget setImageAlpha rendering itself
no longer threw. Exporter now uses private external evidence files onpre29;
missing source is reported immediately without dispatching an unnecessary IO task,
and the5s assertion is retained. No test was skipped to hide these failures.
Queue JSON snapshots are cached/bounded, UI polling stops while backgrounded,
startup recovery errors cannot break the main screen, and chapter cancellation
captures the selected book before dispatch. Disk-write failure restores the prior
queue value. Local execution transport disconnected again; GitHub remains usable.
After recovery preserve the local worktree before syncing these remote changes.

Final scoped41090081599 on a598e4f succeeded on both API23 and36. The last-chapter
completion event was then guarded against known partial local playlists and
known previews; four ordinary JVM regression tests cover that cleanup policy.
No UI/platform API changed in that small guard. The complete original build-apk
gate will run from build/library-experience, preserving its existing run counter
(next run74+offset1=75), signer, baseline74 and all regular checks. This isolates
the candidate from main and avoids invoking the optional ub-mode analysis declined
by the owner. No gate was removed. Signed private publication is allowed for that
exact build branch only; original-stable auxiliary publication remains main-only.
Source and private candidate are not the public HTTPS feed; publish only after
the complete gate and signed artifact inspection. Main's final docs/merge must
use [skip ci] to avoid an unnecessary second build/version; record compiled SHA.

## Release gate follow-up

The first complete release gate (41091566152, candidate 100075) stopped on the offline description refresh UI test. It did not publish an APK. Debug and release JVM XML reports each contain 147 tests, no failures/errors, and one optional skip. The Android XML contains 74 tests, one failure, no errors, and three skips; the failure was a five-second wait for refresh feedback after a physical tap. The prior scoped API 23 and 36 jobs both passed. This is not a completed release.

Description refresh and close actions now stay in a fixed dialog footer, outside the scrolling description and edition details. Loading/error feedback stays beside the actions and cached text remains readable after a failed refresh. The regression test retains the physical tap and five-second error assertion, adds a loader invocation assertion, and checks visible controls with a long description at 150% text size. A new complete gate must pass before publication.

The next full gate41095515200 (candidate100076) passed both147-test JVM variants and the Android XML75/0failures/0errors/3skips, including the formerly failing physical offline refresh and long-description case. Scoped41095511814 succeeded on23 and36. It then stopped at three ProduceStateDoesNotAssignValue lint findings, so76 also did not publish. Exact lint locations and XML receipts were read by41098018302 attempt2 after a transient503 report download.

The three asynchronous producers now use explicit remembered mutable state plus LaunchedEffect. Background reads remain onDispatchers.IO; effects cancel when their keys/foreground state change. Existing foreground snapshots stay visible while polling stops. This uses the underlying Compose state/effect pattern directly, without a lint suppression or reduced gate. A new full77 gate is required before publication.

A further request review found that unmetered-only was insufficient for the UI promise “Wi-Fi only”: mobile links can also be unmetered. New requests now explicitly restrict DownloadManager to NETWORK_WIFI when selected, retaining the existing metered/roaming policy. The actual loopback-to-DownloadManager byte comparison/reload/cancel test now runs with the Wi-Fi setting enabled and restores it afterwards. This requires a fresh complete candidate78; no earlier candidate is published.
