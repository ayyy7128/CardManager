package com.cardmanager

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.view.WindowCompat
import com.cardmanager.R
import com.cardmanager.data.ReleaseInfo
import com.cardmanager.data.UpdateCheckResult
import com.cardmanager.data.UpdateCheckService
import com.cardmanager.ui.components.UpdateAvailableDialog
import com.cardmanager.ui.screen.*
import com.cardmanager.ui.theme.*
import com.cardmanager.viewmodel.MainViewModel

const val EXTRA_WIDGET_TARGET_TAB = "com.cardmanager.extra.WIDGET_TARGET_TAB"
const val EXTRA_WIDGET_ASSET_PLAN_ID = "com.cardmanager.extra.WIDGET_ASSET_PLAN_ID"
const val WIDGET_TARGET_CARDS = "cards"
const val WIDGET_TARGET_CALENDAR = "calendar"
const val WIDGET_TARGET_PIGGY = "piggy"

data class WidgetLaunchRequest(
    val targetTab: String,
    val assetPlanId: String = ""
)

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private var widgetLaunchRequest by mutableStateOf<WidgetLaunchRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetLaunchRequest = widgetLaunchRequestFrom(intent)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val themeMode by vm.themeMode.collectAsState()
            val preferHighRefreshRate by vm.preferHighRefreshRate.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> systemDark
            }
            // ── 状态栏图标颜色随主题变 ───────────────────────
            val view = LocalView.current
            SideEffect {
                try {
                    val win = (view.context as android.app.Activity).window
                    WindowCompat.getInsetsController(win, view).apply {
                        isAppearanceLightStatusBars = !isDark
                        isAppearanceLightNavigationBars = !isDark
                    }
                    val maxRefreshRate = view.display?.supportedModes?.maxOfOrNull { it.refreshRate } ?: 0f
                    win.attributes = win.attributes.apply {
                        preferredRefreshRate = if (preferHighRefreshRate && maxRefreshRate > 60f) maxRefreshRate else 0f
                    }
                } catch (_: Exception) {}
            }
            CardManagerTheme(isDark = isDark) {
                val configuration = LocalConfiguration.current
                val localeKey = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    configuration.locales.toLanguageTags()
                } else {
                    @Suppress("DEPRECATION")
                    configuration.locale.toLanguageTag()
                }
                val density = LocalDensity.current
                key(localeKey) {
                    CompositionLocalProvider(
                        LocalDensity provides Density(density.density, density.fontScale.coerceAtMost(1.12f))
                    ) {
                        CardManagerApp(
                            vm = vm,
                            launchRequest = widgetLaunchRequest,
                            onLaunchRequestConsumed = { widgetLaunchRequest = null }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        widgetLaunchRequest = widgetLaunchRequestFrom(intent)
    }

    private fun widgetLaunchRequestFrom(intent: Intent?): WidgetLaunchRequest? {
        val targetTab = intent?.getStringExtra(EXTRA_WIDGET_TARGET_TAB)?.takeIf { it.isNotBlank() }
            ?: return null
        return WidgetLaunchRequest(
            targetTab = targetTab,
            assetPlanId = intent.getStringExtra(EXTRA_WIDGET_ASSET_PLAN_ID).orEmpty()
        )
    }
}

sealed class NavTab(val id: String, val labelRes: Int, val icon: ImageVector, val iconSelected: ImageVector) {
    object Cards    : NavTab("cards",    R.string.tab_cards,    Icons.Outlined.CreditCard,    Icons.Filled.CreditCard)
    object Calendar : NavTab("calendar", R.string.tab_calendar, Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth)
    object Piggy    : NavTab("piggy",    R.string.tab_piggy,    Icons.Outlined.Savings,       Icons.Filled.Savings)
    object Data     : NavTab("data",     R.string.tab_data,     Icons.Outlined.BarChart,      Icons.Filled.BarChart)
}

val tabs = listOf(NavTab.Cards, NavTab.Calendar, NavTab.Piggy, NavTab.Data)
private val requiredTabIds = setOf(NavTab.Cards.id, NavTab.Data.id)
fun tabById(id: String): NavTab? = tabs.firstOrNull { it.id == id }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun CardManagerApp(
    vm: MainViewModel,
    launchRequest: WidgetLaunchRequest? = null,
    onLaunchRequestConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    var current by remember { mutableStateOf<NavTab>(NavTab.Cards) }
    var pendingPiggyAssetPlanId by remember { mutableStateOf<String?>(null) }
    var allowHiddenCurrent by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<ReleaseInfo?>(null) }
    val themeMode by vm.themeMode.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }
    val tabOrder by vm.tabOrder.collectAsState()
    val visibleOptionalTabs by vm.visibleOptionalTabs.collectAsState()
    val visibleTabs = remember(tabOrder, visibleOptionalTabs) {
        tabOrder.mapNotNull { tabById(it) }
            .filter { it.id in requiredTabIds || it.id in visibleOptionalTabs }
            .ifEmpty { listOf(NavTab.Cards, NavTab.Data) }
    }

    LaunchedEffect(Unit) {
        val result = UpdateCheckService.check(context, force = false)
        if (result is UpdateCheckResult.Available &&
            UpdateCheckService.shouldPromptAutomatically(context, result.release.versionName)
        ) {
            UpdateCheckService.markAutomaticallyPrompted(context, result.release.versionName)
            availableUpdate = result.release
        }
    }

    LaunchedEffect(visibleTabs, current, allowHiddenCurrent) {
        when {
            current in visibleTabs -> if (allowHiddenCurrent) allowHiddenCurrent = false
            !allowHiddenCurrent -> current = NavTab.Cards
        }
    }

    LaunchedEffect(launchRequest, visibleTabs) {
        val request = launchRequest ?: return@LaunchedEffect
        val target = tabById(request.targetTab) ?: NavTab.Cards
        current = target
        allowHiddenCurrent = target !in visibleTabs
        pendingPiggyAssetPlanId = request.assetPlanId.takeIf {
            target == NavTab.Piggy && it.isNotBlank()
        }
        onLaunchRequestConsumed()
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AnimatedContent(
            targetState = showSettings,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            label = "settings_page_transition",
            transitionSpec = {
                val duration = 260
                if (targetState) {
                    (slideInHorizontally(tween(duration)) { it } + fadeIn(tween(duration))) togetherWith
                        (slideOutHorizontally(tween(duration)) { -it / 4 } + fadeOut(tween(duration)))
                } else {
                    (slideInHorizontally(tween(duration)) { -it / 4 } + fadeIn(tween(duration))) togetherWith
                        (slideOutHorizontally(tween(duration)) { it } + fadeOut(tween(duration)))
                }
            }
        ) { settingsVisible ->
            if (settingsVisible) {
                BackHandler { showSettings = false }
                SettingsScreen(vm) { showSettings = false }
            } else {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    Scaffold(
                        contentWindowInsets = WindowInsets(0),
                        topBar = { AppTopBar(current, isDark) { showSettings = true } },
                        bottomBar = { AppBottomBar(visibleTabs, current) { allowHiddenCurrent = false; current = it } },
                        containerColor = MaterialTheme.colorScheme.background
                    ) { padding ->
                        Box(Modifier.padding(padding).fillMaxSize()) {
                            when (current) {
                                NavTab.Cards    -> CardsScreen(vm)
                                NavTab.Calendar -> CalendarScreen(vm)
                                NavTab.Piggy    -> PiggyScreen(
                                    vm = vm,
                                    openAssetPlanId = pendingPiggyAssetPlanId,
                                    onAssetPlanOpened = { pendingPiggyAssetPlanId = null }
                                )
                                NavTab.Data     -> DataScreen(vm)
                            }
                        }
                    }
                }
            }
        }
    }

    availableUpdate?.let { release ->
        UpdateAvailableDialog(
            release = release,
            onDismiss = { availableUpdate = null },
            onOpenRelease = {
                UpdateCheckService.openReleasePage(context, release.releaseUrl)
                availableUpdate = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(tab: NavTab, isDark: Boolean, onSettings: () -> Unit) {
    val statusPad = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val cs = MaterialTheme.colorScheme

    Surface(Modifier.fillMaxWidth(), color = cs.surface, tonalElevation = 0.dp, shadowElevation = 0.dp) {
        Column(Modifier.fillMaxWidth().padding(top = statusPad)) {
            Row(Modifier.fillMaxWidth().height(48.dp).padding(start = 22.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(tab.labelRes), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
                    color = cs.onSurface, letterSpacing = 0.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = onSettings) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                        .background(cs.surfaceVariant.copy(alpha = if (isDark) 0.8f else 1f)),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Settings, stringResource(R.string.settings),
                            tint = cs.onSurfaceVariant, modifier = Modifier.size(19.dp))
                    }
                }
            }
            HorizontalDivider(color = cs.outline.copy(alpha = 0.18f))
        }
    }
}

@Composable
fun AppBottomBar(visibleTabs: List<NavTab>, current: NavTab, onSelect: (NavTab) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bottomPad = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Surface(Modifier.fillMaxWidth(), color = cs.surface, shadowElevation = 0.dp, tonalElevation = 0.dp) {
        Column(Modifier.padding(bottom = bottomPad.coerceAtLeast(4.dp))) {
            HorizontalDivider(color = cs.outline.copy(alpha = 0.18f))
            NavigationBar(
                modifier = Modifier.height(64.dp),
                containerColor = cs.surface,
                tonalElevation = 0.dp,
                windowInsets = WindowInsets(0)
            ) {
                visibleTabs.forEach { tab ->
                    val selected = current == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { onSelect(tab) },
                        icon = { Icon(if (selected) tab.iconSelected else tab.icon, stringResource(tab.labelRes)) },
                        label = { Text(stringResource(tab.labelRes), fontSize = 11.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = cs.primary,
                            selectedTextColor = cs.primary,
                            indicatorColor = cs.primary.copy(alpha = 0.12f),
                            unselectedIconColor = cs.onSurfaceVariant,
                            unselectedTextColor = cs.onSurfaceVariant
                        )
                    )
                }
            }
        }
    }
}
