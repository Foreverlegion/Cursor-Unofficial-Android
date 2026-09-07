package com.cursorandroid.app.data.repo

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.security.MessageDigest
import java.util.UUID

object InstallPulse {
    const val WINDOW_DAYS = 14L
    const val PREFS = "cursor_install"
    const val MAX_IDS = 5_000
    const val PING_EVERY_MS = 20L * 60 * 60 * 1000
    const val OWNER_EMAIL = "foreverlegion@gmail.com"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    data class Sighting(
        val first: Long,
        val last: Long,
        val left: Boolean = false,
    )

    data class Ledger(
        val windowDays: Long = WINDOW_DAYS,
        val ids: Map<String, Sighting> = emptyMap(),
    )

    data class Counts(
        val current: Int,
        val total: Int,
    )

    fun isOwner(email: String?): Boolean {
        return email?.trim()?.lowercase() == OWNER_EMAIL
    }

    fun hashId(id: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(id.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    fun pingDue(lastAtMs: Long, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (lastAtMs <= 0L) return true
        return nowMs - lastAtMs >= PING_EVERY_MS
    }

    fun extractJson(body: String): String? {
        val fence = FENCE.find(body)?.groupValues?.get(1)?.trim()
        if (!fence.isNullOrEmpty()) return fence
        val trimmed = body.trim()
        return trimmed.takeIf { it.startsWith("{") && it.endsWith("}") }
    }

    fun parseLedger(body: String): Ledger {
        val raw = extractJson(body) ?: return Ledger()
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: return Ledger()
        val window = root["windowDays"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0 } ?: WINDOW_DAYS
        val ids = parseIds(root["ids"] as? JsonObject ?: JsonObject(emptyMap()))
        return Ledger(window, ids)
    }

    fun renderLedger(ledger: Ledger, nowSec: Long): String {
        val counted = counts(ledger, nowSec)
        return json.encodeToString(
            LedgerDto(
                windowDays = ledger.windowDays.takeIf { it > 0 } ?: WINDOW_DAYS,
                current = counted.current,
                total = counted.total,
                ids = idsJson(ledger.ids),
            ),
        )
    }

    fun apply(ledger: Ledger, id: String, nowSec: Long, leave: Boolean): Ledger {
        if (!HASH.matches(id)) return ledger
        val next = ledger.ids.toMutableMap()
        val existing = next[id]
        if (leave) {
            if (existing != null) next[id] = existing.copy(left = true)
        } else {
            next[id] = Sighting(
                first = existing?.first?.takeIf { it > 0 } ?: nowSec,
                last = nowSec,
                left = false,
            )
        }
        return Ledger(ledger.windowDays.takeIf { it > 0 } ?: WINDOW_DAYS, cap(next))
    }

    fun counts(ledger: Ledger, nowSec: Long): Counts {
        val days = ledger.windowDays.takeIf { it > 0 } ?: WINDOW_DAYS
        val cutoff = nowSec - days * 86_400L
        val current = ledger.ids.count { (_, seen) -> !seen.left && seen.last >= cutoff }
        return Counts(current = current, total = ledger.ids.size)
    }

    fun installId(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(ID, null)?.trim().orEmpty()
        if (existing.isNotEmpty()) return existing
        val created = UUID.randomUUID().toString()
        prefs.edit { putString(ID, created) }
        return created
    }

    fun lastPingAt(context: Context): Long {
        return context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(LAST_PING, 0L)
    }

    fun markPinged(context: Context, atMs: Long = System.currentTimeMillis()) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { putLong(LAST_PING, atMs) }
    }

    fun fetchCounts(): Counts {
        val token = GithubAppIssues.bakedToken() ?: error("Missing GitHub token")
        val nowSec = System.currentTimeMillis() / 1000L
        return counts(loadLedger(token).second, nowSec)
    }

    fun ping(context: Context, leave: Boolean = false) {
        val token = GithubAppIssues.bakedToken() ?: return
        val hash = hashId(installId(context))
        val nowMs = System.currentTimeMillis()
        if (!leave && !pingDue(lastPingAt(context), nowMs)) return
        val (gistId, current) = loadLedger(token)
        val nowSec = nowMs / 1000L
        val next = apply(current, hash, nowSec, leave)
        val body = renderLedger(next, nowSec)
        if (gistId.isNullOrBlank()) {
            GithubAppIssues.createLedgerGist(token, body)
        } else if (next != current) {
            GithubAppIssues.updateLedgerGist(token, gistId, body)
        }
        if (!leave) markPinged(context, nowMs)
    }

    private fun loadLedger(token: String): Pair<String?, Ledger> {
        val known = runCatching {
            GithubAppIssues.getGist(token, GithubAppIssues.LEDGER_GIST_ID)
        }.getOrNull()
        if (known != null && !known.id.isNullOrBlank()) {
            return known.id to parseLedger(GithubAppIssues.gistLedgerContent(known))
        }
        val listed = GithubAppIssues.findLedgerGist(token) ?: return null to Ledger()
        val id = listed.id ?: return null to Ledger()
        val gist = GithubAppIssues.getGist(token, id)
        return id to parseLedger(GithubAppIssues.gistLedgerContent(gist))
    }

    private fun parseIds(raw: JsonObject): Map<String, Sighting> {
        val out = linkedMapOf<String, Sighting>()
        raw.forEach { (key, value) ->
            if (!HASH.matches(key)) return@forEach
            val primitive = runCatching { value.jsonPrimitive }.getOrNull()
            if (primitive != null) {
                val at = primitive.longOrNull ?: return@forEach
                out[key] = Sighting(first = at, last = at)
                return@forEach
            }
            val obj = runCatching { value.jsonObject }.getOrNull() ?: return@forEach
            val last = obj["last"]?.jsonPrimitive?.longOrNull
                ?: obj["first"]?.jsonPrimitive?.longOrNull
                ?: return@forEach
            val first = obj["first"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0 } ?: last
            val left = obj["left"]?.jsonPrimitive?.booleanOrNull ?: false
            out[key] = Sighting(first = first, last = last, left = left)
        }
        return out
    }

    private fun idsJson(ids: Map<String, Sighting>): JsonObject {
        return buildJsonObject {
            ids.filterKeys { HASH.matches(it) }.forEach { (key, seen) ->
                putJsonObject(key) {
                    put("first", seen.first)
                    put("last", seen.last)
                    if (seen.left) put("left", true)
                }
            }
        }
    }

    private fun cap(ids: Map<String, Sighting>): Map<String, Sighting> {
        if (ids.size <= MAX_IDS) return ids
        return ids.entries
            .sortedWith(compareBy<Map.Entry<String, Sighting>> { it.value.left }.thenBy { it.value.first })
            .takeLast(MAX_IDS)
            .associate { it.key to it.value }
    }

    @Serializable
    internal data class LedgerDto(
        val windowDays: Long = WINDOW_DAYS,
        val current: Int = 0,
        val total: Int = 0,
        val ids: JsonObject = JsonObject(emptyMap()),
    )

    internal val HASH = Regex("^[a-f0-9]{64}$")
    private val FENCE = Regex("```json\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
    private const val ID = "install_id"
    private const val LAST_PING = "last_ping_ms"
}
