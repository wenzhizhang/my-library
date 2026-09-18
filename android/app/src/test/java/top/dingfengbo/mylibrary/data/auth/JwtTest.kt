package top.dingfengbo.mylibrary.data.auth

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun jwt(payload: String): String {
    val encoder = Base64.getUrlEncoder().withoutPadding()
    val header = encoder.encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray())
    val body = encoder.encodeToString(payload.toByteArray())
    return "$header.$body.c2lnbmF0dXJl"
}

class JwtTest {
    @Test
    fun `reads exp from an unpadded payload`() {
        val token = jwt("""{"sub":"1","uuid":"abc","exp":1900000000}""")
        assertEquals(1_900_000_000L, Jwt.expiryEpochSeconds(token))
    }

    @Test
    fun `reads exp from a padded payload`() {
        // "ab" forces base64 padding characters in the encoded payload.
        val token = jwt("""{"sub":"ab","exp":1899999999}""")
        assertEquals(1_899_999_999L, Jwt.expiryEpochSeconds(token))
    }

    @Test
    fun `returns null when exp is absent`() {
        assertNull(Jwt.expiryEpochSeconds(jwt("""{"sub":"1","uuid":"abc"}""")))
    }

    @Test
    fun `returns null for malformed input`() {
        assertNull(Jwt.expiryEpochSeconds("not-a-jwt"))
        assertNull(Jwt.expiryEpochSeconds("header.!!!not-base64!!!.sig"))
        assertNull(Jwt.expiryEpochSeconds(""))
    }

    @Test
    fun `token that is still valid is not expired`() {
        val session = Session(token = "t", username = "u", uuid = "x", expiresAtEpochSeconds = 1_900_000_000)
        assertFalse(session.isExpired(nowEpochSeconds = 1_800_000_000))
    }

    @Test
    fun `token inside the clock-skew window counts as expired`() {
        val session = Session(token = "t", username = "u", uuid = "x", expiresAtEpochSeconds = 1_900_000_000)
        // 30s left, skew is 60s: the server would already be rejecting it by the time it arrives.
        assertTrue(session.isExpired(nowEpochSeconds = 1_899_999_970))
    }

    @Test
    fun `token without an exp claim is never treated as expired`() {
        val session = Session(token = "t", username = "u", uuid = "x", expiresAtEpochSeconds = null)
        assertFalse(session.isExpired(nowEpochSeconds = 4_000_000_000))
    }
}
