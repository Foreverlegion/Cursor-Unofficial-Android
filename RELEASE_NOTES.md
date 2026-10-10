# 1.0.27

Owner tools in Settings match the public GitHub login Foreverlegion. The in-app Ban button is shown only for that login. The privacy policy contact is that GitHub profile and this project's issues.

Chats started in the app show the model on each agent message. Cursor's API does not report which model a run used, so chats started on another device show plain "Agent". To get a label there, start the prompt with a first line like `Model: Opus 5.5`. The line stays in the message. Ids such as `claude-opus-5-5` show as Opus 5.5, and other text is shown as written.

MCP: a follow-up no longer replaces the servers an agent was created with. The app now omits the list on follow-ups so the agent keeps its set. HTTP servers take an OAuth client (client ID, optional secret, scopes), and the type SSE is available with a note that Cursor does not support it for cloud agents. Settings, Connections, MCP has presets, import from a pasted or chosen `mcp.json` (preview, pick servers, ask before replacing), and export to `mcp.json`.

Settings export leaves out the API key, forge tokens, and MCP header, env, and OAuth values unless you turn on Include secrets, which seals them with a passphrase you choose. Import asks for that passphrase. If the phone's secure storage cannot be opened, secrets stay in memory only and a warning shows. The old plain `mcp_name` and `mcp_url` copy is moved into the encrypted list and deleted. Saved settings are kept across the update.

New agent: the Source list now includes every forge saved in Settings, Connections, Forges (GitHub, GitLab, Bitbucket, Azure DevOps, Origin, self-hosted and custom HTTPS hosts), for Cloud, Machine, and Pool. Picking GitLab lists your projects with the saved GitLab token (membership projects, searchable, load more) and sends the HTTPS clone URL as the repo. Saved forges and tokens are only read, never changed.

Machines: long-press a machine in New agent, Machine, Remote or in the Remote list (or tap its menu) to Hide it or Delete it. Settings, Connections, Machines lists every machine and worker this phone has seen, whether it is online, when it was last seen, and has Hide, Unhide, Delete, Restore, and an option to auto-hide offline machines not seen for 7 to 90 days. Cursor's API can deregister a pool but not a single worker, so Delete forgets the machine on this phone only; it shows again if it comes back under a new id. Hidden state is kept across restarts and updates. Machines are no longer dropped when a worker disconnects, the worker list returns empty, or a restart gives the worker a new id: every machine seen stays listed as offline until you hide it, and the list cached by earlier builds is merged in on update. The worker list now reads every page instead of the first 50.

---

# 1.0.26

Play upload no longer declares a permission to install other apps. The in-app APK installer is gone. Play installs update from the Play Store.

Unrestricted battery stays so finish and approval notifications are not frozen in the background.

---

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
