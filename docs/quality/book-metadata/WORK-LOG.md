# Stage 8: book metadata editor

- Continued authorized stage 8 with ub-mode and TokenEconomizer CLI; no release permission used.
- Fetched origin and confirmed clean base e4378972dee3055ce17e472a7fa19d0802375120. Created cloud/book-metadata-20261118; main unchanged.
- Audited LibraryBook/bookId, canonical title keys, chapter identities, ServiceTrack serialization, source metadata, backup allowlist and all title renderers before editing.
- Chose display-only overrides by stable bookId. No file renames, title-key migration, source-evidence edits, package/version/signing changes.
- Added Java boundary rules test first; observed compiler red for absent production class, then 3 passing tests with JaCoCo.
- Added strict JSON codec, compare-and-set save, explicit reset with corrupt raw snapshot, failed-commit visibility guard. Writes run on IO from editor.
- Added editor and independent playback metadata identity transport; canonical titles/edition/chapter IDs untouched. Library filter/sort, rows/posters, player/mini-player, system metadata and widgets use display values.
- Kept personal series as a label rather than false evidence for network continuation discovery. Added source narrator persistence separately from user overrides.
- Added backup allowlist/validation for overrides; URI grants, damaged recovery snapshots and credentials remain excluded.
- Added codec and Android store/UI preservation, wrong-edition, concurrent edit, corrupt record and failed-commit tests. Results pending until actual execution.
