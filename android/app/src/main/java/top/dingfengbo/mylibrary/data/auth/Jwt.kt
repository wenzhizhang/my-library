package top.dingfengbo.mylibrary.data.auth

import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Reads claims out of a JWT payload without verifying the signature.
 *
 * Signature verification is the server's job. The client only needs `exp` so it can stop
 * sending a dead token: this backend answers read endpoints with **200 + demo.db data** when
 * the bearer token is invalid or expired (auth.py swallows JWTError and database.py falls
 * back to the shared demo database), so a stale token would silently show another library.
 */
object Jwt {
    private val json = Json { ignoreUnknownKeys = true }

    fun expiryEpochSeconds(token: String): Long? =
        runCatching {
            val payload = token.split('.').getOrNull(1) ?: return null
            val decoded = Base64.getUrlDecoder().decode(pad(payload))
            json.parseToJsonElement(String(decoded, Charsets.UTF_8)).jsonObject["exp"]?.jsonPrimitive?.longOrNull
        }.getOrNull()

    /** JWT payloads drop base64 padding; java.util.Base64 requires it. */
    private fun pad(value: String): String = value + "=".repeat((4 - value.length % 4) % 4)
}
