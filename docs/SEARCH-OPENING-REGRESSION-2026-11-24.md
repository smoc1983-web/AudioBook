# 2026-11-24 — Test 4.2.114 regression

Reported on device: valid editions are rejected with title mismatch; Android ANR during Fayroll cycle search. Screenshots: source titles with SEO suffixes, cycle prefix/volume in official title. Fix: opening-specific identity comparison, explicit known series prefix/volume only, exact remaining title and author if available. Same check for preflight and verified catalog. Cycle alias lookup indexed once; replace per-byte Formatter in availability key with equivalent hex; yield between volume steps. These reduce CPU work but ANR root cause is not proven without traces.

Tests: OpenedBookIdentityTest includes both reported examples, wrong author/title/volume negatives. User acceptance pending; no claim of device verification.
