package it.justapps.justchords.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import it.justapps.justchords.core.Signal
import it.justapps.justchords.core.Taste
import it.justapps.justchords.data.GenreName
import it.justapps.justchords.data.Rec
import it.justapps.justchords.data.Setlist
import it.justapps.justchords.data.Song
import it.justapps.justchords.data.SongLevels
import it.justapps.justchords.data.uid
import kotlinx.coroutines.delay
import java.text.Normalizer

private fun norm(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("[\\u0300-\\u036f]"), "")
fun isLink(s: String) = Regex("^https?://\\S+$").matches(s.trim())

@Composable
fun HomeScreen() {
    val t = rememberT()
    val nav = LocalNav.current
    val a = app()
    val lib by a.library.state.collectAsState()
    var q by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("recent") }
    var newList by remember { mutableStateOf(false) }

    val songs = remember(lib.songs, q, filter) {
        var list = lib.songs
        if (q.isNotBlank()) {
            val words = norm(q).split(Regex("\\s+")).filter { it.isNotEmpty() }
            list = list.filter { s -> words.all { norm("${s.title} ${s.artist}").contains(it) } }
        } else if (filter == "fav") list = list.filter { it.favorite }
        if (filter == "all" && q.isBlank()) list.sortedBy { it.title.lowercase() } else list.sortedByDescending { it.opened ?: it.updated }
    }
    val find = {
        val v = q.trim()
        if (v.isNotEmpty()) nav.navigate(if (isLink(v)) "find?url=${enc(v)}" else "find?q=${enc(v)}")
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate("import") },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text(t("importBtn")) },
                containerColor = JustOrange, contentColor = Color.White,
            )
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp + pad.calculateBottomPadding())) {
            item { Hero() }
            item {
                SearchField(q, { q = it }, t("searchOrLink"), onSubmit = find, modifier = Modifier.padding(horizontal = 16.dp))
                if (q.isNotBlank()) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(find) {
                            Text(if (isLink(q)) t("openLink") else t("findChords"))
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        AssistChip({ nav.navigate("import?title=${enc(q)}") }, { Text(t("paste")) }, leadingIcon = { Icon(Icons.Rounded.ContentPaste, null, Modifier.size(18.dp)) })
                    }
                    Muted(if (isLink(q)) t("linkHint") else t("findHint"), Modifier.padding(horizontal = 20.dp), small = true)
                }
            }
            if (q.isBlank()) {
                item { ToolsRow(Modifier.padding(top = 16.dp)) }
                item { RecsSection() }
            }
            item {
                SectionHeader(if (q.isNotBlank()) t("inLibrary") else t("library"), Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    if (q.isBlank()) Segmented(listOf("recent" to t("recent"), "all" to t("all"), "fav" to "★"), filter, { filter = it }, Modifier.width(236.dp))
                }
            }
            if (lib.loaded && lib.songs.isEmpty()) item { EmptyLibrary() }
            if (lib.songs.isNotEmpty() && songs.isEmpty()) item { Muted(t("noResults"), Modifier.padding(horizontal = 20.dp)) }
            items(songs, key = { it.id }) { s -> SongRow(s) { nav.navigate("song/${s.id}") } }
            item {
                SectionHeader(t("setlists"), Modifier.padding(start = 20.dp, end = 12.dp, top = 20.dp)) {
                    TextButton({ newList = true }) { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(t("newSetlist")) }
                }
            }
            items(lib.setlists, key = { "sl" + it.id }) { l ->
                ListRow(l.name, "${l.songs.size} ${t("songs")}", Icons.AutoMirrored.Rounded.QueueMusic) { nav.navigate("setlist/${l.id}") }
            }
            item { Muted(t("privacy"), Modifier.padding(20.dp), small = true) }
        }
    }
    if (newList) {
        NameDialog(t("newSetlist"), t("setlistName"), "", onDismiss = { newList = false }) { name ->
            val id = uid()
            a.library.saveSetlists(lib.setlists + Setlist(id, name, emptyList(), System.currentTimeMillis()))
            newList = false
            nav.navigate("setlist/$id")
        }
    }
}

@Composable
private fun Hero() {
    val t = rememberT()
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(JustOrange), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text("Just-Chords", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
        Spacer(Modifier.height(18.dp))
        Text(t("tagline"), style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, onSubmit: () -> Unit) {
    TextField(
        value, onChange,
        modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(if (isLink(value)) Icons.Rounded.Link else Icons.Rounded.Search, null) },
        trailingIcon = { if (value.isNotEmpty()) IconButton({ onChange("") }) { Icon(Icons.Rounded.Close, null) } },
        singleLine = true,
        shape = CircleShape,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = just.card, unfocusedContainerColor = just.card,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

data class ToolItem(val key: String, val route: String, val icon: ImageVector)

val TOOLS = listOf(
    ToolItem("analyze", "analyze", Icons.Rounded.AutoAwesome),
    ToolItem("tuner", "tuner", Icons.Rounded.GraphicEq),
    ToolItem("metronome", "metronome", Icons.Rounded.Timer),
    ToolItem("listen", "listen", Icons.Rounded.Headphones),
    ToolItem("dictionary", "chords", Icons.Rounded.MenuBook),
)

@Composable
private fun ToolsRow(modifier: Modifier = Modifier) {
    val t = rememberT()
    val nav = LocalNav.current
    LazyRow(modifier, contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(TOOLS) { tool ->
            JustCard(Modifier.width(132.dp), onClick = { nav.navigate(tool.route) }, padding = 14.dp) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(just.orangeSoft), contentAlignment = Alignment.Center) {
                    Icon(tool.icon, null, tint = JustOrange, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text(t(tool.key), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 2, lineHeight = 16.sp, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun SongRow(s: Song, trailing: @Composable () -> Unit = {}, onClick: () -> Unit) {
    val level = remember(s.chordpro) { SongLevels.of(s.chordpro) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(just.orangeSoft), contentAlignment = Alignment.Center) {
            Text(s.title.take(1).uppercase().ifEmpty { "♪" }, color = JustOrange, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (s.favorite) Icon(Icons.Rounded.Star, null, tint = JustOrange, modifier = Modifier.size(16.dp).padding(end = 2.dp))
                Text(s.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.artist, color = just.muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(8.dp))
                LevelBadge(level)
            }
        }
        trailing()
    }
}

@Composable
fun ListRow(title: String, sub: String, icon: ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = just.muted)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = just.muted, fontSize = 14.sp)
        }
    }
}

@Composable
private fun EmptyLibrary() {
    val t = rememberT()
    JustCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Icon(Icons.Rounded.LibraryMusic, null, tint = JustOrange)
        Spacer(Modifier.height(10.dp))
        Text(t("empty"), color = just.muted)
    }
}

@Composable
fun NameDialog(title: String, label: String, initial: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    val t = rememberT()
    var v by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(v, { v = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = { TextButton({ if (v.isNotBlank()) onOk(v.trim()) }, enabled = v.isNotBlank()) { Text(t("save")) } },
        dismissButton = { TextButton(onDismiss) { Text(t("cancel")) } },
    )
}

// ------------------------------------------------------------------ for you

private object RecsCache {
    var sig: String? = null
    var at = 0L
    var recs: List<Rec>? = null
    var names: List<GenreName> = emptyList()
}

@Composable
private fun RecsSection() {
    val t = rememberT()
    val nav = LocalNav.current
    val a = app()
    val st = settings()
    val lib by a.library.state.collectAsState()
    val acc by a.account.state.collectAsState()
    val p = acc.profile
    val empty = p.genres.isEmpty() && p.artists.isEmpty() && lib.songs.isEmpty()
    val level = remember(lib.songs, p.level) { a.account.effectiveLevel(lib.songs) }
    val known = remember(lib.songs) { lib.songs.map { Taste.songKey(it.title, it.artist) } }
    val sig = listOf(p.genres, p.level, level, p.artists.keys.sorted().take(12), p.disliked.size, known.size).toString()
    var recs by remember { mutableStateOf(if (RecsCache.sig == sig) RecsCache.recs else null) }
    var names by remember { mutableStateOf(RecsCache.names) }
    var err by remember { mutableStateOf(false) }

    LaunchedEffect(sig, lib.loaded) {
        if (!lib.loaded || empty) return@LaunchedEffect
        if (RecsCache.sig == sig && System.currentTimeMillis() - RecsCache.at < 3_600_000) {
            recs = RecsCache.recs
            names = RecsCache.names
            return@LaunchedEffect
        }
        delay(300)
        runCatching { a.account.api.recommendations(p, level, known) }
            .onSuccess { (r, g) ->
                recs = r; names = g; err = false
                RecsCache.sig = sig; RecsCache.at = System.currentTimeMillis(); RecsCache.recs = r; RecsCache.names = g
            }
            .onFailure { err = true }
    }

    if (!lib.loaded) return
    if (empty) {
        JustCard(Modifier.padding(16.dp).fillMaxWidth()) {
            Eyebrow(t("forYou"), color = JustOrange)
            Spacer(Modifier.height(6.dp))
            Text(t("recsCta"), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(14.dp))
            FilledTonalButton({ nav.navigate("profile") }) { Text(t("pickGenres")) }
        }
        return
    }
    fun reason(r: Rec) = when (r.reasonType) {
        "artist" -> "${t("becauseYouPlay")} ${r.reasonName}"
        "more" -> "${t("moreFrom")} ${r.artist}"
        "genre" -> names.find { it.id == r.reasonName }?.let { if (st.lang == "it") it.it else it.en } ?: r.reasonName ?: ""
        else -> t("forYourLevel")
    }
    SectionHeader(t("forYou"), Modifier.padding(start = 20.dp, end = 8.dp, top = 20.dp)) {
        TextButton({ nav.navigate("profile") }) { Text(t("tuneRecs")) }
    }
    if (err && recs == null) Muted(t("recsOffline"), Modifier.padding(horizontal = 20.dp))
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val list = recs
        if (list == null && !err) items(4) { Box(Modifier.width(160.dp).aspectRatio(0.72f).clip(MaterialTheme.shapes.large).background(just.soft)) }
        if (list != null) items(list, key = { it.key }) { r ->
            RecCard(r, reason(r), onOpen = {
                a.account.signal(Signal.OPEN, r.title, r.artist, r.genres)
                nav.navigate("find?q=${enc("${r.title} ${r.artist}")}&t=${enc(r.title)}&a=${enc(r.artist)}")
            }, onLike = {
                a.account.signal(Signal.LIKE, r.title, r.artist, r.genres)
            }, onDislike = {
                a.account.signal(Signal.DISLIKE, r.title, r.artist, r.genres)
                recs = recs?.filter { it.key != r.key }
            })
        }
    }
}

@Composable
private fun RecCard(r: Rec, reason: String, onOpen: () -> Unit, onLike: () -> Unit, onDislike: () -> Unit) {
    val t = rememberT()
    var liked by remember { mutableStateOf(false) }
    val shape = MaterialTheme.shapes.large
    Column(Modifier.width(160.dp).clip(shape).background(just.card).border(1.dp, just.line, shape)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clickable(onClick = onOpen)) {
            if (r.cover != null) AsyncImage(r.cover, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(JustOrange, Color(0xFFFF9A6B)))), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(40.dp))
            }
            r.level?.let { LevelBadge(it, Modifier.align(Alignment.TopStart).padding(8.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.9f))) }
        }
        Column(Modifier.clickable(onClick = onOpen).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(r.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(r.artist, color = just.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(reason, color = JustOrange, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = PlexMono)
        }
        HorizontalDivider(color = just.line)
        Row {
            IconButton({ liked = true; onLike() }, Modifier.weight(1f)) { Icon(Icons.Rounded.ThumbUp, t("like"), tint = if (liked) JustOrange else just.muted, modifier = Modifier.size(18.dp)) }
            IconButton(onDislike, Modifier.weight(1f)) { Icon(Icons.Rounded.ThumbDown, t("dislike"), tint = just.muted, modifier = Modifier.size(18.dp)) }
        }
    }
}
