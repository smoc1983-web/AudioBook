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

Current released version is4.2.74 /100074 (2026-10-30): descriptions on cover tap,
offline annotation cache and independent title listen action. PR6, scoped41014370607
(API23/36:15/15 each), normal41015767058, signed inspection41018067403,
exact-report recovery41018566005 and public Site17 completed. Native-header clients
73/72 fetched every part and verified the full APK SHA. See docs/RELEASE-4.2.74.md
and docs/HANDOFF.md. This task is complete; ask whether to use ub-mode before
the next new development task. Next normal run74 plus permanent offset1 produces
75 /100075. Do not rebuild or replace published74 under the same code, rotate
the signing identity, or affect original stable/files/settings. Keep keys private.

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
