# Gradual MainActivity decomposition

Base: Audiobook Test, pending ranking-cycle fixes. MainActivity.kt at audit: 16,081 lines, 828,316 characters. The source is copied into the Android build by app/build.gradle.kts.

## First small extraction
AbPlayerProgress.kt: read-only progress UI and its two persistent display settings, shared by the main player and the book information dialog. Existing AbBookProgress / AbBookTimeRules and PlaybackService remain the single source of timing and playback state. Defaults: show both. Hiding the book panel stops its duration-resolution effect. No changes to saved listening positions or the APK identity.

## Proposed next steps, not yet implemented
1. Move the remaining player screen and its player-specific UI into AbPlayerScreen.kt.
2. Extract library and collection screens with their own UI state into AbLibraryScreen.kt and AbCollectionsScreen.kt.
3. Separate the search/home screen and its dialogs from the search coordinator, navigation restoration and source browser lifecycle. Search is the largest and riskiest screen; do it last.
4. Keep MainActivity responsible for lifecycle, navigation, shared state and service wiring. Shared UI components remain separate. Use internal interfaces instead of making private functions public indiscriminately.
5. Move one coherent section at a time; compare behavior and run regressions before the next extraction. Do not combine a wholesale refactor with the current search fixes.

No build or tests executed, per existing deferred-build workflow. On-device verification is pending.

## Second extraction and APK size

Moved AbPlayerScreen, its transport, chapter, bookmark and tool UI into AbPlayerScreen.kt (311 lines). Shared interfaces remain internal. Removed obsolete release-notes Activity registration and launch hooks. The JSON release notes remain the updater's input. Test CI converts nine decorative backgrounds to WebP, keeps dimensions/alpha, does not touch atlas/icon/audio or repository originals; measures APK against prior release. No R8 change in this release. Other screens are not extracted yet; validate this smaller refactor before proceeding.
