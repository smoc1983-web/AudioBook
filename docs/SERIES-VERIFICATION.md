# Series metadata and release tracking

The source catalogue can describe a broad fictional world rather than a direct series. For example, Knigoblud's Отдел 15-К contains Золото мертвых; Author.Today identifies that work as Хранитель кладов #2. Litres also keeps Хранитель кладов separate and links its parent Отдел 15-К.

## Changes

- Classify cycle links by explicit metadata and dedicated routes. A sibling BookDescriptionSeriesItem anchor is a book, never a cycle. Reject previously cached numbered/narrator labels.
- Verify title plus full author against a public Author.Today or Litres book-detail page. Search cards only discover candidates. Store direct cycle, number, provenance, and a separate parent-world label. Unknown authors require an unambiguous official candidate. A failed lookup preserves previous evidence.
- Independent metadata workers run outside playback/search with two network permits, finite page/redirect/time budgets, 14-day positive and 15-minute negative caching.
- Player → Серия displays local volume matches, verified metadata, source book listings, and follow controls. Filename matches are labelled and do not generate release notifications. Different explicitly known authors remain separate.
- Recover missing legacy source addresses from existing private/SAF metadata sidecars before an exact title/author source lookup. Knigoblud UUIDs require the book's own labelled cycle block; they are not globally reclassified as cycle URLs. Its broad list is intersected with the official direct-series list and audio availability before the background snapshot advances. When the reference search has no match, a source-only fallback requires the book’s own explicit cycle list, identical series-base names throughout, the seed itself, matching authors and matching own-cycle links on every playable detail page; it remains labelled as source metadata. Mixed worlds fail this fallback.
- Store already published/local books separately from release notices. A successful first snapshot is silent but its existing volumes remain visible. Failed checks retain old lists and snapshots; muted collection titles stop being seeds.
- Optional Litres new-audiobook feed is off by default. Public audio-only cards establish a silent baseline. Later entries need a matching audio detail page and explicit Дата выхода на Литрес within the unprocessed time boundary; future/unknown dates do not alert. Text editions are excluded. Links open the official site, not the app audio extractor. No paid media or login is accessed.
- Native “Что нового” screen appears once after an unacknowledged version update, with a manual settings entry. Fresh installation does not open it automatically.

## Limits

External layouts/availability can change. Ambiguous identity, incomplete paginated reference lists, more than ten matching Knigoblud narrations, unavailable audio, or unknown release dates are reported/deferred, never treated as proof of a new book. Local filename grouping helps legacy books even when online verification fails. Android may defer 12-hour WorkManager jobs. Notifications require system permission. This does not merge private app sandboxes or change the installed app package/signing identity.

## Validation

Metadata-only recorded fixtures under app/src/test/resources/series-metadata document the exact public pages. JVM tests cover identity, direct/parent distinction, existing Hotel volume 2, release dates, and update acknowledgement. Native SeriesRecoveryTest covers recovery with a missing old URL, an already published second volume, exclusion of another subseries, third-volume notification, deduplication, offline retention, muting, and Litres baseline/retry. The existing complete CI suite and actual old-to-new installation checks remain required before public publication.
