package com.cursorandroid.app.data.repo

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** The secret half of a settings export, encrypted as one blob so the rest of the file stays readable. */
@Serializable
data class SealedSecrets(
    val format: String = SecretsVault.FORMAT,
    val version: Int = 1,
    val kdf: String = SecretsVault.KDF,
    val iterations: Int = SecretsVault.ITERATIONS,
    val salt: String = "",
    val iv: String = "",
    val data: String = "",
)

object SecretsVault {
    const val FORMAT = "cursor-android-secrets"
    const val KDF = "PBKDF2WithHmacSHA256"
    const val ITERATIONS = 600_000
    const val MIN_PASSPHRASE = 8

    private const val MIN_ITERATIONS = 100_000
    private const val MAX_ITERATIONS = 2_000_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128

    fun seal(
        plain: ByteArray,
        passphrase: CharArray,
        iterations: Int = ITERATIONS,
        random: SecureRandom = SecureRandom(),
    ): SealedSecrets {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val head = SealedSecrets(iterations = iterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(passphrase, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(aad(head))
        return head.copy(
            salt = Base64.getEncoder().encodeToString(salt),
            iv = Base64.getEncoder().encodeToString(iv),
            data = Base64.getEncoder().encodeToString(cipher.doFinal(plain)),
        )
    }

    /** Null for a wrong passphrase, a damaged file, or a header this build does not understand. */
    fun open(sealed: SealedSecrets, passphrase: CharArray): ByteArray? {
        if (sealed.format != FORMAT || sealed.version != 1 || sealed.kdf != KDF) return null
        if (sealed.iterations !in MIN_ITERATIONS..MAX_ITERATIONS) return null
        return try {
            val salt = Base64.getDecoder().decode(sealed.salt)
            val iv = Base64.getDecoder().decode(sealed.iv)
            val data = Base64.getDecoder().decode(sealed.data)
            if (iv.size != IV_BYTES || salt.isEmpty()) return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(passphrase, salt, sealed.iterations), GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(aad(sealed))
            cipher.doFinal(data)
        } catch (_: GeneralSecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun aad(head: SealedSecrets): ByteArray =
        "${head.format}:${head.version}:${head.kdf}:${head.iterations}".toByteArray(Charsets.UTF_8)

    private fun key(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BITS)
        try {
            val raw = SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded
            return SecretKeySpec(raw, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}

@Serializable
data class McpSecret(
    val headers: Map<String, String> = emptyMap(),
    val env: Map<String, String> = emptyMap(),
    val clientSecret: String = "",
)

@Serializable
data class BackupSecrets(
    val apiKey: String? = null,
    val githubToken: String? = null,
    val forgeTokens: Map<String, String> = emptyMap(),
    val mcp: Map<String, McpSecret> = emptyMap(),
)

private val SECRETS_JSON = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun SettingsSnapshot.secrets(): BackupSecrets = BackupSecrets(
    apiKey = apiKey?.takeIf { it.isNotBlank() },
    githubToken = githubToken?.takeIf { it.isNotBlank() },
    forgeTokens = forges.filter { it.token.isNotBlank() }.associate { it.id to it.token },
    mcp = mcpServers.filter { it.hasSecrets() }.associate {
        it.id to McpSecret(
            headers = it.headers.filterValues { v -> v.isNotBlank() },
            env = it.env.filterValues { v -> v.isNotBlank() },
            clientSecret = it.auth?.clientSecret.orEmpty(),
        )
    },
)

fun SettingsSnapshot.withoutSecrets(): SettingsSnapshot = copy(
    apiKey = null,
    githubToken = null,
    forges = forges.map { it.copy(token = "") },
    mcpServers = mcpServers.map { it.withoutSecrets() },
    secrets = null,
)

fun SettingsSnapshot.withSecrets(s: BackupSecrets): SettingsSnapshot = copy(
    apiKey = s.apiKey ?: apiKey,
    githubToken = s.githubToken ?: githubToken,
    forges = forges.map { f -> s.forgeTokens[f.id]?.let { f.copy(token = it) } ?: f },
    mcpServers = mcpServers.map { m ->
        val saved = s.mcp[m.id] ?: return@map m
        m.copy(
            headers = m.headers.mapValues { (k, v) -> saved.headers[k] ?: v },
            env = m.env.mapValues { (k, v) -> saved.env[k] ?: v },
            auth = m.auth?.let { a -> if (saved.clientSecret.isNotBlank()) a.copy(clientSecret = saved.clientSecret) else a },
        )
    },
    secrets = null,
)

/** Plain file without secrets, or with them sealed under [passphrase]. */
fun buildBackup(full: SettingsSnapshot, passphrase: CharArray?, iterations: Int = SecretsVault.ITERATIONS): SettingsSnapshot {
    val plain = full.withoutSecrets().copy(version = BACKUP_VERSION)
    if (passphrase == null) return plain
    val body = SECRETS_JSON.encodeToString(full.secrets()).toByteArray(Charsets.UTF_8)
    return plain.copy(secrets = SecretsVault.seal(body, passphrase, iterations))
}

enum class BackupOpen { Ok, WrongPassphrase }

data class BackupOpened(val snapshot: SettingsSnapshot, val result: BackupOpen)

/**
 * A file without a sealed block is returned as is (older exports carried plain secrets).
 * With one, [passphrase] decides: null drops the secrets, a wrong one applies nothing.
 */
fun openBackup(snap: SettingsSnapshot, passphrase: CharArray?): BackupOpened {
    val sealed = snap.secrets ?: return BackupOpened(snap, BackupOpen.Ok)
    if (passphrase == null) return BackupOpened(snap.copy(secrets = null), BackupOpen.Ok)
    val plain = SecretsVault.open(sealed, passphrase)
    val secrets = plain?.let {
        runCatching { SECRETS_JSON.decodeFromString<BackupSecrets>(it.toString(Charsets.UTF_8)) }.getOrNull()
    } ?: return BackupOpened(snap, BackupOpen.WrongPassphrase)
    return BackupOpened(snap.withSecrets(secrets), BackupOpen.Ok)
}

/** Imported forges with a blank token keep the token already saved for the same forge. */
fun mergeForgeSecrets(existing: List<ForgeConnection>, incoming: List<ForgeConnection>): List<ForgeConnection> {
    return incoming.map { f ->
        if (f.token.isNotBlank()) return@map f
        val old = existing.firstOrNull { it.id == f.id && f.id.isNotBlank() }
            ?: existing.firstOrNull { it.provider == f.provider && it.baseUrl == f.baseUrl && it.username == f.username }
        if (old != null && old.token.isNotBlank()) f.copy(token = old.token) else f
    }
}

/** Imported servers keep saved secrets where the file has none, matching by id and then by name. */
fun mergeMcpSecrets(existing: List<StoredMcpServer>, incoming: List<StoredMcpServer>): List<StoredMcpServer> {
    return incoming.map { m ->
        val old = existing.firstOrNull { it.id == m.id } ?: existing.firstOrNull { mcpNameKey(it.name) == mcpNameKey(m.name) }
        m.fillSecretsFrom(old)
    }
}

const val BACKUP_VERSION = 2
