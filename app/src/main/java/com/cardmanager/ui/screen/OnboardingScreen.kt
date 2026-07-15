package com.cardmanager.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cardmanager.R
import kotlinx.coroutines.launch

private const val ONBOARDING_PAGE_COUNT = 4

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    onSkip: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { ONBOARDING_PAGE_COUNT })
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme

    BackHandler {
        if (pagerState.currentPage > 0) {
            scope.launch {
                pagerState.animateScrollToPage(
                    pagerState.currentPage - 1,
                    animationSpec = tween(240)
                )
            }
        } else {
            onSkip()
        }
    }

    Surface(Modifier.fillMaxSize(), color = cs.background) {
        Box(Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondBoundsPageCount = 1
            ) { page ->
                when (page) {
                    0 -> OnboardingWelcomePage()
                    1 -> OnboardingCardsPage()
                    2 -> OnboardingPlanningPage()
                    else -> OnboardingLocalFirstPage()
                }
            }

            TextButton(
                onClick = onSkip,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 6.dp, end = 14.dp)
            ) {
                Text(stringResource(R.string.onboarding_skip), fontWeight = FontWeight.SemiBold)
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (pagerState.currentPage > 0) {
                    IconButton(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    pagerState.currentPage - 1,
                                    animationSpec = tween(240)
                                )
                            }
                        }
                    ) {
                        Icon(Icons.Default.ChevronLeft, stringResource(R.string.onboarding_previous))
                    }
                } else {
                    Spacer(Modifier.size(48.dp))
                }

                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(ONBOARDING_PAGE_COUNT) { index ->
                        Box(
                            Modifier
                                .padding(horizontal = 3.dp)
                                .size(if (index == pagerState.currentPage) 18.dp else 7.dp, 7.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index == pagerState.currentPage) cs.primary
                                    else cs.outline.copy(alpha = 0.28f)
                                )
                        )
                    }
                }

                Button(
                    onClick = {
                        if (pagerState.currentPage == ONBOARDING_PAGE_COUNT - 1) {
                            onFinish()
                        } else {
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    pagerState.currentPage + 1,
                                    animationSpec = tween(240)
                                )
                            }
                        }
                    },
                    modifier = Modifier.widthIn(min = 116.dp).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = cs.primary)
                ) {
                    Text(
                        stringResource(
                            if (pagerState.currentPage == ONBOARDING_PAGE_COUNT - 1) {
                                R.string.onboarding_start
                            } else {
                                R.string.onboarding_next
                            }
                        ),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingWelcomePage() {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White,
                        Color(0xFFF4FAFF),
                        Color(0xFFD9ECFF),
                        Color(0xFFAED4FF)
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        val compact = maxHeight < 640.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 88.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier
                    .size(if (compact) 132.dp else 164.dp)
                    .clip(RoundedCornerShape(if (compact) 29.dp else 36.dp)),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.height(if (compact) 24.dp else 34.dp))
            Text(
                stringResource(R.string.onboarding_welcome_title),
                color = Color(0xFF10213F),
                fontSize = if (compact) 26.sp else 30.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                letterSpacing = 0.sp
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.onboarding_welcome_body),
                color = Color(0xFF425672),
                fontSize = 15.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
                letterSpacing = 0.sp
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.version_1),
                color = Color(0xFF637691),
                fontSize = 12.sp,
                letterSpacing = 0.sp
            )
        }
    }
}

@Composable
private fun OnboardingCardsPage() {
    var mode by rememberSaveable { mutableStateOf("wallet") }
    OnboardingPageLayout(
        icon = Icons.Default.CreditCard,
        title = stringResource(R.string.onboarding_cards_title),
        body = stringResource(R.string.onboarding_cards_body)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = 430.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            OnboardingSegmentedControl(
                options = listOf(
                    "wallet" to stringResource(R.string.card_view_wallet),
                    "gallery" to stringResource(R.string.card_view_gallery),
                    "list" to stringResource(R.string.card_view_list)
                ),
                selected = mode,
                onSelected = { mode = it }
            )
            Crossfade(mode, animationSpec = tween(180), label = "onboarding_card_mode") { selected ->
                when (selected) {
                    "gallery" -> OnboardingGalleryPreview()
                    "list" -> OnboardingListPreview()
                    else -> OnboardingWalletPreview()
                }
            }
        }
    }
}

@Composable
private fun OnboardingPlanningPage() {
    var mode by rememberSaveable { mutableStateOf("calendar") }
    OnboardingPageLayout(
        icon = Icons.Default.CalendarMonth,
        title = stringResource(R.string.onboarding_planning_title),
        body = stringResource(R.string.onboarding_planning_body)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = 430.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            OnboardingSegmentedControl(
                options = listOf(
                    "calendar" to stringResource(R.string.tab_calendar),
                    "vault" to stringResource(R.string.tab_piggy)
                ),
                selected = mode,
                onSelected = { mode = it }
            )
            Crossfade(mode, animationSpec = tween(180), label = "onboarding_planning_mode") { selected ->
                if (selected == "vault") OnboardingVaultPreview() else OnboardingCalendarPreview()
            }
        }
    }
}

@Composable
private fun OnboardingLocalFirstPage() {
    OnboardingPageLayout(
        icon = Icons.Default.Lock,
        title = stringResource(R.string.onboarding_local_title),
        body = stringResource(R.string.onboarding_local_body)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = 450.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OnboardingFeatureRow(
                Icons.Default.Lock,
                stringResource(R.string.onboarding_local_storage_title),
                stringResource(R.string.onboarding_local_storage_body)
            )
            OnboardingFeatureRow(
                Icons.Default.Backup,
                stringResource(R.string.onboarding_backup_title),
                stringResource(R.string.onboarding_backup_body)
            )
            OnboardingFeatureRow(
                Icons.Default.BarChart,
                stringResource(R.string.onboarding_insights_title),
                stringResource(R.string.onboarding_insights_body)
            )
        }
    }
}

@Composable
private fun OnboardingPageLayout(
    icon: ImageVector,
    title: String,
    body: String,
    preview: @Composable () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        val compact = maxHeight < 640.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 24.dp,
                    end = 24.dp,
                    top = if (compact) 66.dp else 84.dp,
                    bottom = if (compact) 84.dp else 102.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(if (compact) 48.dp else 56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(cs.primary.copy(alpha = 0.11f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = cs.primary, modifier = Modifier.size(if (compact) 24.dp else 28.dp))
            }
            Spacer(Modifier.height(if (compact) 14.dp else 18.dp))
            Text(
                title,
                color = cs.onBackground,
                fontSize = if (compact) 23.sp else 27.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                letterSpacing = 0.sp
            )
            Spacer(Modifier.height(9.dp))
            Text(
                body,
                color = cs.onSurfaceVariant,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                letterSpacing = 0.sp
            )
            Spacer(Modifier.height(if (compact) 16.dp else 26.dp))
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                preview()
            }
        }
    }
}

@Composable
private fun OnboardingSegmentedControl(
    options: List<Pair<String, String>>,
    selected: String,
    onSelected: (String) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cs.surfaceVariant.copy(alpha = 0.55f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEach { (id, label) ->
            val active = selected == id
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clickable { onSelected(id) },
                shape = RoundedCornerShape(11.dp),
                color = if (active) cs.surface else Color.Transparent,
                shadowElevation = if (active) 1.dp else 0.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        color = if (active) cs.primary else cs.onSurfaceVariant,
                        fontSize = 13.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        letterSpacing = 0.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingWalletPreview() {
    val labels = listOf(
        stringResource(R.string.onboarding_sample_daily),
        stringResource(R.string.onboarding_sample_travel),
        stringResource(R.string.onboarding_sample_backup)
    )
    val colors = listOf(Color(0xFF2F7EF7), Color(0xFF20A58C), Color(0xFFEE795E))
    Box(Modifier.fillMaxWidth().height(250.dp), contentAlignment = Alignment.TopCenter) {
        labels.forEachIndexed { index, label ->
            SampleBankCard(
                label = label,
                tail = listOf("4821", "7306", "1598")[index],
                color = colors[index],
                modifier = Modifier
                    .fillMaxWidth(0.86f)
                    .aspectRatio(1.82f)
                    .offset(y = (index * 34).dp)
            )
        }
    }
}

@Composable
private fun OnboardingGalleryPreview() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SampleBankCard(
            stringResource(R.string.onboarding_sample_daily),
            "4821",
            Color(0xFF2F7EF7),
            Modifier.weight(1f).aspectRatio(1.36f)
        )
        SampleBankCard(
            stringResource(R.string.onboarding_sample_travel),
            "7306",
            Color(0xFF20A58C),
            Modifier.weight(1f).aspectRatio(1.36f)
        )
    }
}

@Composable
private fun OnboardingListPreview() {
    val cs = MaterialTheme.colorScheme
    val rows = listOf(
        stringResource(R.string.onboarding_sample_daily) to "4821",
        stringResource(R.string.onboarding_sample_travel) to "7306",
        stringResource(R.string.onboarding_sample_backup) to "1598"
    )
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEachIndexed { index, (label, tail) ->
            Surface(shape = RoundedCornerShape(12.dp), color = cs.surface) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(listOf(Color(0xFF2F7EF7), Color(0xFF20A58C), Color(0xFFEE795E))[index]),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CreditCard, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Text(label, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text("*$tail", color = cs.onSurfaceVariant, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun SampleBankCard(
    label: String,
    tail: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = color, shadowElevation = 3.dp) {
        Column(
            Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(Icons.Default.CreditCard, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("****  ****", color = Color.White.copy(alpha = 0.78f), fontSize = 12.sp)
                Text("*$tail", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun OnboardingCalendarPreview() {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            (12..18).forEach { day ->
                val selected = day == 15 || day == 18
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (selected) cs.primary else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        day.toString(),
                        color = if (selected) cs.onPrimary else cs.onSurfaceVariant,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp
                    )
                }
            }
        }
        OnboardingTaskRow(
            Icons.Default.CreditCard,
            stringResource(R.string.onboarding_task_repayment),
            stringResource(R.string.onboarding_task_due_today),
            cs.primary
        )
        OnboardingTaskRow(
            Icons.Default.Savings,
            stringResource(R.string.onboarding_task_investment),
            stringResource(R.string.onboarding_task_recurring),
            Color(0xFFB67B00)
        )
    }
}

@Composable
private fun OnboardingTaskRow(icon: ImageVector, title: String, subtitle: String, color: Color) {
    val cs = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(12.dp), color = cs.surface) {
        Row(
            Modifier.fillMaxWidth().padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(19.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(subtitle, color = cs.onSurfaceVariant, fontSize = 12.sp)
            }
            Icon(Icons.Default.CheckCircle, null, tint = color, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun OnboardingVaultPreview() {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.onboarding_vault_total), color = cs.onSurfaceVariant, fontSize = 12.sp)
                Text("\u00A5 28,680.00", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.sp)
            }
            Icon(Icons.Default.AccountBalanceWallet, null, tint = cs.primary, modifier = Modifier.size(30.dp))
        }
        OnboardingVaultRow(stringResource(R.string.onboarding_project_fund), "\u00A5 18,200", 0.64f, cs.primary)
        OnboardingVaultRow(stringResource(R.string.onboarding_project_savings), "\u00A5 10,480", 0.42f, Color(0xFF20A58C))
    }
}

@Composable
private fun OnboardingVaultRow(label: String, amount: String, progress: Float, color: Color) {
    val cs = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(12.dp), color = cs.surface) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(label, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(amount, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(cs.surfaceVariant)) {
                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().clip(CircleShape).background(color))
            }
        }
    }
}

@Composable
private fun OnboardingFeatureRow(icon: ImageVector, title: String, body: String) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(cs.primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = cs.primary, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp)
            Text(body, fontSize = 12.sp, lineHeight = 17.sp, color = cs.onSurfaceVariant, letterSpacing = 0.sp)
        }
    }
}
