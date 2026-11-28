package it.justapps.justchords.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import coil3.compose.AsyncImage
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import it.justapps.justchords.R
import it.justapps.justchords.core.Notation
import it.justapps.justchords.core.Taste
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Google's account sheet through Credential Manager; returns the ID token. */
suspend fun googleIdToken(ctx: Context): String {
    val option = GetSignInWithGoogleOption.Builder(ctx.getString(R.string.google_web_client_id)).build()
    val res = CredentialManager.create(ctx).getCredential(ctx, GetCredentialRequest(listOf(option)))
    return GoogleIdTokenCredential.createFrom(res.credential.data).idToken
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen() {
    val t = rememberT()
    val nav = LocalNav.current
    val a = app()
    val st = settings()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val acc by a.account.state.collectAsState()
    val lib by a.library.state.collectAsState()
    val p = acc.profile
    var err by remember { mutableStateOf("") }
    var consent by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val estimated = remember(lib.songs, p.level) { a.account.effectiveLevel(lib.songs) }

    val saveData = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) { runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(a.account.exportData().toByteArray()) } } }
    }
    fun signIn() {
        err = ""
        busy = true
        scope.launch {
            try {
                val created = a.account.completeLogin(googleIdToken(ctx))
                afterLogin(nav, created, a.account.profile.onboarded, home = false)
            } catch (e: GetCredentialCancellationException) {
                // closed by the user
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                err = e.message ?: e.javaClass.simpleName
            }
            busy = false
        }
    }

    Page(t("profile"), onBack = null) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding(), bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                JustCard(Modifier.fillMaxWidth()) {
                    val user = acc.user
                    if (user != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (user.picture != null) AsyncImage(user.picture, null, Modifier.size(52.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                            else Box(Modifier.size(52.dp).clip(CircleShape).background(just.orangeSoft), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Person, null, tint = JustOrange) }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(user.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                Text(user.email, color = just.muted, fontSize = 14.sp)
                                Text(
                                    when (acc.sync) {
                                        "syncing" -> t("syncing")
                                        "error" -> "${t("syncError")}: ${acc.error}"
                                        else -> "${t("synced")} · ${lib.songs.size} ${t("songs")}"
                                    },
                                    color = if (acc.sync == "error") MaterialTheme.colorScheme.error else just.muted, fontSize = 13.sp,
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton({ scope.launch { a.account.syncNow() } }) { Icon(Icons.Rounded.Sync, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("syncNow")) }
                            OutlinedButton({ a.account.logout() }) { Text(t("logout")) }
                        }
                        Row {
                            TextButton({ saveData.launch("just-chords-data.json") }) { Text(t("downloadData"), fontSize = 13.sp) }
                            TextButton({ confirmDelete = true }) { Text(t("deleteAccount"), color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                        }
                    } else {
                        Text(t("loginTitle"), style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(4.dp))
                        Muted(t("loginText"))
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.clip(MaterialTheme.shapes.small).clickable { consent = !consent }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(consent, { consent = it }, colors = CheckboxDefaults.colors(checkedColor = JustOrange))
                            val link = SpanStyle(fontWeight = FontWeight.SemiBold)
                            Text(buildAnnotatedString {
                                append("${t("consentAge")} ")
                                withStyle(link) { append(t("terms")) }
                                append(" ${t("consentAnd")} ")
                                withStyle(link) { append(t("privacy2")) }
                                append(".")
                            }, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        }
                        Row(Modifier.padding(start = 40.dp)) {
                            TextButton({ nav.navigate("legal/terms") }) { Text(t("terms"), fontSize = 13.sp) }
                            TextButton({ nav.navigate("legal/privacy") }) { Text(t("privacy2"), fontSize = 13.sp) }
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            ::signIn, Modifier.fillMaxWidth().height(52.dp), enabled = consent && !busy,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface, contentColor = MaterialTheme.colorScheme.surface),
                        ) {
                            GoogleG()
                            Spacer(Modifier.width(10.dp))
                            Text(t("loginGoogle"), fontWeight = FontWeight.SemiBold)
                        }
                        if (!consent) Muted(t("consentNeeded"), Modifier.padding(top = 6.dp), small = true)
                        Spacer(Modifier.height(12.dp))
                        EmailLogin(consent) { created -> afterLogin(nav, created, a.account.profile.onboarded, home = false) }
                    }
                    if (err.isNotEmpty()) Text(err, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
            item {
                JustCard(Modifier.fillMaxWidth()) {
                    Text(t("yourGenres"), style = MaterialTheme.typography.titleLarge)
                    Muted(t("genresHint"), small = true)
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (g in Taste.GENRES) {
                            val on = g.id in p.genres
                            FilterChip(on, { a.account.setProfile(p.copy(genres = if (on) p.genres - g.id else p.genres + g.id)) }, { Text(if (st.lang == "it") g.it else g.en) }, colors = chipColors())
                        }
                    }
                }
            }
            item {
                JustCard(Modifier.fillMaxWidth()) {
                    Text(t("yourLevel"), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(10.dp))
                    Segmented(listOf(0 to t("levelAuto"), 1 to t("level1"), 2 to t("level2"), 3 to t("level3")), p.level, { a.account.setProfile(p.copy(level = it)) }, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    if (p.level == 0) Row(verticalAlignment = Alignment.CenterVertically) { Muted(t("levelEstimated"), small = true); Spacer(Modifier.width(8.dp)); LevelBadge(estimated) }
                    else Muted(t("level${p.level}Hint"), small = true)
                }
            }
            val artists = p.artists.filterValues { it > 0.3 }.entries.sortedByDescending { it.value }.take(15)
            if (artists.isNotEmpty()) item {
                JustCard(Modifier.fillMaxWidth()) {
                    Text(t("learntArtists"), style = MaterialTheme.typography.titleLarge)
                    Muted(t("learntHint"), small = true)
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((name, _) in artists) InputChip(
                            false, { a.account.setProfile(p.copy(artists = p.artists - name)) },
                            { Text(name.split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }) },
                            trailingIcon = { Icon(Icons.Rounded.Close, t("remove"), Modifier.size(16.dp)) },
                        )
                    }
                }
            }
            item {
                JustCard(Modifier.fillMaxWidth()) {
                    Text(t("settings"), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(12.dp))
                    SettingRow(t("language")) { Segmented(listOf("it" to "Italiano", "en" to "English"), st.lang, { l -> a.settings.update { it.copy(lang = l) } }) }
                    SettingRow(t("notation")) { Segmented(listOf(Notation.IT to "Do Re Mi", Notation.EN to "C D E"), st.notation, { n -> a.settings.update { it.copy(notation = n) } }) }
                    SettingRow(t("theme")) { Segmented(listOf("auto" to "Auto", "light" to "☀", "dark" to "☾"), st.theme, { th -> a.settings.update { it.copy(theme = th) } }) }
                    SettingRow(t("lefty")) { Switch(st.lefty, { v -> a.settings.update { it.copy(lefty = v) } }, colors = SwitchDefaults.colors(checkedTrackColor = JustOrange)) }
                }
            }
            item {
                Muted(t("profilePrivacy"), Modifier.padding(horizontal = 4.dp), small = true)
                Row {
                    TextButton({ nav.navigate("legal/privacy") }) { Text("Privacy") }
                    TextButton({ nav.navigate("legal/terms") }) { Text(t("terms")) }
                    TextButton({ runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.justapps.it"))) } }) { Text("JustApps") }
                }
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        ticlyText("Just × ticly"), fontWeight = FontWeight.Bold, fontSize = 15.sp,
                        modifier = Modifier.clip(MaterialTheme.shapes.small).clickable { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.ticly.it"))) } },
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "info@ticly.it", color = JustOrange, fontSize = 13.sp,
                        modifier = Modifier.clip(MaterialTheme.shapes.small).clickable { runCatching { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:info@ticly.it"))) } },
                    )
                }
                Muted("Just-Chords ${it.justapps.justchords.BuildConfig.VERSION_NAME} · GPL-3.0", Modifier.padding(horizontal = 12.dp), small = true)
            }
        }
    }
    if (confirmDelete) AlertDialog(
        { confirmDelete = false },
        title = { Text(t("deleteAccount")) },
        text = { Text(t("deleteAccountConfirm")) },
        confirmButton = {
            TextButton({
                confirmDelete = false
                scope.launch { runCatching { a.account.deleteAccount() }.onFailure { err = it.message ?: "error" } }
            }) { Text(t("deleteAccount"), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton({ confirmDelete = false }) { Text(t("cancel")) } },
    )
}

@Composable
private fun SettingRow(label: String, control: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontWeight = FontWeight.Medium)
        control()
    }
}

@Composable
fun GoogleG() {
    androidx.compose.foundation.Image(
        androidx.compose.ui.res.painterResource(R.drawable.ic_google), null, Modifier.size(20.dp),
    )
}

@Composable
fun LegalScreen(doc: String) {
    val st = settings()
    val d = (if (doc == "terms") LegalText.terms else LegalText.privacy)[st.lang] ?: LegalText.privacy.getValue("en")
    val ctx = LocalContext.current
    Page(d.title) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = pad.calculateTopPadding(), bottom = 40.dp)) {
            for ((h, ps) in d.sections) {
                item {
                    Text(h, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
                    for (para in ps) {
                        val url = Regex("https?://\\S+|www\\.[a-z.]+").find(para)?.value
                        Text(
                            ticlyText(para), Modifier.padding(bottom = 8.dp).then(
                                if (url != null) Modifier.clickable { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(if (url.startsWith("http")) url else "https://$url"))) } } else Modifier,
                            ),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f), lineHeight = 22.sp,
                        )
                    }
                }
            }
            item { HorizontalDivider(Modifier.padding(vertical = 12.dp), color = just.line) }
        }
    }
}
