# Agent notes

Durable learnings for working in this repo. Keep it short and free of secrets.

## Build and test

- Android app, package `com.cursorandroid.app`. Kotlin, Compose Material3, Gradle wrapper, JDK 21.
- Unit tests: `./gradlew :app:testDebugUnitTest`. A missing SDK fails this task. `local.properties` (`sdk.dir`) is gitignored.
- `RELEASE_NOTES.md` must start with `# <versionName>` from `app/build.gradle.kts`. `bash scripts/check-release-notes.sh` checks that.
- Debug APK: `./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`. Do not upload it to Play from here.
- Pushing a version bump to `main` publishes a GitHub release via `release-apk.yml`. Preview APKs build on `Backend/**`.

## Stream text (1.0.32)

Root cause: `assistant` and `thinking` SSE payloads are `{ text }`, and the client appended every payload. That field is not only a token delta.

Confirmed against the Cloud Agents API (`GET /v1/agents/{id}/runs/{runId}/stream`, docs: cursor.com/docs/cloud-agent/api/endpoints):

- `assistant` and `thinking` are `{ text }` deltas. The space after a period often arrives as the first character of the next delta. Do not trim it.
- The same `text` field is also, in practice, a cumulative snapshot of the open segment, or a revised snapshot that shares a prefix or a long tail with the text already shown. Appending a revision produced `whilekept` (`will be kept, while` + `kept as finished samples`).
- `tool_call` is `{ callId, name, status, args?, result? }`. `running` and `completed` are two events for one call. They close the assistant segment once. The next assistant text is a new paragraph (`\n\n`). Thinking is a side channel and must not close the assistant segment.
- `interaction_update` is the SDK-shaped stream (`text-delta`, `thinking-delta`, …). This app uses the simplified events and ignores `interaction_update`. Do not apply both or the text is doubled.
- `result.text` is the terminal reply for the run, one string. The byte-for-byte transcript is `GET /v0/agents/{id}/conversation` (`assistant_message.text`). There is no public v1 conversation route. Those rows are separate segments. Do not trim them.
- Event `id` lines are opaque. Resume with `Last-Event-ID`. The leading `status` event has no id. A full replay (`replay = true`, or a new run) must `RunStreamFold.reset()`. A resume keeps the fold and skips ids it has already applied. A delta replay without ids cannot be told from new tokens and will double-append.
- `410 stream_expired` means read the run, not loop the stream. `invalid_last_event_id` clears the cursor.

`RunStreamFold` / `integrateStreamText` in `StreamText.kt` is the only accumulator. `MessageText` shows that string. Do not trim chunks before render, and do not re-flow markdown by stripping whitespace on partial text. An unclosed code fence is still the raw accumulator bytes.

On a finished run (settle or refresh), `dropSettledAssistant` drops a streamed bubble when a conversation row is that same segment (exact cover, or a long shared prefix/tail such as `whilekept`). Keep the bubble when it still has a tail the server has not listed, and when the server window never listed that turn. Another run's reply must not delete it. `pickLine` keeps the longer assistant string; a garbled splice is often longer than the real sentence, so the conversation row has to win before coalesce.

## Do not regress

- Leading spaces on assistant and thinking deltas (`"Done."` + `" Next"`).
- A revised snapshot or a rewritten tail (`whilekept`) replaces the open segment. It is not appended.
- Thinking between tokens does not insert a paragraph. Tool calls do.
- Replayed event ids do not duplicate. Cumulative snapshots that already contain the bubble do not duplicate earlier segments.
- Settled chat text matches `assistant_message.text` bytes, including a leading space. A streamed tail the server has not listed yet is kept.
- Tests: `StreamTextTest`, `RunSettleThreadTest`, `RunSettleTranscriptTest`.
