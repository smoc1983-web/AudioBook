# Search corrections for 4.2.91 / 100091

Base: main df555d9a5345b28af5a6515875fb8e238c74824c (4.2.90).

## Native search results and the global aggregate

The existing UI merged source reports, but then filtered the native search response
again by title/author/cycle tokens. Search engines sometimes return installment
cards without the parent series or author field. The local filter silently discarded
such cards even when the source had returned them for the user's query. A narrower
query for that installment's actual title would find it.

`AbGlobalBookResults.nativeResult` now retains the native query context through the
same aggregation and row-preparation pipeline. `AbBookQuery` ranks exact title and
author matches first, then exact tokens, conservative prefixes (at least four
characters and no numeric tokens), cycle matches, and finally native-only search
hits. All query words are required for local matching. The native-only fallback is
restricted to the same normalized query; cached results cannot leak into another
query. Site adapters still own relevance checking, access checks and recommendation
exclusion. Only enabled/selected sources are queried, as before.

The regression `globalSeriesSearchRetainsFifthVolumeAcrossSourceReports` covers the
owner's «Файролл» example: volumes 1–4 from one source and a native result for
«Сицилианская защита» from another, without series metadata in its card. The fifth
result remains in the global list. This is a deterministic model of the failure,
not a claim of a live phone or all-site search test. Numeric series and volume order
remain exact; no fuzzy typo guessing was added.

## Empty or unverified editions

Availability is specific to a source URL, not to a title shared by different sites.
Source chips explicitly distinguish:

- «Главы не проверены»: no successful observation yet;
- «Главы найдены»: the reader parsed a nonempty playable chapter list;
- «Главы не найдены»: the settled reader page yielded no readable chapter list.

Previously observed empty editions sort below unknown and known-playable editions.
A playable alternative keeps a combined book above empty editions. Opening a source
again performs the ordinary chapter lookup and can restore its positive state.
Observations expire after 30 days and then become unknown again.

The empty marker is set only by the existing settled/no-playlist result or an actual
empty playlist, never by arbitrary timeout, HTTP, SSL, CAPTCHA or parse errors.
Unknown results are not silently treated as available or deleted. No automatic
visits to hundreds of book pages were added; the first observation occurs when the
reader opens an edition. A parsed playlist does not prove every remote audio URL
still downloads, and this update does not probe every media file.

## Scope and preservation

The update does not add audio-lib.club, rotate package/signing identity, modify
playback state, alter downloaded books, or change the source enable/disable policy.
The only visible change is the chapter-status line on each source chip. Existing
public GitHub Releases updater checks remain in place.
