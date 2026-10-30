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

Current public in-app channel is4.2.73 /100073 (Site16). Signed4.2.74 /100074
is implemented, merged and fully verified on GitHub (PR6, normal41015767058,
native41014370607, signed inspection41018067403, exact XML recovery41018566005).
It is available as private releasepreview-v100074; public Site publication is
pending solely because exec has No usable execution environment. Do not claim74
is in the public feed. See docs/RELEASE-4.2.74.md and docs/HANDOFF.md.
Do NOT rebuild to publish: use existing APK74, SHA876b5739e36c41340177534d2766be812cf597959dfa12b24b822ffc0ac01d5d.
Next normal run74+offset1 would make75; use [skip ci] for proof/hosting-only commits.
This book-description task's ub-mode and development/publication authorization
already exist; do not ask again within its continuation. Ask before a new task.
Preserve package/signature/library/settings and original stable. Keep keys private.

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
