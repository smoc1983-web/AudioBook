# Inline collection continuations — work log

2026-10-30: user approved ub-mode for inline continuation cards and simpler menus.
Baseline released72; preserve package/signature/downloads/settings and notification scope.
Extracted the existing poster into a testable card without adding controls yet.
Added a behavioral Compose test: pressing Continuations must expand in-place,
collapse, keep the player unchanged, and keep open/menu actions separate.
Scoped CI is test-only; no signing or publication.
