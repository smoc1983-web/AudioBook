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

Final review reuses existing bibliographic title normalization (ordinal/narrator
suffixes) and protects distinct known series orders when titles coincide. Sort keys
are computed once per book, not repeatedly inside comparison. Source-authority cache
is applied before the final query filter. Focused suite now includes the600-row
scroll/return/append regression. Complete build-apk gate can run from the isolated
build/search-collections-layout branch, preserving the original signing/version and
all ordinary checks, without invoking declined optional ub-mode analysis.

Follow-up: player bookmark/queue/series/chapter panels open immediately in bounded
paged dialogs; startup smoke asserts all core controls before any swipe and opens
bookmark/queue/chapter windows. Explicit reset has its own action: clears search
results/cache and cover-memory LRU, while ordinary home navigation keeps the TTL cache.
The collection picker add/toggle regression is tested separately from idempotent
removal. Unified search now uses the same single identity policy (URL aliases,
explicit author, conflicting volume protection) rather than pre-merging ambiguous
records. Digit/letter series codes normalize consistently. Release notes updated;
full gate79 pending, public78 unchanged.

The player tab row now uses equal width cells, not the generic horizontally
scrollable chips: bookmark/queue counts must not push Series past the right edge.
Startup assertions cover both horizontal and vertical visible bounds. This is a
required fix for the no-scroll request; the superseded79 candidate must not be
published. A fresh full80 gate will verify the exact final source.

A strict-query negative case rejects “Отделение15-К” for “Отдел15-К”: token-prefix
matching would admit a different word, so relevance now requires complete normalized
query tokens. Roman-number and digit/letter normalization are retained. Candidate80
is superseded before publication; exact final source needs a fresh complete81 gate.
No further feature additions are planned; fix only observed failures from this gate.
