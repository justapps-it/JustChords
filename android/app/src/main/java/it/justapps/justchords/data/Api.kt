package it.justapps.justchords.data

import it.justapps.justchords.core.FetchedSong
import it.justapps.justchords.core.Levels
import it.justapps.justchords.core.Profile
import it.justapps.justchords.core.SearchResult
import it.justapps.justchords.core.Sources
import it.justapps.justchords.core.Word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder

// Client for the Just-Chords API (the same Worker the website uses). Song
// pages are downloaded by the phone itself first, like a browser would.

class ApiError(val status: Int, message: String) : Exception(message)

data class User(val sub: String, val email: String, val name: String, val picture: String?) {
    fun toJson(): JSONObject = JSONObject().put("sub", sub).put("email", email).put("name", name).apply { picture?.let { put("picture", it) } }

    companion object {
        fun fromJson(o: JSONObject?): User? = o?.let {
            User(it.optString("sub"), it.optString("email"), it.optString("name"), it.optString("picture").ifEmpty { null })
        }
    }
}

data class Rec(
    val key: String,
    val title: String,
    val artist: String,
    val cover: String?,
    val level: Int?,
    val genres: List<String>,
    val reasonType: String,
    val reasonName: String?,
)

data class GenreName(val id: String, val it: String, val en: String)

data class LinkInfo(val kind: String, val url: String, val title: String?, val artist: String?, val youtubeId: String?, val videoId: String? = null)

data class Video(val id: String, val title: String, val channel: String)

class Api(
    private val base: String = "https://chords.justapps.it",
    private val token: () -> String? = { null },
    private val userAgent: String = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130 Mobile Safari/537.36",
) {
    companion object {
        /** Version of terms and privacy policy the user accepts at sign-in. */
        const val TERMS_VERSION = "2026-11-28"
        private val FETCH_HOSTS = setOf("www.ultimate-guitar.com", "tabs.ultimate-guitar.com", "ultimate-guitar.com", "www.accordiespartiti.it", "accordiespartiti.it")

        fun fetchAllowed(url: String) = runCatching { URI(url) }.getOrNull()?.let { it.scheme == "https" && it.host?.lowercase() in FETCH_HOSTS } == true

        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

        private fun readCapped(s: InputStream, cap: Int = 4 shl 20): String {
            val out = ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            while (out.size() < cap) {
                val n = s.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
            }
            return out.toString("UTF-8")
        }
    }

    private suspend fun call(path: String, method: String = "GET", body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val c = URL(base + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 12000
        c.readTimeout = 25000
        c.setRequestProperty("Accept", "application/json")
        token()?.let { c.setRequestProperty("Authorization", "Bearer $it") }
        try {
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = c.responseCode
            val text = (if (code < 400) c.inputStream else c.errorStream)?.use { readCapped(it) } ?: ""
            val data = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (code !in 200..299) throw ApiError(code, data.optString("error").ifEmpty { "HTTP $code" })
            data
        } finally {
            c.disconnect()
        }
    }

    /** Downloads a chords page from the phone (only the two song sites). */
    suspend fun phoneFetch(start: String): String? = withContext(Dispatchers.IO) {
        var url = start
        repeat(5) {
            if (!fetchAllowed(url)) return@withContext null
            val c = URL(url).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 10000
            c.readTimeout = 15000
            c.setRequestProperty("Accept", "text/html,application/json")
            c.setRequestProperty("Accept-Language", "it,en;q=0.8")
            c.setRequestProperty("User-Agent", userAgent)
            try {
                val code = c.responseCode
                if (code in 300..399) {
                    url = URL(URL(url), c.getHeaderField("Location") ?: return@withContext null).toString()
                    return@repeat
                }
                return@withContext if (code in 200..299) c.inputStream.use { readCapped(it) } else null
            } catch (e: Exception) {
                return@withContext null
            } finally {
                c.disconnect()
            }
        }
        null
    }

    // -------------------------------------------------------------- chords

    private fun result(o: JSONObject) = SearchResult(
        o.optString("source"), o.optString("title"), o.optString("artist"), o.optString("url"),
        o.optString("kind").ifEmpty { null }, o.optDouble("rating").takeIf { !it.isNaN() }, if (o.has("votes")) o.optInt("votes") else null,
        if (o.has("level")) o.optInt("level") else null, o.optString("key").ifEmpty { null },
    )

    suspend fun search(q: String): List<SearchResult> = coroutineScope {
        val viaWorker = async {
            runCatching {
                val a = call("/api/search?q=${enc(q)}").optJSONArray("results") ?: JSONArray()
                (0 until a.length()).map { result(a.getJSONObject(it)) }
            }
        }
        val phoneUg = phoneFetch(Sources.searchUrl("ug", q))?.let { Sources.relevant(Sources.parseUgSearch(it), q) } ?: emptyList()
        val w = viaWorker.await()
        if (phoneUg.isEmpty()) return@coroutineScope w.getOrThrow()
        Sources.onePerSong(phoneUg + (w.getOrNull() ?: emptyList()).filter { it.source != "ug" })
    }

    suspend fun song(url: String): FetchedSong {
        if (Sources.sourceOf(url) == null) throw ApiError(400, "unsupported site")
        phoneFetch(url)?.let { Sources.parseSong(it, url) }?.let { return it }
        val s = call("/api/song?url=${enc(url)}").getJSONObject("song")
        return FetchedSong(
            s.optString("source"), s.optString("url", url), s.optString("title"), s.optString("artist"), s.optString("text"),
            s.optString("key").ifEmpty { null }, if (s.has("capo")) s.optInt("capo") else null, if (s.has("level")) s.optInt("level") else null,
        )
    }

    suspend fun link(url: String): LinkInfo {
        val o = call("/api/link?url=${enc(url)}")
        return LinkInfo(
            o.optString("kind"), o.optString("url", url), o.optString("title").ifEmpty { null }, o.optString("artist").ifEmpty { null },
            o.optString("youtubeId").ifEmpty { null }, o.optString("videoId").ifEmpty { null },
        )
    }

    /** A ≤ 35 s 16-bit WAV clip → words with times (Whisper on the server; the audio is not kept). */
    suspend fun transcribe(wav: ByteArray, offset: Double): List<Word> = withContext(Dispatchers.IO) {
        val c = URL("$base/api/transcribe?offset=${"%.2f".format(java.util.Locale.ROOT, offset)}").openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 15000
        c.readTimeout = 90000
        c.doOutput = true
        c.setRequestProperty("Content-Type", "audio/wav")
        try {
            c.outputStream.use { it.write(wav) }
            val code = c.responseCode
            val text = (if (code < 400) c.inputStream else c.errorStream)?.use { readCapped(it) } ?: ""
            val o = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (code !in 200..299) throw ApiError(code, o.optString("error").ifEmpty { "HTTP $code" })
            val a = o.optJSONArray("words") ?: JSONArray()
            (0 until a.length()).map { a.getJSONObject(it).let { w -> Word(w.optString("w"), w.optDouble("s"), w.optDouble("e")) } }
        } finally {
            c.disconnect()
        }
    }

    suspend fun videos(q: String): List<Video> {
        val a = call("/api/yt?q=${enc(q)}").optJSONArray("items") ?: JSONArray()
        return (0 until a.length()).map { a.getJSONObject(it).let { v -> Video(v.optString("id"), v.optString("title"), v.optString("channel")) } }
    }

    // -------------------------------------------------------------- account

    data class Login(val token: String, val user: User, val profile: Profile?, val created: Boolean = false)

    private fun consent() = JSONObject().put("age14", true).put("terms", TERMS_VERSION)
    private fun login(o: JSONObject) =
        Login(o.getString("token"), User.fromJson(o.getJSONObject("user"))!!, o.optJSONObject("profile")?.let(Profile::fromJson), o.optBoolean("created"))

    suspend fun loginWithGoogle(credential: String): Login =
        login(call("/api/auth/google", "POST", JSONObject().put("credential", credential).put("consent", consent())))

    suspend fun register(email: String, password: String, name: String): Login =
        login(call("/api/auth/register", "POST", JSONObject().put("email", email).put("password", password).put("name", name).put("consent", consent())))

    suspend fun loginWithEmail(email: String, password: String): Login =
        login(call("/api/auth/login", "POST", JSONObject().put("email", email).put("password", password)))

    /** A Spotify / YouTube / Deezer playlist → artists and genres. */
    suspend fun playlist(url: String): it.justapps.justchords.core.PlaylistInfo {
        val o = call("/api/playlist?url=${enc(url)}")
        val tr = o.optJSONArray("tracks") ?: JSONArray()
        val ar = o.optJSONArray("artists") ?: JSONArray()
        val ge = o.optJSONArray("genres") ?: JSONArray()
        return it.justapps.justchords.core.PlaylistInfo(
            o.optString("name"), o.optString("source"),
            (0 until tr.length()).map { tr.getJSONObject(it).let { t -> t.optString("title") to t.optString("artist") } },
            (0 until ar.length()).map { ar.getJSONObject(it).let { a -> a.optString("name") to a.optInt("n") } },
            (0 until ge.length()).map { ge.getString(it) },
        )
    }

    suspend fun me(): Pair<User, Profile?> {
        val o = call("/api/me")
        return User.fromJson(o.getJSONObject("user"))!! to o.optJSONObject("profile")?.let(Profile::fromJson)
    }

    suspend fun putProfile(p: Profile) = call("/api/profile", "PUT", p.toJson())
    suspend fun sync(lib: JSONObject): JSONObject = call("/api/sync", "POST", lib)
    suspend fun deleteAccount() = call("/api/account", "DELETE")

    suspend fun recommendations(p: Profile, level: Int, known: List<String>): Pair<List<Rec>, List<GenreName>> {
        val o = call("/api/recs", "POST", JSONObject().put("profile", p.toJson()).put("level", level).put("known", JSONArray(known.take(5000))))
        val recs = o.optJSONArray("recs") ?: JSONArray()
        val genres = o.optJSONArray("genres") ?: JSONArray()
        return (0 until recs.length()).map { i ->
            val r = recs.getJSONObject(i)
            val reason = r.optJSONObject("reason") ?: JSONObject()
            val g = r.optJSONArray("genres") ?: JSONArray()
            Rec(
                r.optString("key"), r.optString("title"), r.optString("artist"), r.optString("cover").ifEmpty { null },
                if (r.has("level")) r.optInt("level") else null, (0 until g.length()).map { g.optString(it) },
                reason.optString("type"), reason.optString("name").ifEmpty { null },
            )
        } to (0 until genres.length()).map { genres.getJSONObject(it).let { x -> GenreName(x.optString("id"), x.optString("it"), x.optString("en")) } }
    }
}

/** Level of a song from its text, with a cache (the library list shows it for every song). */
object SongLevels {
    private val cache = object : LinkedHashMap<String, Int>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Int>?) = size > 500
    }

    @Synchronized
    fun of(chordpro: String): Int = cache.getOrPut(chordpro) { Levels.song(chordpro).level }
}
