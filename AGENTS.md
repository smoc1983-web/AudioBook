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

Active task2026-10-31: search ordering/relevance/dedup/cache/reset, per-book collection actions, conditional local deletion, widget4x1/4x2 and compact player. Owner chose WITHOUT ub-mode; ordinary checks required. Do not ask again in this task. Preserve signature/package/library/progress.
