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
- MCP servers you configure on the phone (HTTP or stdio for cloud VMs)
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
