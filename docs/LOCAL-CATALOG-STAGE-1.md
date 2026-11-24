# Stage 1 — local catalogue index (pending validation)

Base: test 4.2.113 (68b3cb0). No release or build is requested.

- Android SQLiteOpenHelper + FTS4 unicode61, no new dependencies. Independent database search_catalog_v1.db.
- One entry per known source/book page; separate narration URLs remain independent. Searchable title, author, narrator, series, genre. No media URLs or library state; old JSON is unchanged.
- One-time bounded import of search-cache-v2 metadata at launch, retry if interrupted; no network required. Incoming search results also populate the index. Older observations cannot overwrite newer ones.
- 90-day age limit and 20,000 entry cap; payload capped at 64 KiB. Existing JSON cache remains the network-search cache and fallback. No user data migration or deletion.
- localMatches() is an IO-only entry point for stage 2; results are cached metadata, not proof of current audio availability. No changes to search ranking, screen, playback or download logic in stage 1.
- Instrumented tests cover Russian normalization, independent narrations, persistence, source filtering, stale entries and upsert. Not executed; verify FTS4 on API 23 and API 36 in a later authorized build.
- Next: surface cached results separately, merge them with live results and preserve diagnostic network modes.
