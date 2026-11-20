package app.brokoli5191.zeitig.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.brokoli5191.zeitig.R
import app.brokoli5191.zeitig.ui.components.RowItem
import app.brokoli5191.zeitig.ui.components.SectionCard
import app.brokoli5191.zeitig.ui.components.SectionHeader
import app.brokoli5191.zeitig.ui.expressive.BadgeShape
import app.brokoli5191.zeitig.ui.expressive.ShapeContainer

data class LibraryUi(
    val name: String,
    val version: String?,
    val licenses: String,
    val licenseText: String?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    version: String,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onLicences: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.about)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.align(Alignment.CenterHorizontally), horizontalAlignment = Alignment.CenterHorizontally) {
                ShapeContainer(BadgeShape.COOKIE, MaterialTheme.colorScheme.primary, size = 96.dp) {
                    Icon(
                        painterResource(R.drawable.ic_timer),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(48.dp),
                    )
                }
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMediumEmphasized,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    stringResource(R.string.version, version),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SectionCard(title = stringResource(R.string.data_sources)) {
                Text(stringResource(R.string.attribution_wl), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.attribution_transitous), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.attribution_basemap), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.attribution_icons), style = MaterialTheme.typography.bodyMedium)
            }
            SectionCard(title = stringResource(R.string.privacy)) {
                Text(stringResource(R.string.privacy_summary), style = MaterialTheme.typography.bodyMedium)
            }
            Column {
                SectionHeader(stringResource(R.string.links))
                LinkRow(R.drawable.ic_code, stringResource(R.string.source_code)) { onOpenUrl(REPO_URL) }
                LinkRow(R.drawable.ic_download, stringResource(R.string.releases)) { onOpenUrl("$REPO_URL/releases") }
                LinkRow(R.drawable.ic_info, stringResource(R.string.licences)) { onLicences() }
            }
            Text(
                stringResource(R.string.licence_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LinkRow(
    icon: Int,
    title: String,
    onClick: () -> Unit,
) {
    RowItem(
        headlineContent = { Text(title) },
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicencesScreen(
    libraries: List<LibraryUi>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var shown by rememberSaveable { mutableStateOf<String?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.licences)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(libraries, key = { it.name + it.version }) { library ->
                RowItem(
                    headlineContent = { Text(library.name) },
                    supportingContent = { Text(listOfNotNull(library.version, library.licenses).joinToString(" · ")) },
                    modifier = Modifier.clickable(enabled = library.licenseText != null, role = Role.Button) { shown = library.name },
                )
            }
        }
    }
    val library = libraries.firstOrNull { it.name == shown }
    if (library?.licenseText != null) {
        AlertDialog(
            onDismissRequest = { shown = null },
            title = { Text(library.licenses) },
            text = {
                Text(
                    library.licenseText,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = { TextButton(onClick = { shown = null }) { Text(stringResource(R.string.ok)) } },
        )
    }
}

const val REPO_URL = "https://github.com/Brokoli5191/Zeitig"
