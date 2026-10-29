# Grok chat — Cursor API research (Step 1)

Sources (public Cursor docs, Oct 2026):

- [Cloud Agents API overview](https://cursor.com/docs/cloud-agent/api/overview)
- [Cloud Agents API endpoints](https://cursor.com/docs/cloud-agent/api/endpoints)
- [Cloud Agents product overview](https://cursor.com/docs/cloud-agent)
- [Agent SDK — cloud agents guide](https://cursor.com/docs/agent-sdk/guides/cloud-agents) (confirms launches without `cloud.repos` when no repository is listed)

## Authentication

Same as the Android app today: user API key from Cursor Dashboard → API Keys, sent as `Authorization: Bearer …` or HTTP Basic (`-u KEY:`). No separate xAI API key is documented for Cloud Agents.

## Can we run lightweight Grok chat without a repo?

**Yes.** `POST /v1/agents` accepts a body with `prompt` only; omit both `repos` and `env` to start a **no-repo agent** ([Create An Agent](https://cursor.com/docs/cloud-agent/api/endpoints#create-an-agent)). The app already uses this pattern for profile-rule agents.

**Model:** `GET /v1/models` lists model ids (including Grok families when enabled for the account). Pass `model.id` and optional `model.params` on create and on follow-up runs.

**Follow-ups:** `POST /v1/agents/{id}/runs` (Create Run) continues the same durable agent and conversation ([Create Run / follow-up](https://cursor.com/docs/cloud-agent/api/endpoints)).

**Not a dedicated chat-completions API:** There is no documented Cursor endpoint for OpenAI-style `/v1/chat/completions`. Conversation is modeled as cloud agents + runs. Latency includes agent VM lifecycle (no-repo agents still run as cloud agents; they are not instant chat-completions).

## Streaming

**Yes.** `GET /v1/agents/{id}/runs/{runId}/stream` returns SSE with assistant text deltas (`assistant`, optional `interaction_update` with `text-delta`), tool events, `result`, and `done`. Retention header `X-Cursor-Stream-Retention-Seconds`; `410 stream_expired` when the stream window ends ([Stream a run](https://cursor.com/docs/cloud-agent/api/endpoints#stream-a-run)).

## Voice / audio

**No documented Cursor API** for Grok native voice, speech-to-text, or TTS. Voice in the app uses **Android on-device** `SpeechRecognizer` and `TextToSpeech`. Real Grok voice would require xAI or another provider outside the Cloud Agents API (out of scope unless Cursor documents it).

## Cost / usage

Per-agent token usage: `GET /v1/agents/{id}/usage` with per-run `inputTokens`, `outputTokens`, cache fields ([Agent usage](https://cursor.com/docs/cloud-agent/api/endpoints)). Billing is through the Cursor account (same as cloud agents in the IDE); no public per-message price list in the API docs. Each Grok chat turn consumes cloud-agent capacity like any other agent run.

## Rate limits

Documented per-route limits (examples: strict limits on some environment/build routes; `429 rate_limit_exceeded`). Full tables are in the [API overview](https://cursor.com/docs/cloud-agent/api/overview). Treat Grok chat as normal agent create + run traffic.

## Blockers / options

| Need | Status |
|------|--------|
| Grok via existing Cursor login | Supported via Cloud Agents API + `GET /v1/models` |
| Multi-turn on one session | Supported via same `agent` id + Create Run |
| Streaming UI | Supported via run SSE |
| Native Grok voice API | **Not in public Cursor API** — local STT/TTS only |
| Cheapest “instant chat” | **No** — agent runs, not chat-completions |
