# Published4.2.93; legacy hostname migration BLOCKED

Owner approved publication2026-11-04. Public GitHub preview-v100093/source6b41836
and Sites21/source736a98d are deployed. Read docs/BRIDGE-4.2.93.md and
publication.json. Native Sites canonical URL changed workspace suffix to smoc1983;
old amplecoot2048 host now HTTP307s to it. Known old updater forbids cross-host
redirect, so never claim transparent migration for that origin. Sites rejected
restoring its generated host as a custom domain. Phone100092 APK/origin remains
unverified. GitHub-origin clients can discover93; old-origin clients require the
platform to restore direct200 or a one-time same-package manual update.
Full anonymous GitHub APK and Site parts hashes verified. No new signing key,
no user-data deletion, no physical-phone installation, AudioBook main unchanged.
Current work branch cloud/publish-bridge93-20261104. Status below is historical.

# Current review status, 2026-11-04 — candidate93 NOT PUBLISHED

Public latest is preview-v100091/main c8301db; owner reports installed100092,
whose APK/origin is not available for inspection. Read docs/HANDOFF.md and
docs/BRIDGE-4.2.93.md before resuming. Branch cloud/candidate-bridge93-20261104 holds
signed100093 and a legacy-Site-to-GitHub bridge prepared WITHOUT ub-mode by the
owner's choice for this updater task. Runtime test/build/lint steps41763061062
passed; its candidate step rejected a stale version. Isolated candidate
CI41765003310 passed; publication requires separate owner permission.
No protected branches, live feeds, tags, APK releases or keys were changed.
Older status entries below are historical.

# Current Audiobook release:4.2.88/100088,2026-10-31

## Execution host preference (owner instruction, 2026-11-01)

Prefer the owner's connected Windows laptop DESKTOP-QG19B0C for development.
Before new or resumed work, read docs/REMOTE-DEVELOPMENT.md and check the actual
Codex host connection plus scripts/check-development-host.py. If the laptop is
unavailable, continue in a cloud Codex environment from backup/audiobook-laptop
on a new cloud/recovery-* branch, reporting the snapshot time and omitted files.
Do not overwrite local uncommitted work or push cloud work to the backup branch.
An offline local chat cannot migrate itself: do not claim autonomous failover.
The existing per-task ub-mode choice and release/signing safeguards still apply.

Compact player option2 and selected themes task completed WITH owner-approved ub-mode.
Full41220372143/sourcea13ac3c6955d95ad999b322ec425b57633d90ec5 passed.
Focused41221382589/source472ece78b20a80de21ea977abd9821e4264c9310 passed43/44;
onlyAndroidTest/docdiff, production paths identical. PublicSite20 serves100088;
upgrade85→88 retaineddata, originalpackage/certificatepreserved. Read latestdocs/HANDOFF.md,
docs/RELEASE-4.2.88.md,docs/PLAYER-DESIGN.md and docs/quality/PLAYER-DESIGN-LOG.md.
Before nextNEWdevelopmenttask ask owner's ub-mode choice. No further tests/builds for
unchanged completedwork. Nextnormal89 unless interveningruns;mainproofmerge[skip ci].
Hostingarchive231086080bytes;measure nextpayload against256MiB. Preserve user files.
Historical instructions/status below are superseded by this current header.

# Audiobook development instructions

Latest user preference (2026-10-29): before starting each application development
task, ask whether to use https://github.com/unclebob/ub-mode. Await the choice
before applying or bypassing that workflow. The user approved ub-mode for the
context-menu downloads / edition details task (2026-10-29).
The user also approved ub-mode for reliable book deletion and collection downloads
on2026-10-30; do not ask again within that combined task. Ask for the next task.
User approved ub-mode for inline collection continuations and simpler menus on2026-10-30; do not ask again within this task.
User approved ub-mode for book description on cover tap on2026-10-30; do not ask again within this task.
This supersedes the earlier unconditional ub-mode instruction below.

The nine library/player improvements task completed2026-10-30 as4.2.78/100078.
Owner explicitly declined ub-mode for this task; ordinary JVM/native/security/upgrade
checks passed. Before the next new development task ask the owner's choice again.
Do not rebuild/re-publish78 or rotate package/signature. See docs/LIBRARY-EXPERIENCE.md,
docs/RELEASE-4.2.78.md, docs/HANDOFF.md and exact docs/release-4.2.78/ evidence.
Current public Site18 serves100078; full41099446302 and focused41099442777 passed.
Compiled de9ae460371f4a165762f5cff23834168cbb129b. Native-header74/73 downloaded every
part and matched the signed APK SHA. Next normal raw78+permanentoffset1 produces79.
Physical phone, Android updater network APK download and PlayProtect remain untested.
Main release/proof merge uses[skip ci]; no extra application build is required.

The user explicitly requested ub-mode on 2026-10-27. For future code changes,
read and apply [.agents/skills/ub-mode/SKILL.md](.agents/skills/ub-mode/SKILL.md)
and [docs/QUALITY.md](docs/QUALITY.md). Treat this file as durable project memory.
Read docs/HANDOFF.md before resuming work. Keep short progress updates during work.

- Reproduce defects, fix causes, make small verifiable changes, test observable
  behavior, simplify duplication, and record checks and limitations honestly.
- Use the pinned Crapper, Dryer and Mutator for supported changed source files.
  Do not interpret an unsupported language or an empty scan as a passing gate.
  Kotlin and embedded JavaScript need the existing Android and JS tests.
- Start mutation checks with a specific file and a real passing test command;
  avoid a whole-repository mutation run or release publishing from mutant workers.
- Keep source edits separate from disposable tool caches and metrics. Preserve
  uncommitted work and existing user authorization. Never publish signing keys.
- Preserve the installed package/signing identity, downloaded library, preferences
  and collection data. Keep original stable Audiobook intact. Do not touch the
  ForTanya music repository while working on Audiobook.
- Skill practices do not grant permission to contact others or spawn subagents.
  Existing user instructions and higher-priority instructions take precedence.
- Before GitHub changes inspect the remote head: this checkout can have different
  local commit IDs with identical trees. Never blindly push or force-push main.

Task2026-10-31 completed as4.2.85/100085, WITHOUT ub-mode by explicit owner choice.
Full41176810494 and focused41176809415 passed; compiled09c9c7446ef8347c561f8ce5130780524bfa40dc.
Public Site19 serves100085 with preserved package/cert; upgrade78→85 retained data.
See docs/RELEASE-4.2.85.md, docs/SEARCH-COLLECTION-LAYOUT.md and release proofs.
Next normal86 unless intervening runs; merge/docs commits use[skip ci].
Ask the owner about ub-mode before the next NEW development task.
Current Site archive263976960 bytes is near256MiB: prepare verified retention before next publication.

Active task: compact player option2, bound to selected Audiobook themes. Owner approved ub-mode for this task. Do not ask again. Baseline85 preserved until verified replacement.
