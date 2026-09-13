# 1.0.20

Show your messages in every thread, including chats started on PC or Cloud. The app now reads run prompts from the API and will not drop older user bubbles when a long tool trace is saved.

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
