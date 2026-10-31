# Search, collection actions and layout — work in progress2026-10-31

Owner chose WITHOUT ub-mode; ordinary native/JVM/security/upgrade checks required.
Baseline public4.2.78/Site18 is unchanged. Preserve package/certificate/library/progress.

Implemented first pass: relevance by title/author/series tokens (not descriptions),
series grouping and numeric order, narrator-variant dedup preserving source links;
bounded per-source in-memory LRU10min/24entries/6000books, no cached errors;
explicit search reset clears cache and existing search state without touching service,
library or preferences. Common menu removes from a named collection, idempotently;
existing batch download retained, per-chapter downloads retain full known edition total.
Delete-files offered only for actual local chapters.4x1 cover has bounded geometry;
4x2 cover/controls use available width. Player cover/details reduced and bookmarks/
queue/series controls moved ahead of content. Seek labels reflect configured intervals.

Pending: actual compilation/native checks, stronger UI/regression checks for real
widget bounds, chapter choice, local-delete visibility and compact player geometry;
review relevance/sort handling of unknown/conflicting series orders. Do not claim
strict chronological truth when sources do not supply confirmed order. No publication
before full gate/upgrade/signature/actual APK checks. Current workflow is focused-only.

First focused run41173054814 compiled on both23/36 and entered native tests from761fead.
Follow-up adds exact widget cover/control-width assertions, individual-chapter UI
regression preserving total count and playback, and full startup no-scroll checks
for player controls. Authority-cache-enriched rows are re-filtered against the query,
so corrected cycles cannot retain unrelated results. Collection picker remains a
separate add/toggle action; removals are idempotent and scoped to one shelf.
