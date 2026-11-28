package it.justapps.justchords.core

import org.json.JSONObject
import java.net.URI

// Song pages of Ultimate Guitar and Accordi e Spartiti → text to import, and
// link recognition (src/lib/sources.ts). The app downloads the page the user
// picks from the phone itself; search, links and recommendations go through
// the Just Chords API like on the web.

data class SearchResult(
    val source: String,
    val title: String,
    val artist: String,
    val url: String,
    val kind: String? = null,
    val rating: Double? = null,
    val votes: Int? = null,
    val level: Int? = null,
    val key: String? = null,
)

data class FetchedSong(
    val source: String,
    val url: String,
    val title: String,
    val artist: String,
    val text: String,
    val key: String? = null,
    val capo: Int? = null,
    val level: Int? = null,
)

enum class LinkKind { YOUTUBE, SPOTIFY, CHORDS, TIKTOK, INSTAGRAM }

/** A video to play and analyse: the platform and its id. */
data class VideoRef(val platform: String, val id: String)

object Sources {
    private val HOSTS = mapOf(
        "www.ultimate-guitar.com" to "ug", "tabs.ultimate-guitar.com" to "ug", "ultimate-guitar.com" to "ug",
        "www.accordiespartiti.it" to "aes", "accordiespartiti.it" to "aes",
    )

    private fun uri(url: String): URI? = runCatching { URI(url.trim()) }.getOrNull()?.takeIf { it.host != null }

    fun sourceOf(url: String): String? {
        val u = uri(url) ?: return null
        return if (u.scheme == "https") HOSTS[u.host.lowercase()] else null
    }

    fun searchUrl(source: String, q: String): String {
        val e = java.net.URLEncoder.encode(q, "UTF-8").replace("+", "%20")
        return if (source == "ug") "https://www.ultimate-guitar.com/search.php?search_type=title&value=$e" else "https://www.accordiespartiti.it/?s=$e"
    }

    private val ENTITIES = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ", "egrave" to "è", "eacute" to "é", "agrave" to "à",
        "ograve" to "ò", "ugrave" to "ù", "igrave" to "ì", "rsquo" to "’", "lsquo" to "‘", "ldquo" to "“", "rdquo" to "”", "hellip" to "…", "ndash" to "–", "mdash" to "—",
    )

    fun decodeEntities(s: String): String = Regex("&(#x[0-9a-f]+|#\\d+|[a-z]+);", RegexOption.IGNORE_CASE).replace(s) { m ->
        val e = m.groupValues[1]
        if (e[0] == '#') {
            val n = if (e.getOrNull(1) == 'x' || e.getOrNull(1) == 'X') e.substring(2).toIntOrNull(16) else e.substring(1).toIntOrNull()
            if (n != null && n in 0..0x10FFFF) String(Character.toChars(n)) else m.value
        } else ENTITIES[e.lowercase()] ?: m.value
    }

    fun htmlToText(html: String): String = decodeEntities(
        html.replace(Regex("<(script|style|noscript)[\\s\\S]*?</\\1>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</(p|div|h[1-6]|li|tr)>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), ""),
    ).replace(Regex("\r\n?"), "\n").replace('\u00a0', ' ').replace(Regex("[ \t]+$", RegexOption.MULTILINE), "")
        .replace(Regex("\n{3,}"), "\n\n").trim()

    private val UG_LEVEL = mapOf("novice" to 1, "beginner" to 1, "intermediate" to 2, "advanced" to 3)

    fun ugStore(html: String): JSONObject? {
        val m = Regex("class=\"js-store\"\\s+data-content=\"([^\"]*)\"").find(html) ?: Regex("data-content=\"([^\"]*)\"\\s+class=\"js-store\"").find(html)
        if (m != null) runCatching { return JSONObject(decodeEntities(m.groupValues[1])) }
        val w = Regex("window\\.UGAPP\\.store\\.page\\s*=\\s*(\\{[\\s\\S]*?\\});\\s*</script>").find(html)
        if (w != null) runCatching { return JSONObject().put("store", JSONObject().put("page", JSONObject(w.groupValues[1]))) }
        return null
    }

    private fun titleCase(s: String) = Regex("(^|[\\s-])(\\p{L})").replace(s) { it.groupValues[1] + it.groupValues[2].uppercase() }

    private val FIRST_NAMES = ("vasco luciano lucio francesco fabrizio franco gianni gino giorgio giorgia laura elisa tiziano cesare marco max eros renato rino adriano " +
        "ornella loredana fiorella edoardo antonello claudio enrico ivano umberto biagio paolo pino riccardo roberto sergio carmen gigi alessandra annalisa arisa " +
        "emma irene giusy noemi alex carl ed john paul george bob david michael neil bruce eric elton james jimmy johnny tom taylor ben billie lewis luca daniele " +
        "davide diodato giuliano filippo samuele levante gianna mia patty anna nada niccolo francesca alessandro angelo nino vinicio jovanotti").split(" ").toSet()

    fun aesArtist(slug: String): String {
        val w = slug.split("-").filter { it.isNotEmpty() }.toMutableList()
        if (w.size >= 2 && w.last() in FIRST_NAMES) w.add(0, w.removeAt(w.size - 1))
        return titleCase(w.joinToString(" "))
    }

    private fun aesTitle(raw: String, url: String): Pair<String, String> {
        val t = decodeEntities(raw.replace(Regex("<[^>]+>"), " ")).replace(Regex("\\s+"), " ")
            .replace(Regex("^\\s*accordi\\s+(di\\s+|per\\s+)?", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*[-–|]\\s*accordi\\s*e\\s*spartiti.*$", RegexOption.IGNORE_CASE), "").trim()
        val parts = t.split(Regex("\\s+[-–—]\\s+"))
        if (parts.size >= 2) return parts[0] to parts.drop(1).joinToString(" - ")
        val slug = uri(url)?.path?.split("/")?.filter { it.isNotEmpty() } ?: emptyList()
        if (slug.size >= 2) return (t.ifEmpty { titleCase(slug.last().replace("-", " ")) }) to aesArtist(slug[slug.size - 2])
        return t to ""
    }

    fun parseUgTab(html: String, url: String): FetchedSong? {
        val data = ugStore(html)?.optJSONObject("store")?.optJSONObject("page")?.optJSONObject("data") ?: return null
        val view = data.optJSONObject("tab_view") ?: return null
        val content = view.optJSONObject("wiki_tab")?.optString("content")?.takeIf { it.isNotEmpty() } ?: return null
        val tab = data.optJSONObject("tab") ?: JSONObject()
        val meta = view.optJSONObject("meta") ?: JSONObject()
        val capo = meta.optString("capo", "0").toIntOrNull()?.takeIf { it != 0 }
        val key = tab.optString("tonality_name").ifEmpty { meta.optString("tonality") }.ifEmpty { null }
        val diff = meta.optString("difficulty").ifEmpty { tab.optString("difficulty") }.lowercase()
        return FetchedSong("ug", url, tab.optString("song_name"), tab.optString("artist_name"), content.replace(Regex("\r\n?"), "\n"), key, capo, UG_LEVEL[diff])
    }

    fun parseAesSong(html: String, url: String): FetchedSong? {
        val pres = Regex("<pre[^>]*>([\\s\\S]*?)</pre>", RegexOption.IGNORE_CASE).findAll(html).map { htmlToText(it.groupValues[1]) }
        var text = pres.maxByOrNull { it.length } ?: ""
        if (text.length < 40) {
            val body = Regex(
                "<div[^>]+class=\"[^\"]*(entry-content|post-content|td-post-content|the-content)[^\"]*\"[^>]*>([\\s\\S]*?)</(article|section)>",
                RegexOption.IGNORE_CASE,
            ).find(html)
            text = body?.let { htmlToText(it.groupValues[2]) } ?: ""
        }
        if (text.length < 20) return null
        val h1 = Regex("<h1[^>]*>([\\s\\S]*?)</h1>", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
            ?: Regex("<title>([\\s\\S]*?)</title>", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1) ?: ""
        val (title, artist) = aesTitle(h1, url)
        text = text.split(Regex("\n(?:Condividi|Ti potrebbe interessare|Articoli correlati|Commenti)\\b", RegexOption.IGNORE_CASE))[0]
        return FetchedSong("aes", url, title, artist, text)
    }

    fun parseSong(html: String, url: String): FetchedSong? = when (sourceOf(url)) {
        "ug" -> parseUgTab(html, url)
        "aes" -> parseAesSong(html, url)
        else -> null
    }

    // ------------------------------------------------------------ search

    fun parseUgSearch(html: String): List<SearchResult> {
        val data = ugStore(html)?.optJSONObject("store")?.optJSONObject("page")?.optJSONObject("data") ?: return emptyList()
        val list = buildList {
            for (k in listOf("results", "other_tabs")) data.optJSONArray(k)?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.let(::add) }
        }
        val out = list.mapNotNull { r ->
            val kind = r.optString("type")
            val url = r.optString("tab_url")
            if (url.isEmpty() || r.has("marketing_type") || !kind.contains("chords", true)) return@mapNotNull null
            val votes = r.optInt("votes", 0)
            val version = r.optInt("version", 1)
            SearchResult(
                "ug", r.optString("song_name"), r.optString("artist_name"), url,
                kind + (if (version > 1) " v$version" else ""),
                r.optDouble("rating").takeIf { !it.isNaN() && votes > 0 }?.let { Math.round(it * 10) / 10.0 },
                if (r.has("votes")) votes else null,
                UG_LEVEL[r.optString("difficulty").lowercase()],
                r.optString("tonality_name").ifEmpty { null },
            )
        }
        fun uke(r: SearchResult) = if (r.kind?.contains("ukulele", true) == true) 1 else 0
        fun q(r: SearchResult) = (r.rating ?: 0.0) * kotlin.math.log10(10.0 + (r.votes ?: 0))
        return out.sortedWith(compareBy<SearchResult> { uke(it) }.thenByDescending { q(it) })
    }

    private fun norm(x: String) = java.text.Normalizer.normalize(x.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("[\\u0300-\\u036f]"), "")

    /** Keeps the results that contain every word (3+ letters) of the query. */
    fun relevant(list: List<SearchResult>, q: String): List<SearchResult> {
        val words = norm(q).split(Regex("[^a-z0-9]+")).filter { it.length > 2 }
        return list.filter { r -> val hay = norm("${r.title} ${r.artist}"); words.all { hay.contains(it) } }
    }

    private fun keyOf(s: String) = norm(s)
        .replace(Regex("\\s*[(\\[].*?[)\\]]"), "")
        .replace(Regex("\\b(the|feat|ft)\\b.*$")) { if (it.value.startsWith("the")) it.value else "" }
        .replace(Regex("[^a-z0-9]+"), " ").trim()

    private fun quality(r: SearchResult) = (r.rating ?: 3.5) * kotlin.math.log10(10.0 + (r.votes ?: 0))

    /** One result per song: the best guitar version by the original artist (sources.ts onePerSong). */
    fun onePerSong(list: List<SearchResult>): List<SearchResult> {
        class G(var best: SearchResult, var votes: Int, val order: Int)
        val groups = LinkedHashMap<String, G>()
        list.filter { !Regex("ukulele|bass|tab", RegexOption.IGNORE_CASE).containsMatchIn(it.kind ?: "") }.forEachIndexed { order, r ->
            val k = "${keyOf(r.title)}|${keyOf(r.artist).split(" ").sorted().joinToString(" ")}"
            val g = groups[k]
            if (g == null) groups[k] = G(r, r.votes ?: 0, order)
            else {
                g.votes += r.votes ?: 0
                if ((r.source == "ug" && g.best.source != "ug") || (r.source == g.best.source && quality(r) > quality(g.best))) g.best = r
            }
        }
        val byTitle = HashMap<String, Int>()
        for ((k, g) in groups) { val t = k.substringBefore('|'); byTitle[t] = maxOf(byTitle[t] ?: 0, g.votes) }
        val topAll = groups.values.maxOfOrNull { it.votes } ?: 0
        return groups.entries.filter { (k, g) ->
            if (Regex("^misc\\b|mashup", RegexOption.IGNORE_CASE).containsMatchIn(g.best.artist)) return@filter false
            val top = byTitle[k.substringBefore('|')] ?: 0
            if (top >= 50 && g.votes * 10 < top) return@filter false
            if (topAll >= 1000 && g.best.source == "ug" && g.votes * 100 < topAll) return@filter false
            true
        }.sortedBy { it.value.order }.map { (_, g) -> g.best.copy(votes = if (g.votes != 0) g.votes else g.best.votes) }
    }

    // ------------------------------------------------------------ links

    fun youtubeId(url: String): String? {
        val u = uri(url) ?: return null
        val host = u.host.lowercase()
        if (Regex("(^|\\.)youtu\\.be$").containsMatchIn(host)) return u.path.trimStart('/').split("/")[0].ifEmpty { null }
        if (Regex("(^|\\.)youtube(-nocookie)?\\.com$").containsMatchIn(host)) {
            val v = u.rawQuery?.split("&")?.firstOrNull { it.startsWith("v=") }?.substring(2)
            if (!v.isNullOrEmpty()) return v
            return Regex("^/(?:embed|shorts|live|v)/([\\w-]{6,})").find(u.path ?: "")?.groupValues?.get(1)
        }
        return null
    }

    fun linkKind(url: String): LinkKind? {
        if (sourceOf(url) != null) return LinkKind.CHORDS
        if (youtubeId(url) != null) return LinkKind.YOUTUBE
        val u = uri(url) ?: return null
        if (u.host == "open.spotify.com" && u.path.contains("/track/")) return LinkKind.SPOTIFY
        socialVideo(url)?.let { return if (it.platform == "tiktok") LinkKind.TIKTOK else LinkKind.INSTAGRAM }
        if (isShortTiktok(url)) return LinkKind.TIKTOK
        return null
    }

    /** TikTok and Instagram video links (short vm.tiktok.com links need resolving first). */
    fun socialVideo(url: String): VideoRef? {
        val u = uri(url) ?: return null
        val host = u.host.lowercase().removePrefix("www.").removePrefix("m.")
        val path = u.path ?: return null
        if (host == "tiktok.com") return Regex("/(?:video|v|photo|embed(?:/v2)?|player/v1)/(\\d{8,})").find(path)?.let { VideoRef("tiktok", it.groupValues[1]) }
        if (host == "instagram.com") return Regex("^/(?:[\\w.]+/)?(?:reels?|p|tv)/([\\w-]{5,})").find(path)?.let { VideoRef("instagram", it.groupValues[1]) }
        return null
    }

    fun isShortTiktok(url: String) = Regex("^https://(vm|vt)\\.tiktok\\.com/\\w+").containsMatchIn(url.trim()) || Regex("^https://(www\\.)?tiktok\\.com/t/\\w+").containsMatchIn(url.trim())

    fun videoRef(url: String): VideoRef? = youtubeId(url)?.let { VideoRef("youtube", it) } ?: socialVideo(url)

    /** Official embedded players: the video is played, never downloaded. */
    fun embedUrl(v: VideoRef): String = when (v.platform) {
        "youtube" -> "https://www.youtube-nocookie.com/embed/${v.id}?rel=0&playsinline=1&modestbranding=1"
        "tiktok" -> "https://www.tiktok.com/player/v1/${v.id}?music_info=1&description=1&rel=0"
        else -> "https://www.instagram.com/reel/${v.id}/embed/"
    }

    fun firstLink(text: String) = Regex("https?://\\S+").find(text)?.value
}
