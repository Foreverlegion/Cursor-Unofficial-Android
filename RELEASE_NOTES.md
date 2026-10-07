# 1.0.25

First browser sign-in saves one API key named cursor-android. Later sign-ins reuse it.

Settings has Report bug and Request feature. Replies show up in the app. Ban still turns that install off.

Play review can sign in with username demo and password demo. That mode shows two sample chats and a Demo banner. It does not call Cursor.

Play installs update from the Play Store. This build does not check GitHub for a newer APK.

Play testing is open. Testers join from the Play testing link. There is no email list.

---

# 1.0.24

Inbox and agent threads follow the Play listing layout. Status pills are Done, Running, Needs approval, and Failed, from real agent and run states. The inbox bar and Settings sit above the Android system navigation bar.

New Agent can start on a saved cloud environment and can show that environment's build status. An any-repo pool can take more than one repository. Machine, the default pool, and repo-backed pools still take one.

Install counting is gone. This build does not ping an install ledger.

Privacy policy: https://foreverlegion.github.io/Cursor-Unofficial-Android/privacy.html

---

# 1.0.23

Thinking and typing sit at the bottom of the thread while a run is live. Finished Thinking rows no longer stick in the scroll backlog between older messages.

Pull-to-refresh and background inbox sync no longer treat IDLE chats as live runs. That was watching every idle chat, firing a pile of finish notifications, and blocking quiet list updates. Refresh now merges the first page immediately, pages older chats in place, and only watches runs that are actually working.

Hidden, Archived, and Working stay on screen when they empty the inbox, so you can turn them off. Turning on Hidden with no hidden chats used to hide the filter and every chat until you reinstalled.

Conversation merge keeps remote message ids and does not collapse two different runs that share the same reply text.

---

# 1.0.22

New agent on a Machine now sends a repository with the worker name. The public Cloud Agents API rejects a repo-less private-worker start. The form pre-fills the worker's registered repo when it has one, and you can pick or paste any HTTPS git URL the API accepts (GitHub, GitLab, Bitbucket, Azure DevOps, Origin, or another connected source). It does not silently pick your first GitHub repo. Commit-on-branch defaults on for machines. Start stays disabled until both repo and branch are set.

Thinking from an older turn no longer jumps under a later message, which was mixing chats.

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
