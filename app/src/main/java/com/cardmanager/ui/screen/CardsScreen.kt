package com.cardmanager.ui.screen

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.tech.IsoDep
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cardmanager.R
import com.cardmanager.data.AssetPlan
import com.cardmanager.data.BuiltinBank
import com.cardmanager.data.BuiltinBankCatalog
import com.cardmanager.data.Card
import com.cardmanager.data.CardGroup
import com.cardmanager.data.CardResourceItem
import com.cardmanager.data.CardResourcePackManager
import com.cardmanager.data.ImageStore
import com.cardmanager.data.PiggyEntry
import com.cardmanager.data.Task
import com.cardmanager.nfc.EmvCardReader
import com.cardmanager.nfc.EmvProbeResult
import com.cardmanager.ui.components.EmptyState
import com.cardmanager.ui.components.ItemShape
import com.cardmanager.ui.components.bottomSpacer
import com.cardmanager.ui.components.responsiveDialogWidth
import com.cardmanager.ui.components.screenPaddingFor
import com.cardmanager.ui.theme.*
import com.cardmanager.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat

// ── 异步加载图片 ──────────────────────────────────────────
@Composable
fun rememberBitmap(path: String): Bitmap? {
    return rememberBitmap(path, 0, 0)
}

@Composable
fun rememberBitmap(path: String, refreshKey: Int, maxDimension: Int = 0): Bitmap? {
    val cached = remember(path, refreshKey, maxDimension) { ImageStore.peek(path, maxDimension) }
    val bmp: Bitmap? by produceState<Bitmap?>(cached, path, refreshKey, maxDimension) {
        value = if (path.isEmpty()) null else withContext(Dispatchers.IO) { ImageStore.load(path, maxDimension) }
    }
    return bmp
}

@Composable
private fun rememberAssetBitmap(assetPath: String, maxDimension: Int = 0): Bitmap? {
    val context = LocalContext.current.applicationContext
    val cached = remember(assetPath, maxDimension) { ImageStore.peekAsset(assetPath, maxDimension) }
    val bitmap: Bitmap? by produceState<Bitmap?>(cached, assetPath, maxDimension) {
        value = if (assetPath.isBlank()) null else withContext(Dispatchers.IO) {
            ImageStore.loadAsset(context, assetPath, maxDimension)
        }
    }
    return bitmap
}

@Composable
private fun rememberBankLogo(
    bank: String,
    customPath: String,
    refreshKey: Int,
    maxDimension: Int
): Bitmap? {
    val context = LocalContext.current.applicationContext
    val assetPath = remember(bank, context) { BuiltinBankCatalog.logoAsset(context, bank) }
    val cached = remember(bank, customPath, refreshKey, maxDimension, assetPath) {
        ImageStore.peek(customPath, maxDimension)
            ?: ImageStore.peekAsset(assetPath, maxDimension)
    }
    val bitmap: Bitmap? by produceState<Bitmap?>(
        cached,
        bank,
        customPath,
        refreshKey,
        maxDimension,
        assetPath
    ) {
        value = withContext(Dispatchers.IO) {
            customPath.takeIf { it.isNotBlank() }
                ?.let { ImageStore.load(it, maxDimension) }
                ?: ImageStore.loadAsset(context, assetPath, maxDimension)
        }
    }
    return bitmap
}

@Composable
private fun rememberDisplayBitmap(source: Bitmap?, rotateLandscape: Boolean): Bitmap? =
    remember(source, rotateLandscape) {
        if (source == null || !rotateLandscape) {
            source
        } else {
            val matrix = Matrix().apply { postRotate(-90f) }
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        }
    }

private data class CardSection(
    val group: CardGroup,
    val cards: List<Card>,
    val isVirtual: Boolean = false
)

private sealed class CardOverlay {
    data class Add(val groupId: String) : CardOverlay()
    data class Edit(val card: Card) : CardOverlay()
    data class Focus(val card: Card) : CardOverlay()
}

@Composable
fun CardsScreen(vm: MainViewModel) {
    val groups   by vm.groups.collectAsState()
    val cards    by vm.cards.collectAsState()
    val imageVersion by vm.imageVersion.collectAsState()
    val cardsPerRowPortrait by vm.cardsPerRowPortrait.collectAsState()
    val cardsPerRowLandscape by vm.cardsPerRowLandscape.collectAsState()
    val ungroupedMode by vm.ungroupedMode.collectAsState()
    val cardViewMode by vm.cardViewMode.collectAsState()
    val tasks by vm.tasks.collectAsState()
    val piggyEntries by vm.piggyEntries.collectAsState()
    val assetPlans by vm.assetPlans.collectAsState()
    val walletMode = cardViewMode == "wallet"
    val galleryMode = cardViewMode == "gallery"
    val listMode = cardViewMode == "list"

    var showAddGroup  by remember { mutableStateOf(false) }
    var editingGroup  by remember { mutableStateOf<CardGroup?>(null) }
    var showAddCard   by remember { mutableStateOf<String?>(null) }
    var editingCard   by remember { mutableStateOf<Card?>(null) }
    var focusedCardId by remember { mutableStateOf<String?>(null) }
    var expandedWalletStacks by remember { mutableStateOf(emptySet<String>()) }
    var deletingGroup by remember { mutableStateOf<CardGroup?>(null) }
    var deletingCard  by remember { mutableStateOf<Card?>(null) }
    var showCreateMenu by remember { mutableStateOf(false) }
    var showFilter    by remember { mutableStateOf(false) }
    var searchQuery   by remember { mutableStateOf("") }
    var showSearch    by remember { mutableStateOf(false) }

    var filterNetworks by remember { mutableStateOf(emptySet<String>()) }
    var filterCurrency by remember { mutableStateOf("") }
    var filterStatus   by remember { mutableStateOf("") }
    val hasFilter = filterNetworks.isNotEmpty() || filterCurrency.isNotEmpty() || filterStatus.isNotEmpty()

    fun cardMatches(card: Card): Boolean {
        val q = searchQuery.trim()
        val searchOk = q.isEmpty() || listOf(
            card.bank,
            card.cardTypeName,
            card.network,
            card.currency,
            card.tail,
            card.note,
            card.cardCategory
        ).any { it.contains(q, ignoreCase = true) }
        if (!searchOk) return false
        val networkOk = if (filterNetworks.isEmpty()) true else {
            val wantAccount = "__account__" in filterNetworks
            val nets = filterNetworks - "__account__"
            (wantAccount && card.noCard) || (nets.isNotEmpty() && card.network in nets && !card.noCard)
        }
        val currencyOk = filterCurrency.isEmpty() || card.currency == filterCurrency
        val statusOk = filterStatus.isEmpty() || card.status == filterStatus
        return networkOk && currencyOk && statusOk
    }

    fun orderedCardsForGroup(groupId: String) = cards
        .filter { it.groupId == groupId }
        .sortedWith(compareBy<Card> { it.sortOrder }.thenBy { it.id })

    fun filteredCards(groupId: String) = orderedCardsForGroup(groupId).filter { cardMatches(it) }

    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val hasSearch = searchQuery.trim().isNotEmpty()
    LaunchedEffect(showSearch) {
        if (!showSearch) searchQuery = ""
    }
    val accountLabel = stringResource(R.string.account)
    val normalLabel = stringResource(R.string.normal)
    val frozenLabel = stringResource(R.string.frozen)
    val pendingLabel = stringResource(R.string.pending)
    val cancelledLabel = stringResource(R.string.cancelled)
    val unionPayLabel = stringResource(R.string.network_unionpay)
    val otherNetworkLabel = stringResource(R.string.network_other)
    fun networkLabel(network: String) = when (network) {
        "银联" -> unionPayLabel
        "其他" -> otherNetworkLabel
        else -> network
    }
    val ungroupedGroupName = stringResource(R.string.ungrouped_cards)
    val realGroupIds = groups.map { it.id }.toSet()
    val ungroupedCards = cards
        .filter { it.groupId.isBlank() || it.groupId !in realGroupIds }
        .sortedWith(compareBy<Card> { it.sortOrder }.thenBy { it.id })
    val groupedSections = buildList {
        groups.forEach { group ->
            val groupCards = filteredCards(group.id)
            if (!hasSearch && !hasFilter || groupCards.isNotEmpty()) add(CardSection(group, groupCards))
        }
        val filteredUngroupedCards = ungroupedCards.filter { cardMatches(it) }
        if (filteredUngroupedCards.isNotEmpty()) {
            add(CardSection(CardGroup("", ungroupedGroupName, "💳", true, Int.MAX_VALUE), filteredUngroupedCards, true))
        }
    }
    val flatCards = buildList {
        groups.forEach { group -> addAll(orderedCardsForGroup(group.id)) }
        addAll(ungroupedCards)
    }
        .filter { cardMatches(it) }
        .distinctBy { it.id }
    val focusedCard = focusedCardId?.let { id -> cards.firstOrNull { it.id == id } }

    LaunchedEffect(focusedCardId, cards) {
        if (focusedCardId != null && focusedCard == null) focusedCardId = null
    }

    LaunchedEffect(cards, imageVersion) {
        val paths = cards
            .flatMap { listOf(it.logoImagePath, it.bankLogoPath) }
            .filter { it.isNotBlank() }
            .distinct()
            .take(48)
        withContext(Dispatchers.IO) {
            paths.forEach { path -> ImageStore.load(path, 720) }
        }
    }

    val activeOverlay = when {
        showAddCard != null -> CardOverlay.Add(showAddCard.orEmpty())
        editingCard != null -> CardOverlay.Edit(editingCard!!)
        focusedCard != null -> CardOverlay.Focus(focusedCard)
        else -> null
    }

    fun dismissActiveOverlay() {
        showAddCard = null
        editingCard = null
        focusedCardId = null
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val pagePadding = screenPaddingFor(maxWidth)
        val galleryColumnCount = if (maxWidth > maxHeight) cardsPerRowLandscape else cardsPerRowPortrait

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = pagePadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // ── 工具栏：筛选按钮 + 视图切换 ─────────────
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AnimatedVisibility(
                        visible = showSearch,
                        enter = slideInVertically(
                            animationSpec = tween(durationMillis = 180),
                            initialOffsetY = { -it / 3 }
                        ) + fadeIn(animationSpec = tween(durationMillis = 140)),
                        exit = slideOutVertically(
                            animationSpec = tween(durationMillis = 150),
                            targetOffsetY = { -it / 3 }
                        ) + fadeOut(animationSpec = tween(durationMillis = 120))
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            leadingIcon = {
                                Icon(Icons.Default.Search, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(34.dp)) {
                                        Icon(Icons.Default.Close, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            placeholder = {
                                Text(
                                    stringResource(R.string.search_cards_hint),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = cs.surface,
                                unfocusedContainerColor = cs.surface,
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            )
                        )
                    }

                        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                    // 筛选按钮（胶囊样式，有筛选时变色）
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (hasFilter) cs.primary.copy(.14f) else cs.surfaceVariant,
                        modifier = Modifier.clickable { showFilter = true }
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Icon(Icons.Default.FilterList, null,
                                tint = if (hasFilter) cs.primary else cs.onSurfaceVariant,
                                modifier = Modifier.size(15.dp))
                            Text("${stringResource(R.string.filter)}${if (hasFilter) " ·" else ""}",
                                fontSize = 12.sp,
                                color = if (hasFilter) cs.primary else cs.onSurfaceVariant,
                                maxLines = 1)
                            if (hasFilter) {
                                val tags = buildList {
                                    when (filterNetworks.size) {
                                        1 -> { val n = filterNetworks.first(); add(if (n == "__account__") accountLabel else networkLabel(n)) }
                                        in 2..Int.MAX_VALUE -> add(ctx.resources.getString(R.string.network_count, filterNetworks.size))
                                    }
                                    if (filterCurrency.isNotEmpty()) add(filterCurrency)
                                    val sl = when(filterStatus) {
                                        "active" -> normalLabel
                                        "frozen" -> frozenLabel
                                        "cancelled" -> cancelledLabel
                                        "pending" -> pendingLabel
                                        else -> ""
                                    }
                                    if (sl.isNotEmpty()) add(sl)
                                }
                                Text(tags.joinToString(" · "), fontSize = 11.sp,
                                    color = cs.primary, fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    if (hasFilter) {
                        IconButton(onClick = { filterNetworks = emptySet(); filterCurrency=""; filterStatus="" },
                            modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.Close, null, tint = ColorRed, modifier = Modifier.size(14.dp))
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (showSearch || hasSearch) cs.primary.copy(.14f) else cs.surfaceVariant,
                        modifier = Modifier.clickable { showSearch = !showSearch }
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                if (showSearch || hasSearch) Icons.Default.Close else Icons.Default.Search,
                                null,
                                tint = if (showSearch || hasSearch) cs.primary else cs.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                stringResource(R.string.search),
                                fontSize = 12.sp,
                                color = if (showSearch || hasSearch) cs.primary else cs.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    // 视图切换
                    Row(Modifier.clip(RoundedCornerShape(8.dp)).background(cs.surfaceVariant)) {
                        ViewToggleBtn(Icons.Default.CreditCard, walletMode) { vm.setCardViewMode("wallet") }
                        ViewToggleBtn(Icons.Default.GridView, galleryMode) { vm.setCardViewMode("gallery") }
                        ViewToggleBtn(Icons.Default.ViewList, listMode) { vm.setCardViewMode("list") }
                    }
                        }
                    }
            }

            // ── 分组 + 卡片 ──────────────────────────────
            if (ungroupedMode) {
                if (walletMode) {
                    item(key = "flat_wallet") {
                        val stackKey = "flat"
                        CardWalletStack(
                            cards = flatCards,
                            expanded = true,
                            imageVersion = imageVersion,
                            onToggle = {
                                expandedWalletStacks = if (stackKey in expandedWalletStacks) {
                                    expandedWalletStacks - stackKey
                                } else {
                                    expandedWalletStacks + stackKey
                                }
                            },
                            onOpenCard = { focusedCardId = it.id }
                        )
                    }
                } else if (galleryMode) {
                    items(flatCards.chunked(galleryColumnCount),
                        key = { chunk -> "flat_gal_" + chunk.joinToString { it.id } }) { row ->
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { card ->
                                CardGalleryItem(card,
                                    onEdit = { focusedCardId = card.id },
                                    imageVersion = imageVersion,
                                    modifier = Modifier.weight(1f),
                                    columnsInRow = galleryColumnCount)
                            }
                            repeat(galleryColumnCount - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                } else {
                    items(flatCards, key = { "flat_lst_${it.id}" }) { card ->
                        CardListItem(card,
                            onEdit = { focusedCardId = card.id },
                            onMoveUp = { vm.moveCardUp(card) },
                            onMoveDown = { vm.moveCardDown(card) },
                            imageVersion = imageVersion)
                    }
                }
            } else {
                groupedSections.forEach { section ->
                    val group = section.group
                    val groupCards = section.cards

                    item(key = "grp_${group.id.ifBlank { "ungrouped" }}") {
                        val headerModifier = Modifier.fillMaxWidth()
                            .clip(ItemShape)
                            .background(cs.surface)
                            .border(1.dp, cs.outline.copy(alpha = 0.18f), ItemShape)
                            .then(if (section.isVirtual) Modifier else Modifier.clickable { vm.toggleGroupOpen(group) })
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                        Row(headerModifier, verticalAlignment = Alignment.CenterVertically) {
                            val iconText = group.icon.ifBlank { group.name.take(1) }
                            Box(Modifier.size(28.dp).clip(RoundedCornerShape(7.dp))
                                .background(cs.primary.copy(.12f)),
                                contentAlignment = Alignment.Center) {
                                Text(iconText, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                    color = cs.primary)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(group.name, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                                color = cs.onSurface, modifier = Modifier.weight(1f),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${groupCards.size}", fontSize = 11.sp, color = cs.onSurfaceVariant)
                            Spacer(Modifier.width(4.dp))
                            if (!section.isVirtual) {
                                Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                                    IconButton(onClick = { vm.moveGroupUp(group) },
                                        modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.KeyboardArrowUp, null,
                                            tint = cs.onSurfaceVariant.copy(.45f), modifier = Modifier.size(14.dp))
                                    }
                                    IconButton(onClick = { vm.moveGroupDown(group) },
                                        modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.KeyboardArrowDown, null,
                                            tint = cs.onSurfaceVariant.copy(.45f), modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                            if (!section.isVirtual) {
                                IconButton(onClick = { editingGroup = group },
                                    modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Edit, null,
                                        tint = cs.onSurfaceVariant, modifier = Modifier.size(14.dp))
                                }
                                Icon(if (group.isOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    null, tint = cs.onSurfaceVariant.copy(.5f), modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    if ((group.isOpen || section.isVirtual) && groupCards.isNotEmpty()) {
                        if (walletMode) {
                            item(key = "wallet_${group.id.ifBlank { "ungrouped" }}") {
                                val stackKey = group.id.ifBlank { "ungrouped" }
                                CardWalletStack(
                                    cards = groupCards,
                                    expanded = section.isVirtual || stackKey in expandedWalletStacks,
                                    imageVersion = imageVersion,
                                    onToggle = {
                                        expandedWalletStacks = if (stackKey in expandedWalletStacks) {
                                            expandedWalletStacks - stackKey
                                        } else {
                                            expandedWalletStacks + stackKey
                                        }
                                    },
                                    onOpenCard = { focusedCardId = it.id }
                                )
                            }
                        } else if (galleryMode) {
                            items(groupCards.chunked(galleryColumnCount),
                                key = { chunk -> "gal_" + chunk.joinToString { it.id } }) { row ->
                                Row(Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    row.forEach { card ->
                                        CardGalleryItem(card,
                                            onEdit = { focusedCardId = card.id },
                                            imageVersion = imageVersion,
                                            modifier = Modifier.weight(1f),
                                            columnsInRow = galleryColumnCount)
                                    }
                                    repeat(galleryColumnCount - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        } else {
                            items(groupCards, key = { "lst_${it.id}" }) { card ->
                                CardListItem(card,
                                    onEdit = { focusedCardId = card.id },
                                    onMoveUp = { vm.moveCardUp(card) },
                                    onMoveDown = { vm.moveCardDown(card) },
                                    imageVersion = imageVersion)
                            }
                        }
                        item(key = "gap_${group.id.ifBlank { "ungrouped" }}") { Spacer(Modifier.height(6.dp)) }
                    }
                }
            }

            if (cards.isEmpty()) {
                item { EmptyState(Icons.Default.CreditCard, stringResource(R.string.no_cards_title), stringResource(R.string.no_cards_body)) }
            } else if ((ungroupedMode && flatCards.isEmpty()) || (!ungroupedMode && groupedSections.isEmpty())) {
                item { EmptyState(Icons.Default.SearchOff, stringResource(R.string.no_matching_cards_title), stringResource(R.string.no_matching_cards_body)) }
            }
            bottomSpacer()
        }

        val createMenuRotation by animateFloatAsState(
            targetValue = if (showCreateMenu) 45f else 0f,
            animationSpec = tween(durationMillis = 180),
            label = "createMenuRotation"
        )
        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AnimatedVisibility(
                visible = showCreateMenu,
                enter = slideInVertically(
                    animationSpec = tween(durationMillis = 180),
                    initialOffsetY = { it / 2 }
                ) + fadeIn(animationSpec = tween(durationMillis = 140)),
                exit = slideOutVertically(
                    animationSpec = tween(durationMillis = 160),
                    targetOffsetY = { it / 2 }
                ) + fadeOut(animationSpec = tween(durationMillis = 120))
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CreateMenuAction(
                        label = stringResource(R.string.add_card),
                        icon = Icons.Default.AddCard
                    ) {
                        showAddCard = ""
                        showCreateMenu = false
                    }
                    CreateMenuAction(
                        label = stringResource(R.string.add_group),
                        icon = Icons.Default.FolderOpen
                    ) {
                        showAddGroup = true
                        showCreateMenu = false
                    }
                }
            }
            FloatingActionButton(
                onClick = { showCreateMenu = !showCreateMenu },
                shape = RoundedCornerShape(16.dp),
                containerColor = cs.primary,
                contentColor = cs.onPrimary
            ) {
                Icon(
                    Icons.Default.Add,
                    stringResource(if (showCreateMenu) R.string.close else R.string.add_card),
                    modifier = Modifier.rotate(createMenuRotation)
                )
            }
        }
    }
    }

    // 筛选弹窗
    if (showFilter) {
        FilterDialog(
            allNetworks = cards.filter { !it.noCard }.map { it.network }
                .filter { it.isNotEmpty() && it != accountLabel }.distinct().sorted(),
            filterNetworks = filterNetworks,
            filterCurrency = filterCurrency,
            filterStatus = filterStatus,
            networkLabel = { networkLabel(it) },
            onNetworkToggle = { n -> filterNetworks = if (n in filterNetworks) filterNetworks - n else filterNetworks + n },
            onCurrencyChange = { filterCurrency = it },
            onStatusChange = { filterStatus = it },
            onClear = { filterNetworks = emptySet(); filterCurrency = ""; filterStatus = "" },
            onDismiss = { showFilter = false }
        )
    }

    // Dialogs
    if (showAddGroup) GroupDialog(onDismiss = { showAddGroup = false }) { n, ic -> vm.addGroup(n, ic); showAddGroup = false }
    editingGroup?.let { g ->
        GroupDialog(initial = g, onDismiss = { editingGroup = null },
            onDelete = { deletingGroup = g; editingGroup = null }) { n, ic ->
            vm.updateGroup(g.copy(name = n, icon = ic)); editingGroup = null }
    }
    deletingGroup?.let { group ->
        AlertDialog(
            onDismissRequest = { deletingGroup = null },
            title = { Text(stringResource(R.string.delete_group_title)) },
            text = { Text(stringResource(R.string.delete_group_body)) },
            confirmButton = {
                Button(
                    onClick = { vm.deleteGroup(group); deletingGroup = null },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorRed)
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deletingGroup = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    deletingCard?.let { card ->
        AlertDialog(
            onDismissRequest = { deletingCard = null },
            title = { Text(stringResource(R.string.delete_card_title)) },
            text = { Text(stringResource(R.string.delete_card_body)) },
            confirmButton = {
                Button(
                    onClick = { vm.deleteCard(card); deletingCard = null },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorRed)
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deletingCard = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    activeOverlay?.let { overlayState ->
        Dialog(
            onDismissRequest = { dismissActiveOverlay() },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            AnimatedContent(
                targetState = overlayState,
                modifier = Modifier.fillMaxSize(),
                label = "card_overlay_transition",
                transitionSpec = {
                    val duration = 260
                    (slideInHorizontally(tween(duration)) { it } + fadeIn(tween(duration))) togetherWith
                        (slideOutHorizontally(tween(duration)) { -it / 4 } + fadeOut(tween(duration)))
                }
            ) { overlay ->
                when (overlay) {
                    is CardOverlay.Add -> CardDialog(
                        groups = groups,
                        selectedGroupId = overlay.groupId,
                        onDismiss = { showAddCard = null },
                        asPage = true
                    ) { d ->
                        vm.addCard(d.groupId, d.bank, d.network, d.currency, d.tail, d.note,
                            d.status, d.isVirtual, d.noCard, "", d.logoImagePath, d.bankLogoPath,
                            d.cardTypeName, d.expiryDate, d.cardCategory, d.imageOrientation,
                            d.creditLimit, d.billingDay, d.repaymentDay)
                        showAddCard = null
                    }

                    is CardOverlay.Edit -> {
                        val card = overlay.card
                        CardDialog(
                            initial = card,
                            groups = groups,
                            onDismiss = { editingCard = null },
                            onDelete = { deletingCard = card; editingCard = null; focusedCardId = null },
                            asPage = true
                        ) { d ->
                            val movedToNewGroup = d.groupId != card.groupId
                            val nextOrder = if (movedToNewGroup) cards.count { it.groupId == d.groupId } else card.sortOrder
                            vm.updateCard(card.copy(groupId = d.groupId, bank = d.bank, network = d.network, currency = d.currency,
                                tail = d.tail, note = d.note, status = d.status,
                                isVirtual = d.isVirtual, noCard = d.noCard, logoImagePath = d.logoImagePath,
                                bankLogoPath = d.bankLogoPath, cardTypeName = d.cardTypeName,
                                expiryDate = d.expiryDate, cardCategory = d.cardCategory,
                                sortOrder = nextOrder, imageOrientation = d.imageOrientation,
                                creditLimit = d.creditLimit, billingDay = d.billingDay,
                                repaymentDay = d.repaymentDay))
                            editingCard = null
                        }
                    }

                    is CardOverlay.Focus -> CardFocusPage(
                        card = overlay.card,
                        groups = groups,
                        tasks = tasks,
                        piggyEntries = piggyEntries,
                        assetPlans = assetPlans,
                        imageVersion = imageVersion,
                        onBack = { focusedCardId = null },
                        onEdit = { editingCard = overlay.card }
                    )
                }
            }
        }
    }
}

@Composable
private fun ViewToggleBtn(icon: androidx.compose.ui.graphics.vector.ImageVector,
                          selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.size(32.dp).background(if (selected) cs.primary else Color.Transparent)
        .clickable { onClick() }, contentAlignment = Alignment.Center) {
        Icon(icon, null,
            tint = if (selected) Color.White else cs.onSurfaceVariant,
            modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun CreateMenuAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = cs.surface,
            tonalElevation = 2.dp,
            shadowElevation = 4.dp
        ) {
            Text(
                label,
                color = cs.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = cs.primary,
            contentColor = cs.onPrimary
        ) {
            Icon(icon, label, modifier = Modifier.size(20.dp))
        }
    }
}

// ══════════════════════════════════════════════════════════
// 筛选弹窗
// ══════════════════════════════════════════════════════════
@Composable
private fun CardWalletStack(
    cards: List<Card>,
    expanded: Boolean,
    imageVersion: Int,
    onToggle: () -> Unit,
    onOpenCard: (Card) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    if (cards.isEmpty()) {
        Surface(shape = ItemShape, color = cs.surface, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.card_wallet_empty),
                fontSize = 13.sp,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(18.dp)
            )
        }
        return
    }
    val visibleCards = if (expanded) cards else cards.take(4)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(durationMillis = 230))
            .clip(ItemShape)
            .background(cs.background)
            .clickable(enabled = !expanded) { onToggle() }
    ) {
        val faceHeight = (maxWidth / 1.586f).coerceIn(154.dp, 222.dp)
        val targetStep = if (expanded) 86.dp else 22.dp
        val step by animateDpAsState(
            targetValue = targetStep,
            animationSpec = tween(durationMillis = 230),
            label = "walletStackStep"
        )
        val stackHeight = faceHeight + step * (visibleCards.size - 1).coerceAtLeast(0).toFloat()
        Box(Modifier.fillMaxWidth().height(stackHeight)) {
            visibleCards.forEachIndexed { index, card ->
                WalletCardFace(
                    card = card,
                    imageVersion = imageVersion,
                    showDetails = false,
                    forceLandscapeCrop = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(faceHeight)
                        .offset(y = step * index.toFloat())
                        .zIndex(index.toFloat())
                        .clickable {
                            if (expanded) onOpenCard(card) else onToggle()
                        }
                )
            }
        }
    }
}

@Composable
private fun WalletCardFace(
    card: Card,
    imageVersion: Int,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    showDetails: Boolean = true,
    forceLandscapeCrop: Boolean = false
) {
    val cs = MaterialTheme.colorScheme
    val nc = networkColor(card.network)
    val sourceCardBmp = rememberBitmap(card.logoImagePath, imageVersion, 720)
    val cardBmp = rememberDisplayBitmap(
        source = sourceCardBmp,
        rotateLandscape = forceLandscapeCrop && card.imageOrientation == "vertical"
    )
    val bankBmp = rememberBankLogo(card.bank, card.bankLogoPath, imageVersion, 160)
    val isDimmed = card.status == "cancelled" || card.status == "frozen"
    val statusLabel = when (card.status) {
        "frozen" -> stringResource(R.string.frozen)
        "pending" -> stringResource(R.string.pending)
        "cancelled" -> stringResource(R.string.cancelled)
        else -> ""
    }
    val displayName = card.cardTypeName.ifBlank { card.bank }
    val summary = cardSummaryText(card)
    val showOverlayDetails = showDetails || cardBmp == null
    val renderAsVertical = !forceLandscapeCrop && card.imageOrientation == "vertical"

    Box(
        modifier
            .clip(RoundedCornerShape(if (compact) 14.dp else 18.dp))
            .background(Brush.linearGradient(listOf(nc.bg, nc.bg.copy(alpha = 0.72f))))
            .border(
                1.4.dp,
                if (MaterialTheme.colorScheme.background.luminance() > 0.5f) {
                    Color.Black.copy(alpha = 0.28f)
                } else {
                    Color.White.copy(alpha = 0.34f)
                },
                RoundedCornerShape(if (compact) 14.dp else 18.dp)
            )
    ) {
        if (cardBmp != null) {
            Image(
                cardBmp.asImageBitmap(),
                null,
                Modifier.fillMaxSize(),
                contentScale = if (renderAsVertical) ContentScale.Fit else ContentScale.Crop,
                filterQuality = FilterQuality.Medium,
                alpha = if (isDimmed) 0.52f else 1f
            )
        }
        if (showOverlayDetails) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.20f), Color.Black.copy(alpha = 0.04f), Color.Black.copy(alpha = 0.28f))
                        )
                    )
            )
        }
        if (showOverlayDetails) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(if (compact) 14.dp else 18.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (bankBmp != null) {
                        Image(
                            bankBmp.asImageBitmap(),
                            null,
                            Modifier.size(if (compact) 28.dp else 34.dp).clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Fit,
                            filterQuality = FilterQuality.Medium
                        )
                    } else {
                        Box(
                            Modifier
                                .size(if (compact) 28.dp else 34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AccountBalance, null, tint = nc.text, modifier = Modifier.size(if (compact) 16.dp else 19.dp))
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(card.bank, fontSize = if (compact) 12.sp else 14.sp, fontWeight = FontWeight.SemiBold,
                            color = nc.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(displayName, fontSize = if (compact) 10.sp else 12.sp, color = nc.text.copy(alpha = 0.78f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (statusLabel.isNotEmpty()) {
                        SmallTag(statusLabel, Color.Black.copy(alpha = 0.34f), Color.White, compact = true)
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        if (card.tail.isNotBlank()) {
                            Text("**** ${card.tail}", fontSize = if (compact) 17.sp else 21.sp,
                                fontWeight = FontWeight.SemiBold, color = nc.text, letterSpacing = 0.sp)
                        }
                        if (summary.isNotBlank()) {
                            Text(summary, fontSize = if (compact) 10.sp else 12.sp, color = nc.text.copy(alpha = 0.72f),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Text(card.currency, fontSize = if (compact) 12.sp else 14.sp,
                        fontWeight = FontWeight.Bold, color = nc.text.copy(alpha = 0.82f))
                }
            }
        }
        if (showOverlayDetails && card.isVirtual) {
            Box(Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                SmallTag(stringResource(R.string.virtual_card), cs.primary.copy(alpha = 0.82f), cs.onPrimary, compact = true)
            }
        }
    }
}

@Composable
private fun CardFocusPage(
    card: Card,
    groups: List<CardGroup>,
    tasks: List<Task>,
    piggyEntries: List<PiggyEntry>,
    assetPlans: List<AssetPlan>,
    imageVersion: Int,
    onBack: () -> Unit,
    onEdit: () -> Unit
) {
    BackHandler(onBack = onBack)
    val cs = MaterialTheme.colorScheme
    val groupName = groups.firstOrNull { it.id == card.groupId }?.name ?: stringResource(R.string.ungrouped_cards)
    val relatedTasks = tasks.filter { it.cardId == card.id }
    val relatedPiggy = piggyEntries.filter { it.cardId == card.id }.sortedByDescending { it.timestamp }
    val relatedPlans = assetPlans.filter { it.cardId == card.id }.sortedBy { it.orderIndex }
    val bankBmp = rememberBankLogo(card.bank, card.bankLogoPath, imageVersion, 160)
    val statusLabel = when (card.status) {
        "frozen" -> stringResource(R.string.frozen)
        "pending" -> stringResource(R.string.pending)
        "cancelled" -> stringResource(R.string.cancelled)
        else -> stringResource(R.string.normal)
    }
    val networkDisplayName = when (card.network) {
        "閾惰仈" -> stringResource(R.string.network_unionpay)
        "鍏朵粬" -> stringResource(R.string.network_other)
        else -> card.network
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(cs.background)) {
        val pagePadding = screenPaddingFor(maxWidth)
        LazyColumn(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
            contentPadding = pagePadding,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.close), tint = cs.onBackground)
                    }
                    if (bankBmp != null) {
                        Image(
                            bankBmp.asImageBitmap(),
                            null,
                            Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit,
                            filterQuality = FilterQuality.Medium
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(card.bank, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                            color = cs.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(card.cardTypeName.ifBlank { card.cardCategory.ifBlank { card.currency } },
                            fontSize = 13.sp, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Default.Edit, stringResource(R.string.edit_card), tint = cs.primary)
                    }
                }
            }
            item {
                WalletCardFace(
                    card = card,
                    imageVersion = imageVersion,
                    showDetails = false,
                    forceLandscapeCrop = true,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1.586f)
                )
            }
            item {
                FocusSection(title = stringResource(R.string.card_focus_details)) {
                    FocusInfoRow(stringResource(R.string.card_group), groupName)
                    FocusInfoRow(stringResource(R.string.status), statusLabel)
                    if (!card.noCard) FocusInfoRow(stringResource(R.string.network), networkDisplayName)
                    FocusInfoRow(stringResource(R.string.currency), card.currency)
                    if (card.tail.isNotBlank()) FocusInfoRow(stringResource(R.string.tail_number), card.tail)
                    if (card.cardCategory.isNotBlank()) FocusInfoRow(stringResource(R.string.card_category), card.cardCategory)
                    if (card.expiryDate.isNotBlank()) FocusInfoRow(stringResource(R.string.expiry_optional), card.expiryDate)
                    if (card.creditLimit > 0.0) FocusInfoRow(stringResource(R.string.credit_limit), "${card.currency} ${formatCreditAmount(card.creditLimit)}")
                    if (card.billingDay in 1..31) FocusInfoRow(stringResource(R.string.billing_day), stringResource(R.string.billing_day_format, card.billingDay))
                    if (card.repaymentDay in 1..31) FocusInfoRow(stringResource(R.string.repayment_day), stringResource(R.string.billing_day_format, card.repaymentDay))
                    if (card.note.isNotBlank()) FocusInfoRow(stringResource(R.string.note_optional), card.note)
                }
            }
            item {
                FocusSection(title = stringResource(R.string.card_related_items)) {
                    FocusInfoRow(stringResource(R.string.card_related_tasks), relatedTasks.take(3).joinToString { it.name }.ifBlank { "0" })
                    FocusInfoRow(stringResource(R.string.card_related_piggy), relatedPiggy.take(3).joinToString { "${it.date} ${formatCreditAmount(it.amount)}" }.ifBlank { "0" })
                    FocusInfoRow(stringResource(R.string.card_related_assets), relatedPlans.take(3).joinToString { it.name.ifBlank { it.platform } }.ifBlank { "0" })
                }
            }
            bottomSpacer()
        }
    }
}

@Composable
private fun FocusSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(shape = ItemShape, color = cs.surface, tonalElevation = 0.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun FocusInfoRow(label: String, value: String) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(label, fontSize = 12.sp, color = cs.onSurfaceVariant, modifier = Modifier.width(86.dp))
        Text(value, fontSize = 13.sp, color = cs.onSurface, modifier = Modifier.weight(1f))
    }
}

private fun cardSummaryText(card: Card): String =
    listOfNotNull(
        card.cardCategory.takeIf { it.isNotBlank() && !card.noCard },
        card.expiryDate.takeIf { it.isNotBlank() },
        card.note.takeIf { it.isNotBlank() }
    ).joinToString("  ")

@Composable
fun FilterDialog(
    allNetworks: List<String>,
    filterNetworks: Set<String>, filterCurrency: String, filterStatus: String,
    networkLabel: (String) -> String,
    onNetworkToggle: (String) -> Unit, onCurrencyChange: (String) -> Unit,
    onStatusChange: (String) -> Unit, onClear: () -> Unit, onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val currencies = listOf("CNY","HKD","USD","EUR","GBP","JPY","MOP","AUD","CAD")
    val statuses = listOf(
        "active" to stringResource(R.string.normal),
        "frozen" to stringResource(R.string.frozen),
        "pending" to stringResource(R.string.pending),
        "cancelled" to stringResource(R.string.cancelled)
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = cs.surface,
            modifier = Modifier.responsiveDialogWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.filter_cards), fontWeight = FontWeight.Bold, fontSize = 16.sp,
                        modifier = Modifier.weight(1f))
                    if (filterNetworks.isNotEmpty() || filterCurrency.isNotEmpty() || filterStatus.isNotEmpty()) {
                        TextButton(onClick = onClear, contentPadding = PaddingValues(4.dp)) {
                            Text(stringResource(R.string.clear_all), fontSize = 12.sp, color = ColorRed)
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                    }
                }

                // 卡组织（多选，含账户）
                FilterSection(stringResource(R.string.network_multi_select)) {
                    val isAccountSel = "__account__" in filterNetworks
                    FilterPill(stringResource(R.string.account), isAccountSel,
                        if (isAccountSel) Color(0xFF2D3748) else cs.surfaceVariant,
                        if (isAccountSel) Color(0xFF94A3B8) else cs.onSurfaceVariant) {
                        onNetworkToggle("__account__")
                    }
                    allNetworks.forEach { n ->
                        val nc = networkColor(n)
                        val sel = n in filterNetworks
                        FilterPill(networkLabel(n), sel, if (sel) nc.bg else cs.surfaceVariant,
                            if (sel) nc.text else cs.onSurfaceVariant) {
                            onNetworkToggle(n)
                        }
                    }
                }

                // 币种
                FilterSection(stringResource(R.string.currency)) {
                    currencies.forEach { c ->
                        val sel = filterCurrency == c
                        FilterPill(c, sel, if (sel) cs.primary else cs.surfaceVariant,
                            if (sel) Color.White else cs.onSurfaceVariant) {
                            onCurrencyChange(if (sel) "" else c)
                        }
                    }
                }

                // 状态
                FilterSection(stringResource(R.string.status)) {
                    statuses.forEach { (v, l) ->
                        val col = when(v) { "frozen"->ColorFrozen; "cancelled"->ColorRed; "pending"->ColorPending; else->ColorActive }
                        val sel = filterStatus == v
                        FilterPill(l, sel,
                            if (sel) col.copy(.2f) else cs.surfaceVariant,
                            if (sel) col else cs.onSurfaceVariant,
                            if (sel) col else Color.Transparent) {
                            onStatusChange(if (sel) "" else v)
                        }
                    }
                }

                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)) { Text(stringResource(R.string.done)) }
            }
        }
    }
}

@Composable
private fun FilterSection(title: String, content: @Composable FlowRowScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            content()
        }
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, bg: Color, textColor: Color,
                       border: Color = Color.Transparent, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(bg)
        .border(1.dp, border, RoundedCornerShape(20.dp))
        .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(label, fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = textColor)
    }
}

// ══════════════════════════════════════════════════════════
// 图库模式卡片
// ══════════════════════════════════════════════════════════
@Composable
fun CardGalleryItem(
    card: Card,
    onEdit: () -> Unit,
    imageVersion: Int,
    modifier: Modifier = Modifier,
    columnsInRow: Int = 2
) {
    val cs = MaterialTheme.colorScheme
    val nc = networkColor(card.network)
    val compact = columnsInRow >= 3
    val dense = columnsInRow >= 4
    val facePadding = if (compact) 7.dp else 10.dp
    val faceCorner = if (compact) 10.dp else 12.dp
    val cardInfoSpacing = if (compact) 2.dp else 4.dp
    val infoHorizontalPadding = if (compact) 7.dp else 9.dp
    val infoTopPadding = if (compact) 6.dp else 8.dp
    val infoBottomPadding = if (compact) 7.dp else 9.dp
    val faceBankLogoSize = if (compact) 17.dp else 22.dp
    val faceBankFont = if (compact) 8.sp else 10.sp
    val faceWatermarkFont = if (compact) 26.sp else 36.sp
    val faceTailFont = if (compact) 9.sp else 11.sp
    val infoLogoSize = if (compact) 12.dp else 14.dp
    val infoBankFont = if (compact) 9.sp else 10.sp
    val titleFont = if (compact) 11.sp else 13.sp
    val titleLineHeight = if (compact) 14.sp else 16.sp
    val infoTailFont = if (compact) 9.sp else 11.sp
    val expiryFont = if (compact) 8.sp else 9.sp
    val noteFont = if (compact) 8.sp else 10.sp
    val tagSpacing = if (compact) 2.dp else 4.dp
    val statusColor = when (card.status) {
        "frozen" -> Color(0xFF94A3B8); "pending" -> Color(0xFF7DD3FC)
        "cancelled" -> Color(0xFFEF4444); else -> Color(0xFF22C55E)
    }
    val statusLabel = when (card.status) {
        "frozen" -> stringResource(R.string.frozen)
        "pending" -> stringResource(R.string.pending)
        "cancelled" -> stringResource(R.string.cancelled)
        else -> ""
    }
    val networkDisplayName = when (card.network) {
        "银联" -> stringResource(R.string.network_unionpay)
        "其他" -> stringResource(R.string.network_other)
        else -> card.network
    }
    val categoryDisplayName = when (card.cardCategory) {
        "储蓄卡" -> stringResource(R.string.debit_card)
        "信用卡" -> stringResource(R.string.credit_card)
        else -> card.cardCategory
    }
    val isCreditCard = !card.noCard && card.cardCategory == "信用卡"
    val creditLimitLabel = stringResource(R.string.credit_limit)
    val creditMetaTags = listOfNotNull(
        if (card.creditLimit > 0.0) "$creditLimitLabel ${formatCreditAmount(card.creditLimit)}" else null,
        if (card.billingDay in 1..31) "${stringResource(R.string.billing_day)} ${stringResource(R.string.day_short_format, card.billingDay)}" else null,
        if (card.repaymentDay in 1..31) "${stringResource(R.string.repayment_day)} ${stringResource(R.string.day_short_format, card.repaymentDay)}" else null
    )
    val isDimmed = card.status == "cancelled" || card.status == "frozen"

    val cardBmp = rememberBitmap(card.logoImagePath, imageVersion, 720)
    val bankBmp = rememberBankLogo(card.bank, card.bankLogoPath, imageVersion, 160)

    // 卡片显示名（卡种名优先）
    val displayName = card.cardTypeName.ifBlank { card.bank }
    val isVerticalFace = card.imageOrientation == "vertical"

    @Composable
    fun CardFace(faceModifier: Modifier) {
        Box(faceModifier
            .clip(RoundedCornerShape(faceCorner))
            .background(Brush.linearGradient(listOf(nc.bg, nc.bg.copy(.7f))))) {

            if (cardBmp != null) {
                Image(cardBmp.asImageBitmap(), null,
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.Medium,
                    alpha = if (isDimmed) 0.45f else 1f)
            } else {
                // 无图占位
                Column(Modifier.fillMaxSize().padding(facePadding),
                    verticalArrangement = Arrangement.SpaceBetween) {
                    // 银行 logo 或名称
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)) {
                        if (bankBmp != null) {
                            Image(bankBmp.asImageBitmap(), null,
                                Modifier.size(faceBankLogoSize).clip(RoundedCornerShape(4.dp)),
                                contentScale = ContentScale.Fit,
                                filterQuality = FilterQuality.Medium)
                        }
                        Text(card.bank.take(if (compact) 4 else 6), fontSize = faceBankFont, color = nc.text.copy(.8f),
                            fontWeight = FontWeight.SemiBold, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                    }
                    Column {
                        Text(card.bank.take(1), fontSize = faceWatermarkFont,
                            fontWeight = FontWeight.ExtraBold, color = nc.text.copy(.12f))
                        if (card.tail.isNotEmpty())
                            Text("*${card.tail}", fontSize = faceTailFont, color = nc.text.copy(.6f))
                    }
                }
                // 添加卡面提示（右下角）
            }

            // 状态角标（非正常才显示）
            if (statusLabel.isNotEmpty()) {
                Box(Modifier.align(Alignment.TopEnd).padding(5.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(statusColor.copy(.9f))
                    .padding(horizontal = if (compact) 4.dp else 5.dp, vertical = 2.dp)) {
                    Text(statusLabel, fontSize = if (compact) 7.sp else 8.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            // 虚拟卡角标（右下角）
            if (card.isVirtual) {
                Box(Modifier.align(Alignment.BottomEnd).padding(5.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF7C3AED).copy(.85f))
                    .padding(horizontal = if (compact) 4.dp else 5.dp, vertical = 2.dp)) {
                    Text(stringResource(R.string.virtual_card), fontSize = if (compact) 7.sp else 8.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    @Composable
    fun CardInfo(infoModifier: Modifier) {
        Column(infoModifier,
            verticalArrangement = Arrangement.spacedBy(cardInfoSpacing)) {
            // 银行名（小标题）
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (bankBmp != null) {
                    Image(bankBmp.asImageBitmap(), null,
                        Modifier.size(infoLogoSize).clip(RoundedCornerShape(3.dp)),
                        contentScale = ContentScale.Fit,
                        filterQuality = FilterQuality.Medium)
                } else {
                    Box(Modifier.size(infoLogoSize).clip(RoundedCornerShape(3.dp))
                        .background(nc.bg),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.AccountBalance, null,
                            tint = nc.text, modifier = Modifier.size(if (compact) 8.dp else 9.dp))
                    }
                }
                Text(card.bank, fontSize = infoBankFont, color = cs.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // 卡种名 + 卡号
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(displayName, fontWeight = FontWeight.Bold, fontSize = titleFont,
                    lineHeight = titleLineHeight,
                    color = cs.onSurface, modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (card.tail.isNotEmpty())
                    Text("*${card.tail}", fontSize = infoTailFont, color = cs.onSurfaceVariant)
            }
            // 标签行（左：卡组织+币种+用途；右：有效期）
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!card.noCard) SmallTag(networkDisplayName, nc.bg, nc.text, compact)
                    SmallTag(card.currency, cs.outline.copy(.2f), cs.onSurfaceVariant, compact)
                    if (!card.noCard && categoryDisplayName.isNotEmpty())
                        SmallTag(categoryDisplayName, cs.outline.copy(.15f), cs.onSurfaceVariant, compact)
                }
                if (card.expiryDate.isNotEmpty())
                    Text(card.expiryDate, fontSize = expiryFont,
                        color = cs.onSurfaceVariant.copy(.55f),
                        fontWeight = FontWeight.Medium)
            }
            // 备注（斜体小字）
            if (isCreditCard && creditMetaTags.isNotEmpty() && !dense) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(tagSpacing),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    creditMetaTags.forEach {
                        SmallTag(it, cs.primary.copy(.08f), cs.primary.copy(.82f), compact)
                    }
                }
            }
            if (card.note.isNotEmpty() && !dense) {
                Text(card.note, fontSize = noteFont, color = cs.onSurfaceVariant.copy(.5f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    @Composable
    fun VerticalCardInfo(infoModifier: Modifier) {
        Column(
            infoModifier.padding(top = if (compact) 4.dp else 10.dp, bottom = if (compact) 2.dp else 4.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (bankBmp != null) {
                    Image(bankBmp.asImageBitmap(), null,
                        Modifier.size(infoLogoSize).clip(RoundedCornerShape(3.dp)),
                        contentScale = ContentScale.Fit,
                        filterQuality = FilterQuality.Medium)
                } else {
                    Box(Modifier.size(infoLogoSize).clip(RoundedCornerShape(3.dp))
                        .background(nc.bg),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.AccountBalance, null,
                            tint = nc.text, modifier = Modifier.size(if (compact) 8.dp else 9.dp))
                    }
                }
                Text(card.bank, fontSize = infoBankFont, lineHeight = if (compact) 11.sp else 12.sp, color = cs.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                Column(
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(tagSpacing)
                ) {
                    if (!card.noCard) SmallTag(networkDisplayName, nc.bg, nc.text, compact)
                    SmallTag(card.currency, cs.outline.copy(.2f), cs.onSurfaceVariant, compact)
                    if (!card.noCard && categoryDisplayName.isNotEmpty())
                        SmallTag(categoryDisplayName, cs.outline.copy(.15f), cs.onSurfaceVariant, compact)
                }
            }

            Column(Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (card.tail.isNotEmpty())
                    Text("*${card.tail}", fontSize = infoTailFont, lineHeight = if (compact) 11.sp else 14.sp, color = cs.onSurfaceVariant,
                        fontWeight = FontWeight.Medium)
                if (card.expiryDate.isNotEmpty())
                    Text(card.expiryDate, fontSize = expiryFont, lineHeight = if (compact) 10.sp else 12.sp,
                        color = cs.onSurfaceVariant.copy(.62f),
                        fontWeight = FontWeight.Medium)
                if (isCreditCard && creditMetaTags.isNotEmpty() && !dense) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(tagSpacing),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        creditMetaTags.forEach {
                            SmallTag(it, cs.primary.copy(.08f), cs.primary.copy(.82f), compact)
                        }
                    }
                }
            }
        }
    }

    BoxWithConstraints(modifier) {
        val showNote = card.note.isNotEmpty() && !dense
        val verticalNoteHeight = if (showNote) (if (compact) 12.dp else 16.dp) else 0.dp
        val baseVerticalCardHeight = (maxWidth / 1.586f) + if (compact) 46.dp else 70.dp
        val verticalCardHeight = baseVerticalCardHeight + verticalNoteHeight
        val verticalFaceHeight = baseVerticalCardHeight - if (compact) 30.dp else 38.dp
        val containerModifier = Modifier
            .fillMaxWidth()
            .clip(ItemShape)
            .background(cs.surface)
            .border(1.dp, cs.outline.copy(alpha = 0.18f), ItemShape)
            .clickable { onEdit() }

        if (isVerticalFace) {
            Column(containerModifier.height(verticalCardHeight).padding(if (compact) 6.dp else 8.dp),
                verticalArrangement = Arrangement.SpaceBetween) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    CardFace(Modifier.height(verticalFaceHeight).aspectRatio(0.63f))
                    VerticalCardInfo(Modifier.weight(1f).height(verticalFaceHeight))
                }
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(displayName, fontWeight = FontWeight.Bold, fontSize = titleFont, lineHeight = titleLineHeight,
                        color = cs.onSurface,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (showNote) {
                        Text(card.note, fontSize = noteFont, color = cs.onSurfaceVariant.copy(.5f),
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        } else {
            Column(containerModifier) {
                CardFace(Modifier.fillMaxWidth().aspectRatio(1.586f))
                CardInfo(Modifier.fillMaxWidth().padding(
                    start = infoHorizontalPadding,
                    end = infoHorizontalPadding,
                    top = infoTopPadding,
                    bottom = infoBottomPadding
                ))
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
// 列表模式卡片
// ══════════════════════════════════════════════════════════
@Composable
fun CardListItem(
    card: Card,
    onEdit: () -> Unit, onMoveUp: () -> Unit, onMoveDown: () -> Unit,
    imageVersion: Int
) {
    val cs = MaterialTheme.colorScheme
    val nc = networkColor(card.network)
    val (statusColor, statusLabel) = when (card.status) {
        "frozen" -> Pair(Color(0xFF94A3B8), stringResource(R.string.frozen))
        "pending" -> Pair(Color(0xFF7DD3FC), stringResource(R.string.pending))
        "cancelled" -> Pair(Color(0xFFEF4444), stringResource(R.string.cancelled))
        else -> Pair(Color(0xFF22C55E), stringResource(R.string.normal))
    }
    val isDimmed = card.status == "cancelled" || card.status == "frozen"
    val cardBmp = rememberBitmap(card.logoImagePath, imageVersion, 320)
    val bankBmp = rememberBankLogo(card.bank, card.bankLogoPath, imageVersion, 120)
    val displayName = card.cardTypeName.ifBlank { card.bank }
    val isVerticalFace = card.imageOrientation == "vertical"
    val networkDisplayName = when (card.network) {
        "银联" -> stringResource(R.string.network_unionpay)
        "其他" -> stringResource(R.string.network_other)
        else -> card.network
    }
    val categoryDisplayName = when (card.cardCategory) {
        "储蓄卡" -> stringResource(R.string.debit_card)
        "信用卡" -> stringResource(R.string.credit_card)
        else -> card.cardCategory
    }
    val isCreditCard = !card.noCard && card.cardCategory == "信用卡"
    val creditLimitLabel = stringResource(R.string.credit_limit)
    val creditMetaTags = listOfNotNull(
        if (card.creditLimit > 0.0) "$creditLimitLabel ${formatCreditAmount(card.creditLimit)}" else null,
        if (card.billingDay in 1..31) "${stringResource(R.string.billing_day)} ${stringResource(R.string.day_short_format, card.billingDay)}" else null,
        if (card.repaymentDay in 1..31) "${stringResource(R.string.repayment_day)} ${stringResource(R.string.day_short_format, card.repaymentDay)}" else null
    )
    val thumbModifier = if (isVerticalFace) {
        Modifier.width(43.dp).height(68.dp)
    } else {
        Modifier.width(68.dp).height(43.dp)
    }

    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(ItemShape)
        .background(cs.surface.copy(if (isDimmed) .55f else 1f))
        .border(1.dp, cs.outline.copy(alpha = 0.18f), ItemShape)
        .clickable { onEdit() }, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(statusColor.copy(if (isDimmed) .4f else .8f)))
        Spacer(Modifier.width(10.dp))
        // Logo 缩略
        Box(thumbModifier.clip(RoundedCornerShape(8.dp)).background(nc.bg), contentAlignment = Alignment.Center) {
            if (cardBmp != null) Image(cardBmp.asImageBitmap(), null,
                Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.Medium)
            else if (bankBmp != null) Image(bankBmp.asImageBitmap(), null,
                Modifier.size(28.dp), contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.Medium)
            else Text(card.bank.take(1), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = nc.text)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // 银行名（小标题）
            Text(card.bank, fontSize = 10.sp, color = cs.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            // 卡种名 + 卡号
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    color = cs.onSurface, modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (card.tail.isNotEmpty())
                    Text("*${card.tail}", fontSize = 11.sp, color = cs.onSurfaceVariant,
                        maxLines = 1)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    if (!card.noCard) SmallTag(networkDisplayName, nc.bg, nc.text)
                    SmallTag(card.currency, cs.outline.copy(.2f), cs.onSurfaceVariant)
                    if (!card.noCard && categoryDisplayName.isNotEmpty())
                        SmallTag(categoryDisplayName, cs.outline.copy(.15f), cs.onSurfaceVariant)
                    Row(Modifier.clip(RoundedCornerShape(3.dp)).background(statusColor.copy(.15f))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(Modifier.size(4.dp).clip(CircleShape).background(statusColor))
                        Text(statusLabel, fontSize = 9.sp, color = statusColor, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (card.expiryDate.isNotEmpty())
                    Text(card.expiryDate, fontSize = 9.sp,
                        color = cs.onSurfaceVariant.copy(.55f),
                        fontWeight = FontWeight.Medium)
            }
            if (isCreditCard && creditMetaTags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    creditMetaTags.forEach {
                        SmallTag(it, cs.primary.copy(.08f), cs.primary.copy(.82f))
                    }
                }
            }
            if (card.note.isNotEmpty()) {
                Text(card.note, fontSize = 10.sp,
                    color = cs.onSurfaceVariant.copy(.5f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Column {
            IconButton(onClick = onMoveUp, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.KeyboardArrowUp, null, tint = cs.onSurfaceVariant.copy(.3f), modifier = Modifier.size(14.dp))
            }
            IconButton(onClick = onMoveDown, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, null, tint = cs.onSurfaceVariant.copy(.3f), modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
fun SmallTag(text: String, bg: Color, textColor: Color, compact: Boolean = false) {
    Box(Modifier.clip(RoundedCornerShape(3.dp)).background(bg).padding(
        horizontal = if (compact) 4.dp else 5.dp,
        vertical = 1.dp
    )) {
        Text(text,
            fontSize = if (compact) 7.sp else 8.sp,
            lineHeight = if (compact) 9.sp else 10.sp,
            color = textColor,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private val creditAmountFormatter = DecimalFormat("#,##0.##")

private fun formatCreditAmount(amount: Double): String =
    creditAmountFormatter.format(amount)

private fun formatCreditInputAmount(amount: Double): String =
    if (amount <= 0.0) "" else DecimalFormat("0.##").format(amount)

private fun sanitizeCreditLimitInput(raw: String): String {
    var hasDot = false
    val builder = StringBuilder()
    raw.forEach { ch ->
        when {
            ch.isDigit() -> builder.append(ch)
            ch == '.' && !hasDot -> {
                hasDot = true
                builder.append(ch)
            }
        }
    }
    return builder.toString().take(12)
}

// ══════════════════════════════════════════════════════════
// 分组 Dialog
// ══════════════════════════════════════════════════════════
@Composable
fun GroupDialog(initial: CardGroup? = null, onDismiss: () -> Unit,
                onDelete: (() -> Unit)? = null, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var icon by remember { mutableStateOf(initial?.icon ?: "") }
    var submitted by remember { mutableStateOf(false) }
    val nameError = submitted && name.isBlank()
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.responsiveDialogWidth()) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (initial == null) stringResource(R.string.add_group) else stringResource(R.string.edit_group), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (it.isNotBlank()) submitted = false
                    },
                    label = { Text(stringResource(R.string.group_name)) },
                    isError = nameError,
                    supportingText = {
                        if (nameError) Text(stringResource(R.string.required_field))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(value = icon, onValueChange = { icon = it },
                    label = { Text(stringResource(R.string.group_icon_optional)) }, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp), placeholder = { Text(stringResource(R.string.group_icon_placeholder)) })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    onDelete?.let { OutlinedButton(onClick = it, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text(stringResource(R.string.delete), color = ColorRed) } }
                    Button(onClick = {
                        submitted = true
                        if (name.isNotBlank()) onSave(name.trim(), icon.ifBlank { name.take(1) })
                    },
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text(stringResource(R.string.save)) }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
// 卡片编辑 Dialog
// ══════════════════════════════════════════════════════════
private sealed class BankPickerRow {
    data class Header(val section: String) : BankPickerRow()
    data class Entry(val bank: BuiltinBank) : BankPickerRow()
}

private fun openMissingBankIssue(context: Context): Boolean {
    val issueUrl = Uri.parse("https://github.com/ayyy7128/CardManager/issues/new")
        .buildUpon()
        .appendQueryParameter("title", "[银行 Logo] 补充银行：")
        .appendQueryParameter(
            "body",
            "银行名称：\n英文名称：\n国家/地区：\n官方网站：\n官方 Logo 来源：\n其他说明："
        )
        .build()
    return runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, issueUrl))
        true
    }.getOrDefault(false)
}

@Composable
private fun BankPickerPage(
    banks: List<BuiltinBank>,
    selectedBank: String,
    onBack: () -> Unit,
    onSelect: (BuiltinBank) -> Unit,
    onMissing: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme
    val filteredBanks = remember(banks, query) {
        val normalized = query.trim().lowercase()
        if (normalized.isBlank()) banks else banks.filter { it.searchText.contains(normalized) }
    }
    val rows = remember(filteredBanks) {
        buildList<BankPickerRow> {
            filteredBanks.groupBy { it.section }.toSortedMap().forEach { (section, sectionBanks) ->
                add(BankPickerRow.Header(section))
                sectionBanks.forEach { add(BankPickerRow.Entry(it)) }
            }
        }
    }
    val sectionIndices = remember(rows) {
        rows.mapIndexedNotNull { index, row ->
            (row as? BankPickerRow.Header)?.section?.let { it to index }
        }.toMap()
    }
    val alphabetSections = remember(banks) { banks.map { it.section }.distinct().sorted() }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().height(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, stringResource(R.string.close))
            }
            Text(
                stringResource(R.string.select_bank),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onMissing) {
                Text(stringResource(R.string.bank_not_found))
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.search_bank)) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Close, stringResource(R.string.clear_search))
                    }
                }
            } else null,
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(Modifier.height(8.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (rows.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.bank_search_empty), color = colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(end = if (query.isBlank()) 30.dp else 0.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(rows, key = { row ->
                        when (row) {
                            is BankPickerRow.Header -> "header_${row.section}"
                            is BankPickerRow.Entry -> "bank_${row.bank.name}"
                        }
                    }) { row ->
                        when (row) {
                            is BankPickerRow.Header -> Text(
                                row.section,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.primary
                            )
                            is BankPickerRow.Entry -> {
                                val logo = rememberAssetBitmap(row.bank.logoAsset, 256)
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onSelect(row.bank) }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(colorScheme.surfaceVariant.copy(alpha = 0.55f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (logo != null) {
                                            Image(
                                                logo.asImageBitmap(),
                                                null,
                                                Modifier.fillMaxSize().padding(2.dp),
                                                contentScale = ContentScale.Fit,
                                                filterQuality = FilterQuality.High
                                            )
                                        } else {
                                            Icon(
                                                Icons.Default.AccountBalance,
                                                null,
                                                modifier = Modifier.size(20.dp),
                                                tint = colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        Text(
                                            row.bank.name,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (row.bank.english.isNotBlank() && row.bank.english != row.bank.name) {
                                            Text(
                                                row.bank.english,
                                                fontSize = 11.sp,
                                                color = colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    if (row.bank.name == selectedBank) {
                                        Icon(
                                            Icons.Default.Check,
                                            stringResource(R.string.selected),
                                            tint = colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (query.isBlank()) {
                    Column(
                        Modifier
                            .align(Alignment.CenterEnd)
                            .width(30.dp)
                            .fillMaxHeight()
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceEvenly
                    ) {
                        alphabetSections.forEach { section ->
                            Text(
                                section,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .clickable {
                                        sectionIndices[section]?.let { index ->
                                            scope.launch { listState.scrollToItem(index) }
                                        }
                                    }
                                    .wrapContentHeight(Alignment.CenterVertically),
                                color = colorScheme.primary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MissingBankDialog(
    onDismiss: () -> Unit,
    onCustom: () -> Unit,
    onFeedback: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bank_not_found_title)) },
        text = {
            Column {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.custom_bank)) },
                    supportingContent = { Text(stringResource(R.string.custom_bank_summary)) },
                    leadingContent = { Icon(Icons.Default.Edit, null) },
                    trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                    modifier = Modifier.clickable { onCustom() }
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.feedback_missing_bank)) },
                    supportingContent = { Text(stringResource(R.string.feedback_missing_bank_summary)) },
                    leadingContent = { Icon(Icons.Default.Feedback, null) },
                    trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                    modifier = Modifier.clickable { onFeedback() }
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun CustomBankDialog(
    initialName: String,
    initialLogoPath: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    val context = LocalContext.current
    var name by remember(initialName) { mutableStateOf(initialName) }
    var logoPath by remember(initialLogoPath) { mutableStateOf(initialLogoPath) }
    var logoRefreshKey by remember { mutableStateOf(0) }
    var submitted by remember { mutableStateOf(false) }
    val logo = rememberBitmap(logoPath, logoRefreshKey, 256)

    fun discardNewLogo() {
        if (logoPath.isNotBlank() && logoPath != initialLogoPath) {
            ImageStore.deleteOwned(context, logoPath)
        }
    }

    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val saved = ImageStore.saveFromUri(context, it, "custom_bank_${System.currentTimeMillis()}")
            if (saved.isNotBlank()) {
                discardNewLogo()
                logoPath = saved
                logoRefreshKey++
            }
        }
    }

    AlertDialog(
        onDismissRequest = {
            discardNewLogo()
            onDismiss()
        },
        title = { Text(stringResource(R.string.custom_bank)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier
                        .size(76.dp)
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { logoPicker.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (logo != null) {
                        Image(
                            logo.asImageBitmap(),
                            null,
                            Modifier.fillMaxSize().padding(4.dp),
                            contentScale = ContentScale.Fit,
                            filterQuality = FilterQuality.High
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.AddPhotoAlternate, null, modifier = Modifier.size(24.dp))
                            Text(stringResource(R.string.choose_logo), fontSize = 10.sp)
                        }
                    }
                }
                if (logoPath.isNotBlank()) {
                    TextButton(
                        onClick = {
                            discardNewLogo()
                            logoPath = ""
                            logoRefreshKey++
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(stringResource(R.string.remove_logo))
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (it.isNotBlank()) submitted = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.bank_name)) },
                    singleLine = true,
                    isError = submitted && name.isBlank(),
                    supportingText = {
                        if (submitted && name.isBlank()) Text(stringResource(R.string.required_field))
                    },
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                submitted = true
                if (name.isNotBlank()) onSave(name.trim(), logoPath)
            }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                discardNewLogo()
                onDismiss()
            }) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

data class CardFormData(
    val bank: String, val network: String, val currency: String,
    val tail: String, val note: String, val status: String,
    val isVirtual: Boolean, val noCard: Boolean, val logoImagePath: String,
    val bankLogoPath: String, val cardTypeName: String,
    val expiryDate: String, val cardCategory: String,
    val imageOrientation: String, val groupId: String,
    val creditLimit: Double, val billingDay: Int, val repaymentDay: Int
)

private enum class NfcFilledField {
    NETWORK,
    CURRENCY,
    TAIL,
    EXPIRY,
    CATEGORY
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CardDialog(initial: Card? = null, groups: List<CardGroup> = emptyList(), selectedGroupId: String = initial?.groupId ?: "",
               onDismiss: () -> Unit,
               onDelete: (() -> Unit)? = null, asPage: Boolean = false, onSave: (CardFormData) -> Unit) {
    var bank         by remember { mutableStateOf(initial?.bank ?: "") }
    var cardTypeName by remember { mutableStateOf(initial?.cardTypeName ?: "") }
    var network      by remember { mutableStateOf(initial?.network ?: "银联") }
    var currency     by remember { mutableStateOf(initial?.currency ?: "CNY") }
    var tail         by remember { mutableStateOf(initial?.tail ?: "") }
    var cardCategory by remember { mutableStateOf(initial?.cardCategory ?: "") }
    var creditLimitText by remember { mutableStateOf(formatCreditInputAmount(initial?.creditLimit ?: 0.0)) }
    var billingDay    by remember { mutableStateOf(initial?.billingDay?.takeIf { it in 1..31 } ?: 0) }
    var repaymentDay  by remember { mutableStateOf(initial?.repaymentDay?.takeIf { it in 1..31 } ?: 0) }
    var note         by remember { mutableStateOf(initial?.note ?: "") }
    var status       by remember { mutableStateOf(initial?.status ?: "active") }
    var isVirtual    by remember { mutableStateOf(initial?.isVirtual ?: false) }
    var noCard       by remember { mutableStateOf(initial?.noCard ?: false) }
    var imagePath    by remember { mutableStateOf(initial?.logoImagePath ?: "") }
    var bankLogoPath by remember { mutableStateOf(initial?.bankLogoPath ?: "") }
    var expiryDate   by remember { mutableStateOf(initial?.expiryDate ?: "") }
    var imageOrientation by remember { mutableStateOf(initial?.imageOrientation ?: "horizontal") }
    var submitted by remember { mutableStateOf(false) }
    var imageRefreshKey by remember { mutableStateOf(0) }
    var bankLogoRefreshKey by remember { mutableStateOf(0) }
    var groupId by remember(initial?.id, selectedGroupId, groups) {
        mutableStateOf(
            when {
                initial != null && groups.any { it.id == initial.groupId } -> initial.groupId
                initial?.groupId?.isBlank() == true -> ""
                selectedGroupId.isBlank() || groups.any { it.id == selectedGroupId } -> selectedGroupId
                else -> ""
            }
        )
    }
    var showExpiryPicker by remember { mutableStateOf(false) }
    var nfcActive by remember { mutableStateOf(false) }
    var nfcStatusText by remember { mutableStateOf("") }
    var pendingNfcResult by remember { mutableStateOf<EmvProbeResult?>(null) }
    var nfcFilledFields by remember { mutableStateOf(emptySet<NfcFilledField>()) }
    var showResourceLibrary by remember { mutableStateOf(false) }
    var showBankPicker by remember { mutableStateOf(false) }
    var showMissingBankMenu by remember { mutableStateOf(false) }
    var showCustomBankDialog by remember { mutableStateOf(false) }
    var showFeedbackOpenFailed by remember { mutableStateOf(false) }
    var applyingResource by remember { mutableStateOf(false) }
    var resourceApplyError by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val nc = networkColor(network)
    val activity = remember(ctx) { ctx.findActivity() }
    val nfcAdapter = remember(ctx) { NfcAdapter.getDefaultAdapter(ctx) }
    val nfcWaitingText = stringResource(R.string.nfc_waiting)
    val nfcReadingText = stringResource(R.string.nfc_reading)
    val nfcUnsupportedText = stringResource(R.string.nfc_unsupported)
    val nfcDisabledText = stringResource(R.string.nfc_disabled)
    val nfcFailedText = stringResource(R.string.nfc_failed)
    val resourceApplyFailedText = stringResource(R.string.resource_library_apply_failed)

    fun stopNfcReader() {
        activity?.let { nfcAdapter?.disableReaderMode(it) }
        nfcActive = false
    }

    fun startNfcReader() {
        val host = activity
        val adapter = nfcAdapter
        when {
            host == null || adapter == null -> {
                nfcStatusText = nfcUnsupportedText
                nfcActive = false
            }
            !adapter.isEnabled -> {
                nfcStatusText = nfcDisabledText
                nfcActive = false
            }
            else -> {
                nfcStatusText = nfcWaitingText
                nfcActive = true
                val flags = NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
                val extras = Bundle().apply {
                    putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250)
                }
                adapter.enableReaderMode(
                    host,
                    { tag ->
                        host.runOnUiThread { nfcStatusText = nfcReadingText }
                        val isoDep = IsoDep.get(tag)
                        val result = if (isoDep == null) {
                            EmvProbeResult(success = false, failureStage = "不是 ISO-DEP 银行卡")
                        } else {
                            EmvCardReader().read(isoDep)
                        }
                        host.runOnUiThread {
                            stopNfcReader()
                            if (result.hasFillableFields()) {
                                pendingNfcResult = result
                            } else {
                                nfcStatusText = result.failureStage ?: nfcFailedText
                            }
                        }
                    },
                    flags,
                    extras
                )
            }
        }
    }

    DisposableEffect(activity, nfcAdapter) {
        onDispose { stopNfcReader() }
    }

    val cardPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val p = ImageStore.saveFromUri(ctx, it, initial?.id ?: "tmp_${System.currentTimeMillis()}")
            if (p.isNotEmpty()) {
                imagePath = p
                imageRefreshKey++
            }
        }
    }
    val cardBmp = rememberBitmap(imagePath, imageRefreshKey, 720)
    val bankBmp = rememberBankLogo(bank, bankLogoPath, bankLogoRefreshKey, 160)
    val builtinBanks = remember(ctx) { BuiltinBankCatalog.banks(ctx) }

    val networkOptions  = listOf(
        "银联" to stringResource(R.string.network_unionpay),
        "Visa" to "Visa",
        "Mastercard" to "Mastercard",
        "AMEX" to "AMEX",
        "JCB" to "JCB",
        "Discover" to "Discover",
        "其他" to stringResource(R.string.network_other)
    )
    val currencies = listOf("CNY","HKD","USD","EUR","GBP","JPY","MOP","AUD","CAD","TWD","SGD","KRW","THB")
    val statuses  = listOf(
        "active" to stringResource(R.string.normal),
        "frozen" to stringResource(R.string.frozen),
        "pending" to stringResource(R.string.pending),
        "cancelled" to stringResource(R.string.cancelled)
    )
    val categoryOptions = listOf(
        "储蓄卡" to stringResource(R.string.debit_card),
        "信用卡" to stringResource(R.string.credit_card)
    )
    val billingDayOptions = listOf(0 to stringResource(R.string.billing_day_unset)) +
        (1..31).map { it to stringResource(R.string.billing_day_format, it) }
    val isCreditCard = !noCard && cardCategory == "信用卡"
    val ungroupedLabel = stringResource(R.string.ungrouped_cards)
    val groupOptions = listOf("" to ungroupedLabel) + groups.map { it.id to it.name }
    val selectedBuiltinBank = remember(bank, ctx) { BuiltinBankCatalog.find(ctx, bank) }
    val customBankSelected = bank.isNotBlank() && (bankLogoPath.isNotBlank() || selectedBuiltinBank == null)

    @Composable
    fun EditorBody(modifier: Modifier) {
        Column(modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (asPage) {
                        IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.ArrowBack, stringResource(R.string.close), modifier = Modifier.size(22.dp))
                        }
                    }
                    Text(
                        if (initial == null) stringResource(R.string.add_card) else stringResource(R.string.edit_card),
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = {
                            if (nfcActive) stopNfcReader() else startNfcReader()
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            if (nfcActive) Icons.Default.Close else Icons.Default.CreditCard,
                            null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (nfcActive) stringResource(R.string.nfc_stop) else stringResource(R.string.nfc_fill),
                            fontSize = 12.sp
                        )
                    }
                }
                if (nfcStatusText.isNotBlank()) {
                    Text(
                        nfcStatusText,
                        fontSize = 11.sp,
                        color = if (nfcActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val selectedGroupLabel = groupOptions.find { it.first == groupId }?.second ?: ungroupedLabel
                DropdownField(stringResource(R.string.card_group), groupOptions.map { it.second }, selectedGroupLabel) { selected ->
                    groupId = groupOptions.find { it.second == selected }?.first ?: ""
                }

                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.card_face_orientation), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FilterChip(
                        selected = imageOrientation == "horizontal",
                        onClick = { imageOrientation = "horizontal" },
                        label = { Text(stringResource(R.string.horizontal)) }
                    )
                    FilterChip(
                        selected = imageOrientation == "vertical",
                        onClick = { imageOrientation = "vertical" },
                        label = { Text(stringResource(R.string.vertical)) }
                    )
                }

                OutlinedButton(
                    onClick = {
                        resourceApplyError = ""
                        showResourceLibrary = true
                    },
                    enabled = !applyingResource,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (applyingResource) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.PhotoLibrary, null, modifier = Modifier.size(17.dp))
                    }
                    Spacer(Modifier.width(7.dp))
                    Text(stringResource(R.string.resource_library_choose))
                }
                if (resourceApplyError.isNotBlank()) {
                    Text(resourceApplyError, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                }

                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val previewModifier = if (imageOrientation == "vertical") {
                        Modifier.width(148.dp).aspectRatio(0.63f)
                    } else {
                        Modifier.fillMaxWidth().aspectRatio(1.586f)
                    }
                    Box(previewModifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(listOf(nc.bg, nc.bg.copy(.7f))))
                        .clickable { cardPicker.launch("image/*") },
                        contentAlignment = Alignment.Center) {
                        if (cardBmp != null) Image(cardBmp.asImageBitmap(), null,
                            Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                            filterQuality = FilterQuality.Medium)
                        else Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.AddPhotoAlternate, null, tint = nc.text.copy(.5f), modifier = Modifier.size(28.dp))
                            Text(stringResource(R.string.upload_card_face), fontSize = 12.sp, color = nc.text.copy(.5f))
                        }
                        if (imagePath.isNotEmpty()) Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.BottomEnd) {
                            Surface(shape = RoundedCornerShape(999.dp), color = Color.Black.copy(alpha = 0.55f)) {
                                TextButton(onClick = {
                                    imagePath = ""
                                    imageRefreshKey++
                                }, colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                                    Text(stringResource(R.string.remove), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(.25f))

                val bankError = submitted && bank.isBlank()
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                1.dp,
                                if (bankError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { showBankPicker = true }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (bankBmp != null) {
                                Image(
                                    bankBmp.asImageBitmap(),
                                    null,
                                    Modifier.fillMaxSize().padding(2.dp),
                                    contentScale = ContentScale.Fit,
                                    filterQuality = FilterQuality.High
                                )
                            } else {
                                Icon(
                                    Icons.Default.AccountBalance,
                                    null,
                                    tint = nc.text.copy(alpha = 0.65f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                stringResource(R.string.bank_name),
                                fontSize = 11.sp,
                                color = if (bankError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                bank.ifBlank { stringResource(R.string.select_bank) },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (bank.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (customBankSelected) {
                            Text(
                                stringResource(R.string.custom_bank),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (bankError) {
                        Text(
                            stringResource(R.string.required_field),
                            modifier = Modifier.padding(start = 12.dp),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                OutlinedTextField(value = cardTypeName, onValueChange = { cardTypeName = it },
                    label = { Text(stringResource(R.string.card_type_name_hint)) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))

                // 卡组织 + 币种（一行两列）
                if (!noCard) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) {
                            val selectedNetworkLabel = networkOptions.find { it.first == network }?.second ?: network
                            DropdownField(
                                stringResource(R.string.network),
                                networkOptions.map { it.second },
                                selectedNetworkLabel,
                                highlighted = NfcFilledField.NETWORK in nfcFilledFields
                            ) { selected ->
                                nfcFilledFields = nfcFilledFields - NfcFilledField.NETWORK
                                network = networkOptions.find { it.second == selected }?.first ?: selected
                            }
                        }
                        Box(Modifier.weight(1f)) {
                            DropdownField(
                                stringResource(R.string.currency),
                                currencies,
                                currency,
                                highlighted = NfcFilledField.CURRENCY in nfcFilledFields
                            ) {
                                nfcFilledFields = nfcFilledFields - NfcFilledField.CURRENCY
                                currency = it
                            }
                        }
                    }
                } else {
                    // 纯账户只选币种
                    DropdownField(
                        stringResource(R.string.currency),
                        currencies,
                        currency,
                        highlighted = NfcFilledField.CURRENCY in nfcFilledFields
                    ) {
                        nfcFilledFields = nfcFilledFields - NfcFilledField.CURRENCY
                        currency = it
                    }
                }
                // 状态 + 卡号（一行两列）
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        DropdownField(stringResource(R.string.status), statuses.map { it.second }, statuses.find { it.first == status }?.second ?: stringResource(R.string.normal)) { sel ->
                            status = statuses.find { it.second == sel }?.first ?: "active"
                        }
                    }
                    if (!noCard) {
                        OutlinedTextField(
                            value = tail,
                            onValueChange = {
                                if (it.length <= 4 && it.all(Char::isDigit)) {
                                    nfcFilledFields = nfcFilledFields - NfcFilledField.TAIL
                                    tail = it
                                }
                            },
                            label = { Text(stringResource(R.string.tail_number)) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = nfcTextFieldColors(NfcFilledField.TAIL in nfcFilledFields)
                        )
                    }
                }
                // 卡种 + 有效期（同一行，纯账户时不显示卡种）
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!noCard) {
                        Box(Modifier.weight(1f)) {
                            val selectedCategoryLabel = categoryOptions.find { it.first == cardCategory }?.second
                                ?: categoryOptions.first().second
                            DropdownField(stringResource(R.string.card_category), categoryOptions.map { it.second },
                                selectedCategoryLabel,
                                highlighted = NfcFilledField.CATEGORY in nfcFilledFields
                            ) { selected ->
                                nfcFilledFields = nfcFilledFields - NfcFilledField.CATEGORY
                                val nextCategory = categoryOptions.find { it.second == selected }?.first ?: selected
                                cardCategory = nextCategory
                                if (nextCategory != "信用卡") {
                                    creditLimitText = ""
                                    billingDay = 0
                                    repaymentDay = 0
                                }
                            }
                        }
                    }
                    // 有效期输入框
                    OutlinedTextField(
                        value = expiryDate,
                        onValueChange = {
                            val filtered = it.filter { ch -> ch.isDigit() || ch == '/' }.take(5)
                            nfcFilledFields = nfcFilledFields - NfcFilledField.EXPIRY
                            expiryDate = filtered
                        },
                        label = { Text(stringResource(R.string.expiry_optional)) },
                        placeholder = { Text("MM/YY", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(.4f)) },
                        trailingIcon = {
                            Row {
                                if (expiryDate.isNotEmpty()) IconButton(onClick = {
                                    expiryDate = ""
                                    nfcFilledFields = nfcFilledFields - NfcFilledField.EXPIRY
                                }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Clear, null, modifier = Modifier.size(14.dp))
                                }
                                IconButton(onClick = { showExpiryPicker = true }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        modifier = Modifier.weight(if (noCard) 1f else 1f)
                            .clickable { showExpiryPicker = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = nfcTextFieldColors(NfcFilledField.EXPIRY in nfcFilledFields)
                    )
                }
                if (isCreditCard) {
                    OutlinedTextField(
                        value = creditLimitText,
                        onValueChange = { creditLimitText = sanitizeCreditLimitInput(it) },
                        label = { Text(stringResource(R.string.credit_limit_optional)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) {
                            val selectedBillingDayLabel = billingDayOptions
                                .firstOrNull { it.first == billingDay }
                                ?.second ?: stringResource(R.string.billing_day_unset)
                            DropdownField(
                                stringResource(R.string.billing_day),
                                billingDayOptions.map { it.second },
                                selectedBillingDayLabel
                            ) { selected ->
                                billingDay = billingDayOptions.firstOrNull { it.second == selected }?.first ?: 0
                            }
                        }
                        Box(Modifier.weight(1f)) {
                            val selectedRepaymentDayLabel = billingDayOptions
                                .firstOrNull { it.first == repaymentDay }
                                ?.second ?: stringResource(R.string.billing_day_unset)
                            DropdownField(
                                stringResource(R.string.repayment_day),
                                billingDayOptions.map { it.second },
                                selectedRepaymentDayLabel
                            ) { selected ->
                                repaymentDay = billingDayOptions.firstOrNull { it.second == selected }?.first ?: 0
                            }
                        }
                    }
                }
                OutlinedTextField(value = note, onValueChange = { note = it },
                    label = { Text(stringResource(R.string.note_optional)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = isVirtual, onCheckedChange = { isVirtual = it }); Text(stringResource(R.string.virtual_card), fontSize = 13.sp) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = noCard, onCheckedChange = {
                            noCard = it
                            if (it) {
                                tail = ""
                                cardCategory = ""
                                creditLimitText = ""
                                billingDay = 0
                                repaymentDay = 0
                                nfcFilledFields = nfcFilledFields - NfcFilledField.TAIL - NfcFilledField.CATEGORY - NfcFilledField.NETWORK
                            }
                        })
                        Text(stringResource(R.string.pure_account), fontSize = 13.sp)
                    }
                }


                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    onDelete?.let { OutlinedButton(onClick = it, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text(stringResource(R.string.delete), color = ColorRed) } }
                    Button(onClick = {
                        submitted = true
                        if (bank.isNotBlank()) {
                            val savingCategory = if (noCard) "" else cardCategory.ifBlank { "储蓄卡" }
                            val savingCredit = savingCategory == "信用卡"
                            onSave(CardFormData(bank.trim(), network, currency,
                                if (noCard) "" else tail.trim(), note.trim(), status, isVirtual, noCard,
                                imagePath, bankLogoPath, cardTypeName.trim(),
                                expiryDate, savingCategory,
                                imageOrientation, groupId,
                                if (savingCredit) creditLimitText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0 else 0.0,
                                if (savingCredit) billingDay.takeIf { it in 1..31 } ?: 0 else 0,
                                if (savingCredit) repaymentDay.takeIf { it in 1..31 } ?: 0 else 0))
                        }
                    }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text(stringResource(R.string.save)) }
                }
            }
        }

    if (showResourceLibrary) {
        CardResourcePickerDialog(
            onDismiss = { showResourceLibrary = false },
            onSelect = { item ->
                showResourceLibrary = false
                applyingResource = true
                resourceApplyError = ""
                scope.launch {
                    val copiedPaths = withContext(Dispatchers.IO) {
                        val resourceCardId = initial?.id ?: "tmp_${System.currentTimeMillis()}"
                        val cardImage = CardResourcePackManager.copyItemImage(
                            context = ctx,
                            item = item,
                            cardId = resourceCardId
                        )
                        val bankLogo = if (BuiltinBankCatalog.logoAsset(ctx, item.bank).isNotBlank()) {
                            ""
                        } else {
                            CardResourcePackManager.copyItemBankLogo(
                                context = ctx,
                                item = item,
                                cardId = resourceCardId
                            )
                        }
                        cardImage to bankLogo
                    }
                    val copiedPath = copiedPaths.first
                    if (copiedPath.isBlank()) {
                        resourceApplyError = resourceApplyFailedText
                    } else {
                        imagePath = copiedPath
                        imageRefreshKey++
                        bankLogoPath = copiedPaths.second
                        bankLogoRefreshKey++
                        if (item.bank.isNotBlank()) bank = item.bank
                        if (item.name.isNotBlank()) cardTypeName = item.name
                        network = item.network
                        currency = item.currency
                        noCard = false
                        if (item.cardCategory.isNotBlank()) {
                            cardCategory = item.cardCategory
                            if (cardCategory != "信用卡") {
                                creditLimitText = ""
                                billingDay = 0
                                repaymentDay = 0
                            }
                        }
                        imageOrientation = item.imageOrientation
                        submitted = false
                    }
                    applyingResource = false
                }
            }
        )
    }

    if (asPage) {
        BackHandler(onBack = {
            if (showBankPicker) showBankPicker = false else onDismiss()
        })
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            if (showBankPicker) {
                BankPickerPage(
                    banks = builtinBanks,
                    selectedBank = selectedBuiltinBank?.name.orEmpty(),
                    onBack = { showBankPicker = false },
                    onSelect = { selected ->
                        bank = selected.name
                        bankLogoPath = ""
                        bankLogoRefreshKey++
                        submitted = false
                        showBankPicker = false
                    },
                    onMissing = { showMissingBankMenu = true }
                )
            } else {
                EditorBody(Modifier.fillMaxSize().padding(20.dp))
            }
        }
    } else {
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.responsiveDialogWidth(620.dp)
            ) {
                EditorBody(Modifier.padding(20.dp))
            }
        }
    }

    if (showMissingBankMenu) {
        MissingBankDialog(
            onDismiss = { showMissingBankMenu = false },
            onCustom = {
                showMissingBankMenu = false
                showCustomBankDialog = true
            },
            onFeedback = {
                showMissingBankMenu = false
                if (!openMissingBankIssue(ctx)) showFeedbackOpenFailed = true
            }
        )
    }

    if (showCustomBankDialog) {
        CustomBankDialog(
            initialName = bank.takeIf { customBankSelected }.orEmpty(),
            initialLogoPath = bankLogoPath.takeIf { customBankSelected }.orEmpty(),
            onDismiss = { showCustomBankDialog = false },
            onSave = { customName, customLogoPath ->
                bank = customName
                bankLogoPath = customLogoPath
                bankLogoRefreshKey++
                submitted = false
                showCustomBankDialog = false
                showBankPicker = false
            }
        )
    }

    if (showFeedbackOpenFailed) {
        AlertDialog(
            onDismissRequest = { showFeedbackOpenFailed = false },
            title = { Text(stringResource(R.string.feedback_missing_bank)) },
            text = { Text(stringResource(R.string.feedback_open_failed)) },
            confirmButton = {
                TextButton(onClick = { showFeedbackOpenFailed = false }) {
                    Text(stringResource(R.string.confirm))
                }
            }
        )
    }

    // 月年选择器
    if (showExpiryPicker) {
        MonthYearPickerDialog(
            initial = expiryDate,
            onConfirm = {
                expiryDate = it
                nfcFilledFields = nfcFilledFields - NfcFilledField.EXPIRY
                showExpiryPicker = false
            },
            onDismiss = { showExpiryPicker = false }
        )
    }

    pendingNfcResult?.let { result ->
        NfcFillConfirmDialog(
            result = result,
            onDismiss = { pendingNfcResult = null },
            onConfirm = {
                val filled = mutableSetOf<NfcFilledField>()
                result.networkValue()?.let {
                    network = it
                    filled += NfcFilledField.NETWORK
                }
                result.currencyCode()?.takeIf { it in currencies }?.let {
                    currency = it
                    filled += NfcFilledField.CURRENCY
                }
                result.panLast4?.let {
                    tail = it
                    filled += NfcFilledField.TAIL
                }
                result.expiration?.let {
                    expiryDate = it
                    filled += NfcFilledField.EXPIRY
                }
                result.categoryValue()?.let {
                    cardCategory = it
                    filled += NfcFilledField.CATEGORY
                }
                if (filled.isNotEmpty()) noCard = false
                nfcFilledFields = nfcFilledFields + filled
                nfcStatusText = ""
                pendingNfcResult = null
            }
        )
    }
}

@Composable
private fun CardResourcePickerDialog(
    onDismiss: () -> Unit,
    onSelect: (CardResourceItem) -> Unit
) {
    val appContext = LocalContext.current.applicationContext
    var resourceItems by remember { mutableStateOf<List<CardResourceItem>?>(null) }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    val cs = MaterialTheme.colorScheme

    LaunchedEffect(Unit) {
        resourceItems = withContext(Dispatchers.IO) {
            CardResourcePackManager.loadItems(appContext)
        }
    }
    val filtered = remember(resourceItems, query, category) {
        val normalizedQuery = query.trim()
        resourceItems.orEmpty().filter { item ->
            (category.isBlank() || item.cardCategory == category) &&
                (normalizedQuery.isBlank() || listOf(
                    item.bank,
                    item.bankEnglish,
                    item.name,
                    item.network,
                    item.level,
                    item.country,
                    item.source
                ).any { it.contains(normalizedQuery, ignoreCase = true) })
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        BackHandler(onBack = onDismiss)
        Surface(
            color = cs.background,
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                Row(
                    Modifier.fillMaxWidth().height(56.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.close))
                    }
                    Text(
                        stringResource(R.string.resource_library),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    if (resourceItems != null) {
                        Text(
                            stringResource(R.string.resource_library_count, filtered.size),
                            fontSize = 12.sp,
                            color = cs.onSurfaceVariant
                        )
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.resource_library_search)) },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "" to stringResource(R.string.resource_library_all),
                        "储蓄卡" to stringResource(R.string.debit_card),
                        "信用卡" to stringResource(R.string.credit_card)
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = category == value,
                            onClick = { category = value },
                            label = { Text(label) }
                        )
                    }
                }

                when {
                    resourceItems == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    resourceItems!!.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.resource_library_empty),
                            color = cs.onSurfaceVariant
                        )
                    }
                    filtered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.resource_library_no_results),
                            color = cs.onSurfaceVariant
                        )
                    }
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(148.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filtered, key = { "${it.packId}:${it.id}" }) { item ->
                            CardResourceTile(item = item, onClick = { onSelect(item) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardResourceTile(item: CardResourceItem, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bitmap = rememberBitmap(item.imagePath, 0, 420)
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(cs.surface)
            .border(1.dp, cs.outline.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1.586f)
                .clip(RoundedCornerShape(6.dp))
                .background(cs.surfaceVariant.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.Medium
                )
            } else {
                Icon(Icons.Default.CreditCard, null, tint = cs.onSurfaceVariant.copy(alpha = 0.4f))
            }
        }
        Text(
            item.name.ifBlank { item.bank },
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            item.bank,
            fontSize = 10.sp,
            color = cs.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SmallTag(item.network, cs.primary.copy(alpha = 0.08f), cs.primary)
            if (item.cardCategory.isNotBlank()) {
                SmallTag(item.cardCategory, cs.outline.copy(alpha = 0.14f), cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun NfcFillConfirmDialog(
    result: EmvProbeResult,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val rows = buildList {
        result.networkValue()?.let {
            add(stringResource(R.string.network) to if (it == "银联") stringResource(R.string.network_unionpay) else it)
        }
        result.currencyCode()?.let { add(stringResource(R.string.currency) to it) }
        result.panLast4?.let { add(stringResource(R.string.tail_number) to it) }
        result.expiration?.let { add(stringResource(R.string.expiry_optional) to it) }
        result.categoryValue()?.let {
            add(stringResource(R.string.card_category) to when (it) {
                "储蓄卡" -> stringResource(R.string.debit_card)
                "信用卡" -> stringResource(R.string.credit_card)
                else -> it
            })
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.nfc_result_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(if (result.success) R.string.nfc_success else R.string.nfc_partial),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                rows.forEach { (label, value) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Text(stringResource(R.string.nfc_editable_hint), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.nfc_apply_to_form))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

// ══════════════════════════════════════════════════════════
// 月年选择器 Dialog
// ══════════════════════════════════════════════════════════
@Composable
fun MonthYearPickerDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    val now = java.time.LocalDate.now()
    // 解析现有值 "MM/yy"
    val initMonth = initial.substringBefore("/").toIntOrNull() ?: now.monthValue
    val initYear  = initial.substringAfter("/").toIntOrNull()?.let { if (it < 100) 2000 + it else it }
                    ?: now.year

    var selYear  by remember { mutableStateOf(initYear) }
    var selMonth by remember { mutableStateOf(initMonth) }
    val months   = List(12) { (it + 1).toString().padStart(2, '0') }
    val cs = MaterialTheme.colorScheme

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = cs.surface,
            modifier = Modifier.responsiveDialogWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.select_expiry), fontWeight = FontWeight.Bold, fontSize = 16.sp)

                // 年份选择行
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { selYear-- }) {
                        Icon(Icons.Default.ChevronLeft, null, tint = cs.primary)
                    }
                    Text(stringResource(R.string.year_format, selYear), fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface)
                    IconButton(onClick = { selYear++ }) {
                        Icon(Icons.Default.ChevronRight, null, tint = cs.primary)
                    }
                }

                // 月份 3×4 格
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(months) { m ->
                        val idx = months.indexOf(m) + 1
                        val selected = idx == selMonth
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) cs.primary else cs.surfaceVariant)
                                .clickable { selMonth = idx }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(m, fontSize = 11.sp,
                                color = if (selected) Color.White else cs.onSurfaceVariant,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }

                // 按钮
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)) { Text(stringResource(R.string.cancel)) }
                    Button(onClick = {
                        val mm = selMonth.toString().padStart(2, '0')
                        val yy = (selYear % 100).toString().padStart(2, '0')
                        onConfirm("$mm/$yy")
                    }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                        Text(stringResource(R.string.confirm))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownField(
    label: String,
    options: List<String>,
    selected: String,
    highlighted: Boolean = false,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(value = selected, onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(), shape = RoundedCornerShape(12.dp),
            colors = nfcTextFieldColors(highlighted))
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt -> DropdownMenuItem(text = { Text(opt) }, onClick = { onSelect(opt); expanded = false }) }
        }
    }
}

@Composable
private fun nfcTextFieldColors(highlighted: Boolean): TextFieldColors {
    val cs = MaterialTheme.colorScheme
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = if (highlighted) cs.primary else cs.primary,
        unfocusedBorderColor = if (highlighted) cs.primary else cs.outline,
        focusedContainerColor = if (highlighted) cs.primary.copy(alpha = 0.08f) else Color.Transparent,
        unfocusedContainerColor = if (highlighted) cs.primary.copy(alpha = 0.08f) else Color.Transparent
    )
}

private fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

private fun EmvProbeResult.hasFillableFields(): Boolean =
    networkValue() != null || currencyCode() != null || !panLast4.isNullOrBlank() ||
        !expiration.isNullOrBlank() || categoryValue() != null

private fun EmvProbeResult.networkValue(): String? =
    when (organization) {
        "UnionPay" -> "银联"
        "Visa" -> "Visa"
        "Mastercard" -> "Mastercard"
        "AMEX" -> "AMEX"
        "JCB" -> "JCB"
        "Discover" -> "Discover"
        else -> null
    }

private fun EmvProbeResult.currencyCode(): String? =
    currency?.substringBefore(" ")?.takeIf { it.length == 3 && it.all(Char::isLetter) }
        ?: when (currency?.filter(Char::isDigit)?.takeLast(3)) {
            "156" -> "CNY"
            "840" -> "USD"
            "344" -> "HKD"
            "446" -> "MOP"
            "392" -> "JPY"
            "978" -> "EUR"
            "826" -> "GBP"
            "702" -> "SGD"
            else -> null
        }

private fun EmvProbeResult.categoryValue(): String? =
    when (cardKind) {
        "Debit" -> "储蓄卡"
        "Credit" -> "信用卡"
        else -> null
    }


@Composable
fun Badge(bg: Color, text: String, textColor: Color) {
    Box(Modifier.clip(RoundedCornerShape(3.dp)).background(bg).padding(horizontal = 4.dp, vertical = 1.dp)) {
        Text(text, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, color = textColor)
    }
}
