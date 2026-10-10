# Cursor Unofficial Android

A free Android client for [Cursor](https://cursor.com) Cloud Agents. I built it because Cursor does not ship an Android app, and I wanted one on my phone.

This is a personal project by **Foreverlegion**. I am a paying Cursor customer. That is the entire relationship.

## Not affiliated with Cursor

This app is **unofficial**. It is not made by, endorsed by, sponsored by, or connected to Cursor or Anysphere in any way.

I do not work for Cursor. I do not represent Cursor. I do not own Cursor, the Cloud Agents service, the models, or anything this app talks to. Those belong to their owners.

This client only sends commands you start, using **your** Cursor account and **your** API key, through Cursor's public APIs. If Cursor changes those APIs, this app can break. That is not their problem and not a support channel into Cursor.

Cursor, the Cursor logo, and related marks are theirs. I use the name here only so people can find a phone client for a product that does not have one.

## What it is

A sideloaded APK that lets you run and follow Cloud Agent work from Android:

- Inbox for cloud, pool, and remote/machine chats
- Start an agent on a repo, a saved cloud environment, a machine, or a pool
- Follow-ups, artifacts, and run status
- Notifications when a run finishes or needs approval, including per-chat mute
- MCP servers you configure on the phone (HTTP, SSE or stdio for cloud VMs), with import from `mcp.json`
- Create a GitHub repo from New agent if you add a GitHub token
- Settings backup

The phone does **not** run the agent. The work happens on Cursor's cloud VMs or on a machine you already signed into. This app is a remote control and inbox.

## What it is not

- Not the official Cursor iOS app, desktop app, or CLI
- Not a Play Store product
- Not a local coding environment
- Not a way to skip a Cursor subscription
- Not a claim on Cursor's product, brand, or backend

## Cost

**Free.** No paid app, no ads, no in-app store. You still need your own Cursor plan for agents to actually run. I do not charge for this client and I do not sell access to Cursor.

## Install

1. Open the latest [GitHub Release](https://github.com/Foreverlegion/Cursor-Unofficial-Android/releases).
2. Download `app-release.apk`.
3. Allow installs from this source if Android asks.
4. Install the APK.

Play Protect may scan a sideloaded APK. That is Google, not this app. You can install anyway or turn Play Protect off yourself.

Play installs update from the Play Store. This app does not download or install APKs.

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

## Sign in

You need a Cursor user API key from [cursor.com/dashboard/api](https://cursor.com/dashboard/api).

The app can also walk a browser sign-in. The first time, it creates one API key and saves it on the phone. Later sign-ins reuse that key.

Play review uses a local demo sign-in (`demo` / `demo`) with sample chats and no Cursor API. Play testing is an open join, not an email list. Instructions: [docs/play-review.md](docs/play-review.md).

Minimum Android: 8.0 (API 26).

## Notifications

The app can alert when a run finishes or when something needs approval on your PC. You can mute one chat from the inbox row or the thread menu without turning all notifications off.

For alerts to arrive with the screen off, the first-launch battery prompt asks Android to leave this app alone. That is optional, but Android will otherwise freeze background checks.

## Status

Built and maintained in my spare time. Features track what the public Cloud Agents API allows. Things Cursor only exposes in the official web/desktop clients (for example in-app remote desktop control) are not available here unless that API exists.

Issues and APKs live in this repository: [Foreverlegion/Cursor-Unofficial-Android](https://github.com/Foreverlegion/Cursor-Unofficial-Android).

If you work at Cursor and want this taken down or renamed, open an issue. I will handle it. I am not trying to pass this off as yours.

I have asked the moderators on the Cursor forum to review this repository. If someone at Cursor or Anysphere wants what I have built, I will transfer ownership of the repo to a developer there. No payment, no conditions. I made this so Cloud Agents could live on a phone. If the people who make Cursor want to take it from here, it is theirs.
