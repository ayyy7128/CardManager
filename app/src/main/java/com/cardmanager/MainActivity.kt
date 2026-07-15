package com.cardmanager

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import com.cardmanager.ui.components.PredictiveBackPage
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

private sealed class AppPage {
    data object Home : AppPage()
    data object Settings : AppPage()
    data class Card(val route: CardPageRoute) : AppPage()
    data class Asset(val route: AssetPageRoute) : AppPage()
}

private fun AppPage.depth(): Int = when (this) {
    AppPage.Home -> 0
    AppPage.Settings -> 1
    is AppPage.Card -> when (route) {
        is CardPageRoute.Focus -> 1
        is CardPageRoute.Add, is CardPageRoute.Edit -> 2
    }
    is AppPage.Asset -> when (route) {
        is AssetPageRoute.Detail -> 1
        is AssetPageRoute.Edit -> 2
    }
}

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
    var cardPage by remember { mutableStateOf<CardPageRoute?>(null) }
    var assetPage by remember { mutableStateOf<AssetPageRoute?>(null) }
    var sectionFullscreen by remember { mutableStateOf(false) }
    var bottomBarVisible by remember { mutableStateOf(true) }
    var bottomBarScrollDelta by remember { mutableFloatStateOf(0f) }
    var availableUpdate by remember { mutableStateOf<ReleaseInfo?>(null) }
    val bottomBarScrollThreshold = with(LocalDensity.current) { 64.dp.toPx() }
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

    LaunchedEffect(current) {
        sectionFullscreen = false
        bottomBarVisible = true
        bottomBarScrollDelta = 0f
    }

    val bottomBarNestedScroll = remember(bottomBarScrollThreshold) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source != NestedScrollSource.Drag || consumed.y == 0f) return Offset.Zero
                if (bottomBarScrollDelta != 0f && bottomBarScrollDelta * consumed.y < 0f) {
                    bottomBarScrollDelta = 0f
                }
                bottomBarScrollDelta = (bottomBarScrollDelta + consumed.y).coerceIn(
                    -bottomBarScrollThreshold * 2f,
                    bottomBarScrollThreshold * 2f
                )
                when {
                    bottomBarScrollDelta <= -bottomBarScrollThreshold -> {
                        bottomBarVisible = false
                        bottomBarScrollDelta = 0f
                    }
                    bottomBarScrollDelta >= bottomBarScrollThreshold -> {
                        bottomBarVisible = true
                        bottomBarScrollDelta = 0f
                    }
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(launchRequest, visibleTabs) {
        val request = launchRequest ?: return@LaunchedEffect
        val target = tabById(request.targetTab) ?: NavTab.Cards
        current = target
        cardPage = null
        assetPage = null
        allowHiddenCurrent = target !in visibleTabs
        pendingPiggyAssetPlanId = request.assetPlanId.takeIf {
            target == NavTab.Piggy && it.isNotBlank()
        }
        onLaunchRequestConsumed()
    }

    val appPage = when {
        showSettings -> AppPage.Settings
        cardPage != null -> AppPage.Card(cardPage!!)
        assetPage != null -> AppPage.Asset(assetPage!!)
        else -> AppPage.Home
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AnimatedContent(
            targetState = appPage,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            label = "app_page_transition",
            transitionSpec = {
                val duration = 260
                if (targetState.depth() > initialState.depth()) {
                    (slideInHorizontally(tween(duration)) { it } + fadeIn(tween(duration))) togetherWith
                        (slideOutHorizontally(tween(duration)) { -it / 4 } + fadeOut(tween(duration)))
                } else {
                    (slideInHorizontally(tween(duration)) { -it / 4 } + fadeIn(tween(duration))) togetherWith
                        (slideOutHorizontally(tween(duration)) { it } + fadeOut(tween(duration)))
                }
            }
        ) { page ->
            when (page) {
                AppPage.Settings -> PredictiveBackPage(
                    onBack = { showSettings = false },
                    modifier = Modifier.fillMaxSize()
                ) {
                    SettingsScreen(vm) { showSettings = false }
                }

                is AppPage.Card -> CardPageHost(
                    route = page.route,
                    vm = vm,
                    onRouteChange = { cardPage = it },
                    onClose = { cardPage = null }
                )

                is AppPage.Asset -> AssetPageHost(
                    route = page.route,
                    vm = vm,
                    onRouteChange = { assetPage = it },
                    onClose = { assetPage = null }
                )

                AppPage.Home -> Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .nestedScroll(bottomBarNestedScroll)
                ) {
                    Scaffold(
                        contentWindowInsets = WindowInsets(0),
                        topBar = {
                            if (!sectionFullscreen) AppTopBar(current, isDark) { showSettings = true }
                        },
                        containerColor = MaterialTheme.colorScheme.background
                    ) { padding ->
                        Box(Modifier.padding(padding).fillMaxSize()) {
                            when (current) {
                                NavTab.Cards -> CardsScreen(
                                    vm = vm,
                                    onOpenPage = { cardPage = it },
                                    floatingNavigationVisible = bottomBarVisible
                                )
                                NavTab.Calendar -> CalendarScreen(
                                    vm = vm,
                                    onFullscreenChanged = { sectionFullscreen = it },
                                    onOpenAssetPage = { assetPage = it },
                                    floatingNavigationVisible = bottomBarVisible
                                )
                                NavTab.Piggy    -> PiggyScreen(
                                    vm = vm,
                                    openAssetPlanId = pendingPiggyAssetPlanId,
                                    onAssetPlanOpened = { pendingPiggyAssetPlanId = null },
                                    onOpenAssetPage = { assetPage = it },
                                    floatingNavigationVisible = bottomBarVisible
                                )
                                NavTab.Data     -> DataScreen(vm)
                            }
                        }
                    }
                    AnimatedVisibility(
                        visible = !sectionFullscreen && bottomBarVisible,
                        modifier = Modifier.align(Alignment.BottomCenter).zIndex(20f),
                        enter = slideInVertically(tween(220)) { it + 24 } + fadeIn(tween(180)),
                        exit = slideOutVertically(tween(220)) { it + 24 } + fadeOut(tween(180))
                    ) {
                        AppBottomBar(
                            visibleTabs = visibleTabs,
                            current = current,
                            onSelect = {
                                allowHiddenCurrent = false
                                bottomBarVisible = true
                                current = it
                            }
                        )
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
fun AppBottomBar(
    visibleTabs: List<NavTab>,
    current: NavTab,
    onSelect: (NavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val bottomPad = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val isDarkSurface = cs.background.luminance() < 0.5f
    Box(
        modifier
            .wrapContentWidth()
            .padding(bottom = bottomPad + 14.dp)
    ) {
        val barShape = RoundedCornerShape(30.dp)
        Box(
            Modifier
                .height(68.dp)
                .shadow(12.dp, barShape, clip = false)
                .clip(barShape)
                .background(cs.surface)
                .border(
                    1.dp,
                    if (isDarkSurface) Color.White.copy(alpha = 0.16f)
                    else Color.White.copy(alpha = 0.72f),
                    barShape
                )
        ) {
            Row(
                modifier = Modifier.fillMaxHeight().padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                visibleTabs.forEach { tab ->
                    val selected = current == tab
                    val itemShape = RoundedCornerShape(25.dp)
                    Box(
                        modifier = Modifier
                            .width(76.dp)
                            .fillMaxHeight()
                            .clip(itemShape)
                            .background(
                                if (selected) {
                                    cs.surfaceVariant
                                } else {
                                    Color.Transparent
                                }
                            )
                            .then(
                                if (selected) {
                                    Modifier.border(
                                        1.dp,
                                        if (isDarkSurface) Color.White.copy(alpha = 0.10f)
                                        else Color.White.copy(alpha = 0.58f),
                                        itemShape
                                    )
                                } else Modifier
                            )
                    ) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .clickable { onSelect(tab) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                if (selected) tab.iconSelected else tab.icon,
                                stringResource(tab.labelRes),
                                tint = if (selected) cs.primary else cs.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                stringResource(tab.labelRes),
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) cs.primary else cs.onSurfaceVariant,
                                letterSpacing = 0.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
