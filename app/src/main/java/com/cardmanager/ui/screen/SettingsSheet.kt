package com.cardmanager.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cardmanager.R
import com.cardmanager.data.ImportMode
import com.cardmanager.tabs
import com.cardmanager.ui.components.ScreenPadding
import com.cardmanager.ui.theme.ColorGold
import com.cardmanager.ui.theme.ColorRed
import com.cardmanager.viewmodel.MainViewModel
import java.time.LocalDate

private const val USDT_TRC20_ADDRESS = "TBzhjqcTLQVkKiyGY3bE94RQnoj6TAFndw"

@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val themeMode by vm.themeMode.collectAsState()
    val cardsPerRowPortrait by vm.cardsPerRowPortrait.collectAsState()
    val cardsPerRowLandscape by vm.cardsPerRowLandscape.collectAsState()
    val ungroupedMode by vm.ungroupedMode.collectAsState()
    val tabOrder by vm.tabOrder.collectAsState()
    val visibleOptionalTabs by vm.visibleOptionalTabs.collectAsState()
    val preferHighRefreshRate by vm.preferHighRefreshRate.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var snackMsg by remember { mutableStateOf<String?>(null) }
    val fontImported = stringResource(R.string.font_imported_snackbar)
    val fontImportFailed = stringResource(R.string.font_import_failed)
    val fontReset = stringResource(R.string.font_reset_snackbar)
    var savedFontName by remember { mutableStateOf("") }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportUsePassword by remember { mutableStateOf(false) }
    var exportPassword by remember { mutableStateOf("") }
    var pendingExportPassword by remember { mutableStateOf<String?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var importMode by remember { mutableStateOf(ImportMode.MERGE) }
    var importPassword by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { savedFontName = vm.getSetting("custom_font_name", "") }
    LaunchedEffect(snackMsg) { snackMsg?.let { snackbarHostState.showSnackbar(it); snackMsg = null } }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri == null) {
            pendingExportPassword = null
            return@rememberLauncherForActivityResult
        }
        vm.exportBackup(uri, pendingExportPassword) { _, msg -> snackMsg = msg }
        pendingExportPassword = null
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        pendingImportUri = uri
        importMode = ImportMode.MERGE
        importPassword = ""
        showImportDialog = true
    }

    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            val cr = ctx.contentResolver
            val fileName = cr.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                ?: uri.lastPathSegment ?: "custom_font.ttf"
            val outFile = java.io.File(ctx.filesDir, "custom_font.ttf")
            cr.openInputStream(uri)?.use { input -> outFile.outputStream().use { out -> input.copyTo(out) } }
            ctx.getSharedPreferences("cm_font", android.content.Context.MODE_PRIVATE).edit()
                .putString("font_path", outFile.absolutePath)
                .putString("font_name", fileName)
                .apply()
            vm.setSetting("custom_font_path", outFile.absolutePath)
            vm.setSetting("custom_font_name", fileName)
            savedFontName = fileName
            snackMsg = fontImported.format(fileName)
        } catch (e: Exception) {
            snackMsg = fontImportFailed.format(e.message ?: "")
        }
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = {
                showExportDialog = false
                exportUsePassword = false
                exportPassword = ""
            },
            title = { Text(stringResource(R.string.export_backup_options)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.backup_password_body), fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = exportUsePassword, onCheckedChange = { exportUsePassword = it })
                        Text(stringResource(R.string.backup_use_password), fontSize = 13.sp)
                    }
                    if (exportUsePassword) {
                        OutlinedTextField(
                            value = exportPassword,
                            onValueChange = { exportPassword = it },
                            label = { Text(stringResource(R.string.backup_password)) },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !exportUsePassword || exportPassword.isNotBlank(),
                    onClick = {
                        pendingExportPassword = if (exportUsePassword) exportPassword else null
                        exportPassword = ""
                        exportUsePassword = false
                        showExportDialog = false
                        exportLauncher.launch(ctx.getString(R.string.backup_file_name, LocalDate.now().toString()))
                    }
                ) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showExportDialog = false
                    exportUsePassword = false
                    exportPassword = ""
                }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = {
                showImportDialog = false
                pendingImportUri = null
                importPassword = ""
            },
            title = { Text(stringResource(R.string.import_mode_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.import_mode_body), fontSize = 13.sp)
                    ImportModeRow(
                        selected = importMode == ImportMode.MERGE,
                        title = stringResource(R.string.import_merge),
                        onClick = { importMode = ImportMode.MERGE }
                    )
                    ImportModeRow(
                        selected = importMode == ImportMode.OVERWRITE,
                        title = stringResource(R.string.import_overwrite),
                        onClick = { importMode = ImportMode.OVERWRITE }
                    )
                    OutlinedTextField(
                        value = importPassword,
                        onValueChange = { importPassword = it },
                        label = { Text(stringResource(R.string.backup_password_optional)) },
                        supportingText = { Text(stringResource(R.string.import_password_hint)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val uri = pendingImportUri ?: return@TextButton
                    vm.importBackup(uri, importMode, importPassword.ifBlank { null }) { _, msg -> snackMsg = msg }
                    showImportDialog = false
                    pendingImportUri = null
                    importPassword = ""
                }) { Text(stringResource(R.string.import_backup)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImportDialog = false
                    pendingImportUri = null
                    importPassword = ""
                }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    Box(Modifier.fillMaxSize().background(cs.background)) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
            contentPadding = ScreenPadding,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                        Icon(
                            Icons.Default.ArrowBack,
                            stringResource(R.string.close),
                            tint = cs.onBackground,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Text(
                        stringResource(R.string.settings),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = cs.onBackground,
                        letterSpacing = 0.sp
                    )
                }
            }

            item {
                SettingsSection(stringResource(R.string.appearance)) {
                    ThemeModeRow(themeMode = themeMode, onChange = vm::setThemeMode)
                    SettingRow(
                        icon = Icons.Default.TextFields,
                        title = if (savedFontName.isNotEmpty()) stringResource(R.string.font_loaded, savedFontName) else stringResource(R.string.import_font),
                        subtitle = stringResource(R.string.font_support_hint),
                        onClick = { fontPicker.launch("*/*") },
                        trailing = { RowValue(stringResource(R.string.choose)) }
                    )
                    if (savedFontName.isNotEmpty()) {
                        SettingRow(
                            icon = Icons.Default.Close,
                            title = stringResource(R.string.restore_default_font),
                            subtitle = "",
                            onClick = {
                                ctx.getSharedPreferences("cm_font", android.content.Context.MODE_PRIVATE).edit().clear().apply()
                                vm.setSetting("custom_font_path", "")
                                vm.setSetting("custom_font_name", "")
                                savedFontName = ""
                                snackMsg = fontReset
                            },
                            trailing = {}
                        )
                    }
                    SettingRow(
                        icon = Icons.Default.Refresh,
                        title = stringResource(R.string.high_refresh_rate),
                        subtitle = stringResource(R.string.high_refresh_rate_summary),
                        trailing = { Switch(checked = preferHighRefreshRate, onCheckedChange = vm::setPreferHighRefreshRate) }
                    )
                }
            }

            item {
                SettingsSection(stringResource(R.string.card_layout)) {
                    CardsPerRowRow(
                        cardsPerRowPortrait = cardsPerRowPortrait,
                        cardsPerRowLandscape = cardsPerRowLandscape,
                        onPortraitChange = vm::setCardsPerRowPortrait,
                        onLandscapeChange = vm::setCardsPerRowLandscape
                    )
                    if (cardsPerRowPortrait >= 3) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.WarningAmber, null, tint = ColorGold, modifier = Modifier.size(16.dp))
                            Text(
                                stringResource(R.string.cards_per_row_dense_warning),
                                fontSize = 12.sp,
                                color = cs.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    SettingRow(
                        icon = Icons.Default.FolderOpen,
                        title = stringResource(R.string.ungrouped_mode),
                        subtitle = stringResource(R.string.ungrouped_mode_summary),
                        trailing = { Switch(checked = ungroupedMode, onCheckedChange = vm::setUngroupedMode) }
                    )
                }
            }

            item {
                SettingsSection(stringResource(R.string.tab_settings)) {
                    val orderedTabs = tabOrder.mapNotNull { id -> tabs.firstOrNull { it.id == id } }
                    orderedTabs.forEach { tab ->
                        val required = tab.id == "cards" || tab.id == "data"
                        val visible = required || tab.id in visibleOptionalTabs
                        TabSettingRow(
                            icon = tab.icon,
                            title = stringResource(tab.labelRes),
                            subtitle = stringResource(if (required) R.string.tab_required else if (visible) R.string.tab_visible else R.string.tab_hidden),
                            checked = visible,
                            switchEnabled = !required,
                            onCheckedChange = { vm.setTabVisible(tab.id, it) },
                            onMoveUp = { vm.moveTab(tab.id, -1) },
                            onMoveDown = { vm.moveTab(tab.id, 1) }
                        )
                    }
                }
            }

            item {
                SettingsSection(stringResource(R.string.data_management)) {
                    SettingRow(
                        icon = Icons.Default.Upload,
                        title = stringResource(R.string.export_full_backup),
                        subtitle = "",
                        onClick = { showExportDialog = true },
                        trailing = { Icon(Icons.Default.ChevronRight, null, tint = cs.onSurfaceVariant) }
                    )
                    SettingRow(
                        icon = Icons.Default.Download,
                        title = stringResource(R.string.import_backup),
                        subtitle = "",
                        onClick = { importLauncher.launch("*/*") },
                        trailing = { Icon(Icons.Default.ChevronRight, null, tint = cs.onSurfaceVariant) }
                    )
                }
            }

            item {
                SettingsSection(stringResource(R.string.about)) {
                    SettingRow(
                        icon = Icons.Default.CreditCard,
                        title = stringResource(R.string.app_name),
                        subtitle = stringResource(R.string.version_1),
                        trailing = {}
                    )
                    SettingRow(
                        icon = Icons.Default.Person,
                        title = stringResource(R.string.authors),
                        subtitle = stringResource(R.string.privacy_local_only),
                        trailing = {}
                    )
                    DonationSupportBlock()
                }
            }

            item { Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars).height(8.dp)) }
        }

        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

@Composable
private fun DonationSupportBlock() {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            stringResource(R.string.support_development),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = cs.onSurface
        )
        DonationCodeImage(
            title = stringResource(R.string.wechat_appreciation_code),
            drawableRes = R.drawable.donation_wechat
        )
        DonationCodeImage(
            title = stringResource(R.string.alipay_appreciation_code),
            drawableRes = R.drawable.donation_alipay
        )
        Text(
            stringResource(R.string.usdt_trc20_address),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = cs.onSurfaceVariant
        )
        SelectionContainer {
            Text(
                USDT_TRC20_ADDRESS,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(cs.surfaceVariant.copy(alpha = 0.42f))
                    .padding(10.dp),
                fontSize = 11.sp,
                lineHeight = 15.sp,
                fontFamily = FontFamily.Monospace,
                color = cs.onSurface
            )
        }
    }
}

@Composable
private fun DonationCodeImage(title: String, drawableRes: Int) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurfaceVariant)
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White)
                .padding(10.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(drawableRes),
                contentDescription = title,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
            )
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = cs.surface,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(vertical = 10.dp), content = content)
        }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(cs.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = cs.primary, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                Text(subtitle, fontSize = 11.sp, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing()
    }
}

@Composable
private fun RowValue(text: String) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = 13.sp, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(Icons.Default.ChevronRight, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ThemeModeRow(themeMode: String, onChange: (String) -> Unit) {
    val options = listOf(
        "system" to stringResource(R.string.theme_system),
        "light" to stringResource(R.string.current_light),
        "dark" to stringResource(R.string.current_dark)
    )
    SettingDropdownRow(
        icon = Icons.Default.DarkMode,
        title = stringResource(R.string.dark_mode),
        subtitle = "",
        value = options.firstOrNull { it.first == themeMode }?.second ?: options.first().second,
        options = options,
        onChange = onChange
    )
}

@Composable
private fun CardsPerRowRow(
    cardsPerRowPortrait: Int,
    cardsPerRowLandscape: Int,
    onPortraitChange: (Int) -> Unit,
    onLandscapeChange: (Int) -> Unit
) {
    Column {
        SettingDropdownRow(
            icon = Icons.Default.CreditCard,
            title = stringResource(R.string.cards_per_row_portrait),
            subtitle = "",
            value = stringResource(R.string.cards_per_row_option, cardsPerRowPortrait),
            options = (1..4).map { it.toString() to stringResource(R.string.cards_per_row_option, it) },
            onChange = { onPortraitChange(it.toIntOrNull() ?: cardsPerRowPortrait) }
        )
        SettingDropdownRow(
            icon = Icons.Default.CreditCard,
            title = stringResource(R.string.cards_per_row_landscape),
            subtitle = "",
            value = stringResource(R.string.cards_per_row_option, cardsPerRowLandscape),
            options = (1..6).map { it.toString() to stringResource(R.string.cards_per_row_option, it) },
            onChange = { onLandscapeChange(it.toIntOrNull() ?: cardsPerRowLandscape) }
        )
    }
}

@Composable
private fun SettingDropdownRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    value: String,
    options: List<Pair<String, String>>,
    onChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        SettingRow(
            icon = icon,
            title = title,
            subtitle = subtitle,
            onClick = { expanded = true },
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(value, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.Default.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.align(Alignment.TopEnd)) {
            options.forEach { (raw, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onChange(raw)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ImportModeRow(selected: Boolean, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(title, fontSize = 13.sp)
    }
}

@Composable
private fun TabSettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    switchEnabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, top = 9.dp, end = 12.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(cs.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = cs.primary, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
            Text(subtitle, fontSize = 11.sp, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Switch(checked = checked, enabled = switchEnabled, onCheckedChange = onCheckedChange)
        Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            IconButton(onClick = onMoveUp, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.KeyboardArrowUp, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onMoveDown, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
    }
}
