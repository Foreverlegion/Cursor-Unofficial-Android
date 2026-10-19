# Cursor Unofficial Android

A free Android client for [Cursor](https://cursor.com) Cloud Agents. I built it because Cursor does not ship an Android app, and I wanted one on my phone.

This is a personal project by **Foreverlegion**. I am a paying Cursor customer. That is the entire relationship.

## Not affiliated with Cursor

This app is **unofficial**. It is not made by, endorsed by, sponsored by, or connected to Cursor or Anysphere in any way.

I do not work for Cursor. I do not represent Cursor. I do not own Cursor, the Cloud Agents service, the models, or anything this app talks to. Those belong to their owners.

This client only sends commands you start, using **your** Cursor account and **your** API key, through Cursor's public APIs. If Cursor changes those APIs, this app can break. That is not their problem and not a support channel into Cursor.

Cursor, the Cursor logo, and related marks are theirs. I use the name here only so people can find a phone client for a product that does not have one.

## What it is

An Android client that lets you run and follow Cloud Agent work from your phone:

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
- Not a public Play Store release. Closed testing is how you install it (see [Install](#install))
- Not a local coding environment
- Not a way to skip a Cursor subscription
- Not a claim on Cursor's product, brand, or backend

## Cost

**Free.** No paid app, no ads, no in-app store. You still need your own Cursor plan for agents to actually run. I do not charge for this client and I do not sell access to Cursor.

## Install

Install from Google Play closed testing. Sideloading an APK is an alternate if you cannot use Play.

Minimum Android: 8.0 (API 26). Package: `com.cursorandroid.app`.

The closed-test build on Play is 1.0.28 (versionCode 128).

### Google Play (closed testing)

1. **Join the Google Group.** Open [unofficial-cursor-android-testers](https://groups.google.com/g/unofficial-cursor-android-testers). Sign in with the Google account on your phone. Choose **Join group** or **Ask to join**.
2. **Opt in on the phone.** On the phone, signed into that same Google account, open the [closed-test opt-in](https://play.google.com/apps/testing/com.cursorandroid.app). Tap **Become a tester**. Tap **Accept** if Play asks.
3. **Wait for Play to refresh.** The listing can take minutes, and sometimes hours, before it shows up. If the page says you are not eligible, confirm the group membership uses the same Google account, wait, and refresh the page.
4. **Install or update from the Play Store.** From the opt-in page, open the store listing, or find the app in the Play Store once it appears. Tap **Install**. If a copy is already on the phone, tap **Update**. Play installs update from the Play Store. This app does not download or install APKs.
5. **Sign in with your Cursor API key.** After install, use a user API key from [cursor.com/dashboard/api](https://cursor.com/dashboard/api). See [Sign in](#sign-in). The `demo` / `demo` sign-in is only for Play reviewers ([docs/play-review.md](docs/play-review.md)).
6. **Battery (optional).** The first launch can ask you to leave battery use unrestricted. Allow it if you want finish and approval notifications while the screen is off. You can skip it. Details are under [Notifications](#notifications).
7. **Stay installed.** If you are helping the closed test toward a production listing, leave the app installed for the closed-testing period.

### Sideload from GitHub Releases (alternate)

Use this only if you cannot use Play.

1. Open the latest [GitHub Release](https://github.com/Foreverlegion/Cursor-Unofficial-Android/releases).
2. Download `app-release.apk`.
3. Allow installs from this source if Android asks.
4. Install the APK.

Play Protect may scan a sideloaded APK. That is Google, not this app. You can install anyway or turn Play Protect off yourself.

## Sign in

You need a Cursor user API key from [cursor.com/dashboard/api](https://cursor.com/dashboard/api).

The app can also walk a browser sign-in. The first time, it creates one API key and saves it on the phone. Later sign-ins reuse that key.

Play reviewers use a local demo sign-in (`demo` / `demo`) with sample chats and no Cursor API. Testers use their own API key. Review instructions: [docs/play-review.md](docs/play-review.md).

## Notifications

The app can alert when a run finishes or when something needs approval on your PC. You can mute one chat from the inbox row or the thread menu without turning all notifications off.

For alerts to arrive with the screen off, the first-launch battery prompt asks Android to leave this app alone. That is optional, but Android will otherwise freeze background checks.

## Status

Built and maintained in my spare time. Features track what the public Cloud Agents API allows. Things Cursor only exposes in the official web/desktop clients (for example in-app remote desktop control) are not available here unless that API exists.

Closed testing is on Google Play. See [Install](#install). Issues and release APKs still live in this repository: [Foreverlegion/Cursor-Unofficial-Android](https://github.com/Foreverlegion/Cursor-Unofficial-Android).

If you work at Cursor and want this taken down or renamed, open an issue. I will handle it. I am not trying to pass this off as yours.

I have asked the moderators on the Cursor forum to review this repository. If someone at Cursor or Anysphere wants what I have built, I will transfer ownership of the repo to a developer there. No payment, no conditions. I made this so Cloud Agents could live on a phone. If the people who make Cursor want to take it from here, it is theirs.
