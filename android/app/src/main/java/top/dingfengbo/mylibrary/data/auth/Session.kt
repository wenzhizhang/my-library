package top.dingfengbo.mylibrary.data.auth

/** An authenticated session. [expiresAtEpochSeconds] comes from the token's `exp` claim. */
data class Session(
    val token: String,
    val username: String,
    val uuid: String,
    val expiresAtEpochSeconds: Long?,
) {
    /** [skewSeconds] keeps a token from being sent while the server is about to reject it. */
    fun isExpired(
        nowEpochSeconds: Long = System.currentTimeMillis() / 1000,
        skewSeconds: Long = 60,
    ): Boolean {
        val expiry = expiresAtEpochSeconds ?: return false
        return nowEpochSeconds + skewSeconds >= expiry
    }
}
