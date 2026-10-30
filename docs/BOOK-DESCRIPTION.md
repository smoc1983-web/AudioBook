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
