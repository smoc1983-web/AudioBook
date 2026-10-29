# Audiobook development instructions

Latest user preference (2026-10-29): before starting each application development
task, ask whether to use https://github.com/unclebob/ub-mode. Await the choice
before applying or bypassing that workflow. The user approved ub-mode for the
context-menu downloads / edition details task (2026-10-29).
This supersedes the earlier unconditional ub-mode instruction below.

Publication explicitly authorized 2026-10-27 after fixing collection notifications.
The user now requests publication through the in-app Updates menu. The earlier
hold has ended for this verified fix and the previously audited changes.
Preserve package/signature/library/settings. Publish from main through the normal
checks, then update the existing public feed. Version69 was delivered manually;
normal workflow run69 uses scripts/with-release-number.sh to build version70.
Never publish signing keys. Do not silently rotate the installed signing identity.

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
