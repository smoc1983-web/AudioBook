# Book description on cover tap — ub-mode log

2026-10-30: user explicitly approved ub-mode for this task. Baseline released73.
Common AbCover currently has no description action; parent rows/posters open player.
No annotation field/cache exists. Add a separate cover action/dialog, read only the
selected work's supported source, preserve playback and cache plain text privately.
Retain original package/signature, downloaded audio, collections and existing menus.

Parser behavior pinned before implementation: six JVM cases, five expected failures
with empty extraction stub (red-jvm.log). Cases require selected-work identity,
source-specific summary nodes, JSON-LD author binding, canonical match, script/
recommendation removal, paragraph preservation, empty/error and size limits.

10/10 standalone parser tests now pass with real Jsoup and JSON. Fresh JaCoCo,
Crapper: all Java methods CRAP<10 after extracting input validation (parse10→6);
Dryer has no candidates>=0.8. Mutator ran3 real guard mutations on line16 with
passing JUnit command; all3 killed,not a whole-file mutation score. Kotlin tools
were invoked and are unsupported/N/A. Logs and actual coverage saved.

Initial native41012648062 stopped at Kotlin compile (text.length() used instead of
text.length),before tests. Fixed the property access; repeat native matrix needed.
No released APK/feed changed. Parser,cache,dialog tests and player isolation remain
required before signed74/publication. Annotation cache is private; no audio writes.

Native41013491276 compiled. Cache/offline/refresh/cancellation/URL tests passed,
but two real cover/title regressions failed: merged poster hit target centered on
the newly interactive cover instead of the title. Removed the poster-wide click;
title is now its own accessible44dp listen action, cover independently opens
annotation. Existing title-open assertions retained unchanged.

Added an inspected source-recovery regression: remember the dialog by book title,
not page URL, so resolving a previously missing source cannot close it mid-load.
Added a genuine failing JVM fixture where a recommendation's h1 falsely identified
another page; moved recommendation pruning before identity checks. JVM11/11 now
pass; fresh JaCoCo/Crapper<10/Dryer and3 guard mutants regenerated. Both source and
URL must identify this work. Live Bookinfo HTTP200/120091bytes: Сыны грома returned
1252chars of annotation; frozen Новый каменный век6 returned522chars. Raw HTML
retained only in scratch, not published as app/user content. Native rerun required.

Final native41014370607 on sourcee1cf58ec672b65601a2c98f222528d8a2068136d succeeded on API23 and36:15 tests started/finished each; JVM142 passed+1optional skip each. Actual completed job logs verified; artifacts12959181462/12959580815 retained. PR source-analysis41014583440 succeeded; targeted Java metrics/mutations recorded separately. The local execution environment became unavailable before XML extraction; all code is on GitHub. Full signed74/upgrade gate can run there; APK inspection and same-Site publication require restored execution access. Public feed remains73 until verified deployment.
