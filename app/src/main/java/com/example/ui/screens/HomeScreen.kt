package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.navigation.AppDestinations
import com.example.ui.theme.AmberDebt
import com.example.ui.theme.GreenCleared
import com.example.ui.viewmodel.HomeDashboardState
import com.example.ui.viewmodel.HomeViewModel
import com.example.ui.viewmodel.TopDebtorItem
import java.util.Locale

@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel,
    onNavigateToDestination: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by homeViewModel.dashboardState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            // Modern Hero Balance Card
            HeroBalanceCard(
                state = state,
                onNavigateToCustomers = { onNavigateToDestination(AppDestinations.CUSTOMERS) },
                onNavigateToInvoices = { onNavigateToDestination(AppDestinations.INVOICES) }
            )

            // Statistics Summary Grid (4 Mini Cards)
            StatsGridSection(state = state)

            // Debt Distribution Chart / Visual Ledger Status
            DebtDistributionCard(
                state = state,
                onNavigateToCustomers = { onNavigateToDestination(AppDestinations.CUSTOMERS) }
            )

            // Quick Actions Section Title
            Text(
                text = stringResource(R.string.home_quick_sections),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            // Navigation Grid Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionModernCard(
                    title = stringResource(R.string.nav_customers),
                    subtitle = stringResource(R.string.customers_count_badge, state.totalCustomers),
                    icon = Icons.Filled.People,
                    tag = "home_nav_customers",
                    badgeColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToDestination(AppDestinations.CUSTOMERS) }
                )
                QuickActionModernCard(
                    title = stringResource(R.string.nav_products),
                    subtitle = stringResource(R.string.products_count_badge, state.totalProducts),
                    icon = Icons.Filled.Inventory2,
                    tag = "home_nav_products",
                    badgeColor = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToDestination(AppDestinations.PRODUCTS) }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionModernCard(
                    title = stringResource(R.string.nav_invoices),
                    subtitle = "المبيعات الآجلة",
                    icon = Icons.Filled.ReceiptLong,
                    tag = "home_nav_invoices",
                    badgeColor = Color(0xFF0284C7),
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToDestination(AppDestinations.INVOICES) }
                )
                QuickActionModernCard(
                    title = stringResource(R.string.nav_payments),
                    subtitle = "سندات القبض",
                    icon = Icons.Filled.Payments,
                    tag = "home_nav_payments",
                    badgeColor = Color(0xFF059669),
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToDestination(AppDestinations.PAYMENTS) }
                )
            }

            QuickActionModernCard(
                title = stringResource(R.string.nav_settings),
                subtitle = "قاعدة البيانات والمحرك المحاسبي المحلي",
                icon = Icons.Filled.Settings,
                tag = "home_nav_settings",
                badgeColor = Color(0xFF475569),
                modifier = Modifier.fillMaxWidth(),
                onClick = { onNavigateToDestination(AppDestinations.SETTINGS) }
            )

            Spacer(modifier = Modifier.height(4.dp))

            // System Trust & Offline-first Guarantee Card
            SystemGuaranteeCard()
        }
    }
}

@Composable
private fun HeroBalanceCard(
    state: HomeDashboardState,
    onNavigateToCustomers: () -> Unit,
    onNavigateToInvoices: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hero_balance_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF0B3A13),
                            Color(0xFF1B5E20),
                            Color(0xFF25752B)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Top Header Row with status badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0x33FFFFFF),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AccountBalanceWallet,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier
                                    .padding(6.dp)
                                    .size(18.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.home_hero_total_debt),
                            style = MaterialTheme.typography.titleSmall,
                            color = Color(0xEEFFFFFF),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0x2AFFFFFF)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(
                                        color = if (state.totalDebt > 0L) Color(0xFFFCD34D) else Color(0xFF86EFAC),
                                        shape = CircleShape
                                    )
                            )
                            Text(
                                text = if (state.totalDebt > 0L) "ذمم نشطة" else "دفتر خالص",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                }

                // Primary Currency Amount
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formatIraqiDinar(state.totalDebt),
                        style = MaterialTheme.typography.displayMedium.copy(fontSize = 32.sp),
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = stringResource(R.string.customers_currency),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xCCFFFFFF),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                // Sub-stats Pill Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0x22FFFFFF),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Text(
                                text = stringResource(R.string.home_hero_debtors_count),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xBBFFFFFF)
                            )
                            Text(
                                text = "${state.debtorsCount} زبائن",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0x22FFFFFF),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Text(
                                text = stringResource(R.string.home_hero_cleared_count),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xBBFFFFFF)
                            )
                            Text(
                                text = "${state.clearedCustomersCount} حسابات",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsGridSection(state: HomeDashboardState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatMiniCard(
            title = stringResource(R.string.home_stat_customers),
            value = state.totalCustomers.toString(),
            icon = Icons.Filled.People,
            iconTint = MaterialTheme.colorScheme.primary,
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.weight(1f)
        )
        StatMiniCard(
            title = stringResource(R.string.home_stat_products),
            value = state.totalProducts.toString(),
            icon = Icons.Filled.Inventory2,
            iconTint = MaterialTheme.colorScheme.secondary,
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatMiniCard(
    title: String,
    value: String,
    icon: ImageVector,
    iconTint: Color,
    containerColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                color = iconTint.copy(alpha = 0.12f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(22.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DebtDistributionCard(
    state: HomeDashboardState,
    onNavigateToCustomers: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AccountBalanceWallet,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.home_chart_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (state.topDebtors.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.home_top_debtors_view_all),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable(onClick = onNavigateToCustomers)
                            .padding(4.dp)
                    )
                }
            }

            if (state.totalDebt == 0L || state.topDebtors.isEmpty()) {
                // Peaceful All-Cleared State
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = GreenCleared.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = GreenCleared,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = stringResource(R.string.home_chart_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = GreenCleared,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                // Visual Stacked Distribution Bar
                SegmentedDebtBar(items = state.topDebtors)

                // List of top debtor rows
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val palette = listOf(
                        Color(0xFF1B5E20),
                        Color(0xFF2E7D32),
                        Color(0xFFD97706),
                        Color(0xFF475569)
                    )
                    state.topDebtors.forEachIndexed { index, debtor ->
                        TopDebtorRow(
                            item = debtor,
                            indicatorColor = palette.getOrElse(index) { Color(0xFF64748B) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SegmentedDebtBar(items: List<TopDebtorItem>) {
    val palette = listOf(
        Color(0xFF1B5E20),
        Color(0xFF2E7D32),
        Color(0xFFD97706),
        Color(0xFF475569)
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
    ) {
        val width = size.width
        val height = size.height
        val cornerRadius = CornerRadius(height / 2, height / 2)

        // Draw track background
        drawRoundRect(
            color = Color(0xFFE2E8E4),
            size = Size(width, height),
            cornerRadius = cornerRadius
        )

        var currentX = 0f
        items.forEachIndexed { index, item ->
            val segmentWidth = (width * (item.percentageOfTotal / 100f)).coerceAtLeast(0f)
            if (segmentWidth > 0f) {
                drawRoundRect(
                    color = palette.getOrElse(index) { Color(0xFF64748B) },
                    topLeft = Offset(currentX, 0f),
                    size = Size(segmentWidth, height),
                    cornerRadius = cornerRadius
                )
                currentX += segmentWidth
            }
        }
    }
}

@Composable
private fun TopDebtorRow(item: TopDebtorItem, indicatorColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(color = indicatorColor, shape = CircleShape)
            )
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = "%.1f%%".format(Locale.US, item.percentageOfTotal),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Text(
            text = "${formatIraqiDinar(item.debt)} ${stringResource(R.string.customers_currency)}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun QuickActionModernCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tag: String,
    badgeColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .testTag(tag)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = badgeColor.copy(alpha = 0.12f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(42.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = badgeColor,
                    modifier = Modifier
                        .padding(9.dp)
                        .size(24.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SystemGuaranteeCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = CircleShape,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(20.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.home_status_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.home_status_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

fun formatIraqiDinar(amount: Long): String {
    return String.format(Locale.US, "%,d", amount)
}
