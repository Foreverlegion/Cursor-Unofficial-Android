# Agent notes

Durable learnings for working in this repo. Keep it short and free of secrets.

## Build and test
- Android app, package `com.cursorandroid.app`; Kotlin, Compose Material3, Gradle wrapper, JDK 21. Use `--offline` when the cache is warm.
- Unit and Robolectric tests: `./gradlew :app:testDebugUnitTest`. Results land in `app/build/test-results/testDebugUnitTest/*.xml`; `-q` prints nothing on success, so count the XML to confirm.
- Release build: `./gradlew :app:assembleRelease` (R8 full mode, unsigned locally). Then run `bash scripts/check-r8-reflective-ctors.sh` and `bash scripts/check-release-notes.sh`.
- `RELEASE_NOTES.md` must start with `# <versionName>` of `app/build.gradle.kts`.

## Branches, PRs and releases
- Work on branches named `Backend/<name>-ad37`. Push to `Backend/**` runs `preview-apk.yml`, which uploads the signed `cursor-android-preview` APK plus the AAB and mapping artifacts.
- `release-apk.yml` runs on push to `main` when `app/build.gradle.kts`, `RELEASE_NOTES.md` or the workflow changes, and publishes the GitHub release and tag. Do not merge to main, bump the version or touch those files unless a release is intended. The Play Console is handled by the owner.
- Open PRs as drafts with `ManagePullRequest`. Use conventional commits. Keep unrelated work on separate PRs.

## Porting work between branches
- Cherry-pick one commit per command and check `git status` after each; a loop keeps going after a conflict.
- Where branches overlap, current main's behaviour wins. Verify main's newer mechanisms survive: `InboxRefresh`/`InboxPollPolicy`, `RunSettleHub`, `RunWatchScheduler`, machine memory, `RunStopper`, `dropDeliveredQueue`.
- Ported commits can depend on each other (for example the cache worker); keep every commit compiling, and use `./gradlew :app:compileDebugKotlin --offline -q` after each conflict.
- `ConversationStore.pending` holds `Versioned` entries; code that edits a pending snap must go through `.snap`.

## Product constraints
- Settings export is sealed (encrypted); do not add copy that says it is plaintext.
- Forge tokens live in the encrypted forge store.
- The Bearer API key is sent only over https to `api.cursor.com`.
- No tracking or analytics; release-note text stays plain language.

## Notes on this file
- The continual-learning skill was not available in the cloud agent that wrote this, so these notes were written by hand from the session.
