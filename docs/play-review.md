# Play review sign-in

Paste into Play Console → App content → App access, as the instructions for a restricted app.

This is a local review sign-in. It is not a Cursor account and it does not call the Cursor API.

```
Sign-in is required.

On the sign-in screen, use Review / Demo. Do not use Sign in with Cursor.

Username: demo
Password: demo

Tap Enter demo.

The app opens with sample chats only. A banner at the top says "Demo — not connected to Cursor". Nothing in this mode contacts Cursor. No API key is created.

To leave, open Settings → Account → Sign out.
```

# Play testing track

Closed testing. Testers join the Google Group, then opt in on Play with the same Google account.

- Group: https://groups.google.com/g/unofficial-cursor-android-testers (`unofficial-cursor-android-testers`)
- Opt-in: https://play.google.com/apps/testing/com.cursorandroid.app

The closed-test build on Play is 1.0.28 (versionCode 128). Upload the signed 1.0.28 (128) app bundle to that closed track.

Reviewers use the demo sign-in above. Testers sign in with their own Cursor API key from cursor.com/dashboard/api.
