# Audiobook development instructions

Publication hold requested 2026-10-27: audit/review branch only until the user
explicitly approves publication. Do not merge into main, run the publishing
workflow, create/publish a release, change the public Site/feed, or distribute
a new APK. Test builds and saved source/report commits are authorized.

2026-10-27 follow-up: the user authorized a direct phone download of the audited
4.2.69 APK, without repeating tests. This permits that manual-download page only;
main, normal releases and the in-app update feed remain held. See
docs/audit-4.2.68/PHONE-CANDIDATE.md before the next update.

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
