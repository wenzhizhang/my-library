package top.dingfengbo.mylibrary.data

import top.dingfengbo.mylibrary.BuildConfig

/**
 * Turns the relative media paths the API returns (`/images/books/9787806631744.webp`) into URLs.
 *
 * Goes through the backend's media proxy rather than the CDN host the web app hardcodes, so the
 * same BASE_URL governs everything and switching to a local backend also switches covers. The
 * proxy is public — no Authorization header needed, which is why Coil can use its own client.
 */
object MediaUrls {
    fun image(path: String?): String? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val base = BuildConfig.BASE_URL.trimEnd('/')
        val suffix = if (path.startsWith("/")) path else "/$path"
        return "$base/api/media$suffix"
    }
}
