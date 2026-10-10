# 1.0.29

- MCP: Create MCP sheet for import, next to Import in Settings, Connections, MCP, saves a template `mcp-template.json` to your Downloads folder and offers to open it. The template has an HTTP, an SSE and a stdio example with placeholder values, all switched off, so importing it as it is changes nothing. A second one is saved as `mcp-template (1).json`, never over the first.
- MCP import reads `"enabled": false` as well as `"disabled": true`.

## Importing MCP servers

Steps:

1. Get an `mcp.json` onto the phone, or have its text ready to paste. It uses the same format as `~/.cursor/mcp.json` and `.cursor/mcp.json` on a PC. To start from a template, open Settings, Connections, MCP and tap Create MCP sheet for import. That saves `mcp-template.json` to your Downloads folder, and the message at the bottom has an Open button.
2. In Settings, Connections, MCP, tap Import, then From mcp.json.
3. Tap Choose file (or paste the text), then Preview. Tick the servers you want and tap Import.

The format:

```json
{
  "mcpServers": {
    "example-http": {
      "url": "https://example.com/mcp",
      "headers": { "Authorization": "Bearer YOUR_TOKEN_HERE" },
      "enabled": false
    },
    "example-sse": {
      "type": "sse",
      "url": "https://example.com/sse",
      "enabled": false
    },
    "example-stdio": {
      "command": "npx",
      "args": ["-y", "@example/mcp-server"],
      "env": { "API_KEY": "YOUR_TOKEN_HERE" },
      "enabled": false
    }
  }
}
```

Fields:

- `mcpServers` (required): a map from server name to its settings. The name is the key. It must not be empty, and names are matched case-insensitively. A top-level `servers` map, or a bare map of servers, is accepted too.
- HTTP server: `url` is required and must start with `https://`. `headers` is optional. `type` is optional (`"http"` is assumed when there is a `url`).
- SSE server: `type` must be `"sse"`, and `url` is required. Cursor says SSE is not supported for cloud agents, so the importer flags it.
- Stdio server: `command` is required. `args` (a list of strings) and `env` (a map of strings) are optional. It runs inside the cloud VM. `type` is optional (`"stdio"` is assumed when there is a `command` and no `url`).
- `enabled`: optional. `false` imports the server switched off. Without it the server is on, and on for every new agent. `"disabled": true` means the same and is the only form version 1.0.28 reads.
- `auth`: optional OAuth client for HTTP and SSE servers: `CLIENT_ID` (required), `CLIENT_SECRET`, `scopes`.
- Header, env and `args` values are used exactly as written. `${VAR}` placeholders are not expanded.

Secrets and headers:

- Header values, env values and OAuth client secrets are stored encrypted on the phone. A header whose value is only `Bearer` (no token) is never sent.
- Enabled servers, with their headers and env, are sent to Cursor when you start a new agent, because that is how Cursor's API takes MCP servers. Nothing else leaves the phone, and there is no tracking.
- If a name is already saved, the importer leaves it unchecked and asks before replacing it. A replaced server keeps its saved header, env and OAuth values wherever the file leaves them empty.
- Export writes the same format with header and env values and client secrets left empty. Turn on Include secrets only for a file you will keep private, because it is plain text.
- OAuth-only servers (for example GitLab, Slack, Sentry) are connected at cursor.com/agents, MCP Servers. This app cannot finish that sign-in.
- A phone holds at most 50 servers.
- The template's three servers are placeholders and are all switched off. Importing it as it is saves three disabled entries and nothing is sent to an agent. Replace the `example.com` URLs and `YOUR_TOKEN_HERE` values, and remove `"enabled": false`, to use one.

---

# 1.0.28

- The app download is much smaller. Release builds are shrunk and optimized.
- Cursor agent links (cursor.com/agents/...) open in the app. Other cursor.com links stay in your browser.
- Fixed a blank screen after signing in with the browser.
- Agent list: grouped by repo, compact cards, filter chips, swipe to archive with Undo, pin a chat. Long-press a repo group to rename it, favorite it, color it, or move it.
- Settings is now a list of pages instead of tabs. New pages: Appearance (accent color, fonts, text size, chat density), Agent list, Repo defaults, Forges, Machines.
- Repo defaults: set a model, branch, MCP servers, environment, and a prompt prefix per repo, and New agent fills them in.
- Forges: save tokens for GitHub, GitLab, Bitbucket, Azure DevOps, Gitea, Origin and custom hosts. Every saved forge is a Source in New agent, and GitLab projects are listed with your token.
- Machines: long-press a machine to hide or delete it, with a Machines page and auto-hide for machines not seen for 7 to 90 days. Machines you have seen stay listed as offline until you hide them. Cursor's API cannot remove a worker, so Delete forgets it on this phone only.
- MCP: follow-ups keep the servers the agent was created with. OAuth client, SSE type, presets, and mcp.json import and export.
- Settings export leaves out secrets unless you seal them with a passphrase. Saved settings and tokens are kept across the update.
- Agent messages show the model that produced the run, including a `Model:` first line from other clients.
- Threads: one status for the header, work bar and list card, a Stop button on the working bar, and Kill process now works when the run id was missing.
- Fixed old messages showing as Queued, old messages jumping to the bottom of a thread, and the chat list flashing while a run streams.
- Fixed the crash when opening Settings, Forges.
- Artifacts show their date and time, and long file names are no longer cut off.
- Messages show code blocks in a scrollable box using the code font you pick in Appearance.
- Settings, About shows saved numbers at once and fills each row as it loads. Usage covers chats active in the last 30 days.
- The battery and first-run notice screens are readable after signing in with the browser.
- Fixed Running staying on a finished agent in the list and thread.
- Faster polling right after a send and steadier background run watching.
- The Inbox now shows an agent as Running when a run starts on another device, without pulling to refresh. It checks every few seconds while something runs and less often when all is idle, refreshes at once when you return to the app or a run notification arrives, and stops in the background.
- Notifications moved off the Inbox page into a popup behind a bell next to New. The bell shows an unread count, opening it marks them read, and each item can be opened or dismissed, with Clear all. Read and dismissed state is kept across restarts and updates.

---

# 1.0.27

Owner tools in Settings match the public GitHub login Foreverlegion. The in-app Ban button is shown only for that login. The privacy policy contact is that GitHub profile and this project's issues.

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
