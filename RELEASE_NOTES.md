# 1.0.22

New agent on a Machine now sends a repository with the worker name. The public Cloud Agents API rejects a repo-less private-worker start. The form pre-fills the worker's registered repo when it has one, and you can pick another checkout the PC already has. Commit-on-branch defaults on for machines.

---

# 1.0.21

Play Protect skip no longer drops the APK URI, so Update can finish after you skip the scan. Autoscroll follows thinking even when a message is queued underneath. Send during a live run tries steer first and does not cancel the current turn; if the API will not take it, the message stays queued.

This build also includes the conversation history load that did not make the immutable 1.0.20 APK.

---

# 1.0.20

Show your messages in every thread, including chats started on PC or Cloud. The app now loads the agent conversation (`GET /v0/agents/{id}/conversation`) so user prompts come back from the server, not only from this phone. Long tool traces no longer clip those bubbles away.

In-app Update only offers a published APK. It no longer labels main's next version as ready and then installs the previous build, and it opens the system installer directly so the prompt is not lost.

---

# 1.0.19

Keep user messages that share the same text. Image follow-ups all use "See attached." and short repeats like "ok" were being collapsed into the first send, so later turns vanished after refresh.

---

# 1.0.18

New Agent now sends the Cloud Agents create fields the public API already supports.

- Model variants and params (fast, effort, and whatever GET /v1/models returns)
- More than one custom subagent, each with inherit or a specific model
- Commit on the starting branch, attach an existing PR URL, and skip adding you as reviewer
- Pool picker from List Pools, with pool counts on Settings
- Follow-up model params on a thread
