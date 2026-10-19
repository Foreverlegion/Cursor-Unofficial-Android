package com.cursorandroid.app.data.repo

import com.cursorandroid.app.data.api.CreateAgentRequest
import com.cursorandroid.app.data.api.Prompt

/**
 * One-time no-repo agent that asks Cursor to save an Always Apply user rule.
 * [message] is the only prompt text: it is sent once, and the chat shows that same line.
 * repos and env are omitted.
 */
object ProfileRule {
    const val TITLE = "Build profile rule to give all Cursor agents context"
    const val AGENT_NAME = "Cursor Unofficial Android"

    /** Title plus the setup body. One launch sends this once. */
    fun message(): String = "$TITLE\n\n$TEXT"

    val TEXT: String = """
You are a one-time setup agent. Do not write app code and do not open a PR on any real project. This agent was started with no repository (both `repos` and `env` omitted).

Your only job: create or update a Cursor User Rule (Always Apply) titled something like **Cursor Unofficial Android** that teaches every future cloud, machine, and pool agent how to interact when the user chats from the **Cursor Unofficial Android** phone app (`com.cursorandroid.app`). After the rule is saved, reply with the exact rule text and confirm Always Apply is on. Then stop.

### What this app is
An unofficial Android remote control and inbox for Cursor Cloud Agents (and machines/pools). The phone does not run the agent. Work runs on Cursor cloud VMs or the user's machines. Assume the user is often on the phone, not desktop.

### Offering artifacts (critical)
When you produce a file the user should open or keep on the phone:
1. Save it under workspace artifacts (paths from List Artifacts, e.g. `artifacts/report.pdf`).
2. In your final reply, name the path and say they can tap Open or Save on the phone.
3. Prefer artifacts over dumping huge content only in chat text. Do not rely on desktop-only paths.

Phone: latest artifact card (Open + Save), short Artifacts history, Open = signed HTTPS URL (~15 min), Save = Downloads via Download Manager.

### Receiving images
User can attach photos/images. They go as prompt images (vision): either base64 `data` + `mimeType`, or an `http(s)` `url` the API fetches. Limits: up to 5 images, 15 MB each, `image/png`, `image/jpeg`, `image/gif`, `image/webp` (HEIC may be converted on the phone). Say what you see when it matters.

### Receiving text in the prompt
Text the user pastes or that the app inlines into `prompt.text` is part of the instruction. Read it from the prompt. There is no separate arbitrary binary-file upload on the Cloud Agents API — only `prompt.text` and `prompt.images`.

### Sending pictures / video back
Prefer artifacts (`artifacts/demo.mp4`, `artifacts/ui.png`) so Open/Save work. Also describe what they show. No desktop preview assumption.

### Chat / UX
- Follow-ups from the phone queue until the current run finishes, then become the next run (`POST …/runs`). Do not assume mid-run steer (not in the public API).
- The user can tap **Send immediately** on a queued message: that cancels the active run (`POST …/cancel`), then sends that follow-up as a new run. Treat cancel-then-new-run as intentional.
- Keep replies phone-friendly. Write a real user-visible answer even though thinking/tool UI exists on the phone.
- If you open a PR, include the https PR URL in git/result metadata (phone can offer Open PR and can filter agents by `prUrl`).
- MCP may be attached from the phone at agent create; do not assume desktop MCP only.
- New agent can target a repo, saved environment, machine, pool, or **no repo** — user picks on the phone.

### Scope
Always Apply for this account. Only Android-app interaction (artifacts, images, prompt text, phone chat UX). Prefer account User Rules over a repo `.mdc`.
    """.trim()

    fun request(): CreateAgentRequest {
        return CreateAgentRequest(
            prompt = Prompt(text = message()),
            name = AGENT_NAME,
        )
    }
}
