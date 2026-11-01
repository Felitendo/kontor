package de.felitendo.kontor.ui.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import de.felitendo.kontor.data.market.MarketApi
import de.felitendo.kontor.ui.LocalVm
import de.felitendo.kontor.ui.Routes
import de.felitendo.kontor.ui.assets.PickRow
import de.felitendo.kontor.ui.assets.SearchField
import de.felitendo.kontor.ui.components.InstrumentLogo
import de.felitendo.kontor.ui.components.Tag
import de.felitendo.kontor.ui.components.TopBar
import de.felitendo.kontor.ui.components.pressable
import de.felitendo.kontor.ui.components.shimmer
import de.felitendo.kontor.ui.theme.K
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(nav: NavController, depotId: String?) {
    Column(Modifier.fillMaxSize().background(K.colors.background)) {
        TopBar("Wertpapier suchen", onBack = { nav.popBackStack() })
        SearchContent { hit ->
            val i = hit.instrument
            nav.navigate(
                Routes.Instrument(
                    isin = i.isin, name = i.name, wkn = i.wkn, type = i.type.name,
                    onvistaType = i.onvistaType, onvistaId = i.onvistaId, onvistaSymbol = hit.onvistaSymbol,
                    depotId = depotId,
                ),
            )
        }
    }
}

private val suggestions = listOf(
    "A1JX52" to "Vanguard FTSE All-World",
    "A0RPWH" to "iShares Core MSCI World",
    "A2PKXG" to "Vanguard FTSE All-World (Acc)",
    "723610" to "Siemens",
    "865985" to "Apple",
    "A0F5UF" to "iShares Core DAX",
)

/** Live search by WKN, ISIN or name. */
@Composable
fun ColumnScope.SearchContent(autoFocus: Boolean = true, onPick: (MarketApi.SearchHit) -> Unit) {
    val vm = LocalVm.current
    val c = K.colors
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<MarketApi.SearchHit>?>(null) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) { results = null; loading = false; return@LaunchedEffect }
        loading = true
        delay(280)
        results = vm.c.market.search(q)
        loading = false
    }

    SearchField(
        query, { query = it }, "WKN, ISIN oder Name",
        Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        autoFocus = autoFocus, capitalization = KeyboardCapitalization.Characters,
    )
    AnimatedContent(
        targetState = when {
            loading && results == null -> "loading"
            results == null -> "empty"
            results!!.isEmpty() -> "none"
            else -> "results"
        },
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "search",
        modifier = Modifier.weight(1f).imePadding(),
    ) { mode ->
        when (mode) {
            "loading" -> Column(Modifier.fillMaxSize().padding(top = 8.dp)) { repeat(6) { SkeletonRow() } }
            "empty" -> LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Text("Beliebt", style = K.type.label, color = c.textSecondary, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp))
                }
                items(suggestions) { (wkn, name) ->
                    Row(
                        Modifier.fillMaxWidth().pressable { query = wkn }.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(name, style = K.type.body, color = c.text, modifier = Modifier.weight(1f))
                        Text(wkn, style = K.type.label, color = c.textSecondary)
                    }
                }
                item { Attribution() }
            }
            "none" -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
                Text("Nichts gefunden. Probier es mit der ISIN oder einem Teil des Namens.", style = K.type.body, color = c.textSecondary)
            }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(results.orEmpty(), key = { it.instrument.isin + it.instrument.onvistaId }) { hit ->
                    val i = hit.instrument
                    PickRow(
                        leading = { InstrumentLogo(i.isin, i.name) },
                        title = i.name,
                        subtitle = listOfNotNull(i.wkn?.let { "WKN $it" }, i.isin).joinToString(" · "),
                        trailing = { Tag(i.type.title) },
                    ) { onPick(hit) }
                }
                item { Attribution() }
            }
        }
    }
}

@Composable
fun Attribution(modifier: Modifier = Modifier) {
    val uri = LocalUriHandler.current
    Text(
        "Logos von Parqet",
        style = K.type.caption,
        color = K.colors.textTertiary,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .pressable(haptic = false) { uri.openUri("https://www.parqet.com") }
            .padding(20.dp),
    )
}

@Composable
fun SkeletonRow() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).shimmer())
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Box(Modifier.fillMaxWidth(0.7f).height(14.dp).clip(RoundedCornerShape(6.dp)).shimmer())
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth(0.4f).height(11.dp).clip(RoundedCornerShape(6.dp)).shimmer())
        }
    }
}
