package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Header with Gradient
        Card(
            modifier = Modifier.fillMaxWidth(),
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
                                Color(0xFF2E7D32)
                            )
                        )
                    )
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        color = Color(0x28FFFFFF),
                        shape = CircleShape,
                        modifier = Modifier.size(54.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .padding(14.dp)
                                .size(26.dp)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = stringResource(R.string.app_tagline),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xCCFFFFFF)
                        )
                    }
                }
            }
        }

        // Section 1: Accounting Engine
        Text(
            text = "المحرك المحاسبي والرياضي",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        ModernSettingsItem(
            title = "معالجة دقيقة بنظام Long (64-bit)",
            subtitle = "يتم تسجيل كافة المبالغ كأعداد صحيحة تمنع تماماً أخطاء الفواصل العشرية (IEEE-754 Floating-point errors)",
            icon = Icons.Filled.Calculate,
            iconTint = MaterialTheme.colorScheme.primary
        )

        ModernSettingsItem(
            title = "حماية تجاوز الذاكرة (Overflow Detection)",
            subtitle = "تستخدم العمليات الحسابية Math.addExact و Math.subtractExact لكشف أي تجاوز لحدود الذاكرة فوراً",
            icon = Icons.Filled.Security,
            iconTint = Color(0xFF0284C7)
        )

        ModernSettingsItem(
            title = stringResource(R.string.settings_currency_info),
            subtitle = "العملة المعتمدة هي الدينار العراقي (IQD)، وتُعرض الفواصل الآلية للملايين والآلاف",
            icon = Icons.Filled.AccountBalanceWallet,
            iconTint = Color(0xFFD97706)
        )

        // Section 2: Storage & Audit
        Text(
            text = "قاعدة البيانات وسجل التدقيق",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        ModernSettingsItem(
            title = stringResource(R.string.settings_db_version),
            subtitle = "قاعدة بيانات SQLite محلية مدمجة عبر Room، تعمل بسرعة فائقة وبدون الحاجة لإنترنت",
            icon = Icons.Filled.Storage,
            iconTint = MaterialTheme.colorScheme.secondary
        )

        ModernSettingsItem(
            title = stringResource(R.string.settings_audit_info),
            subtitle = "توثيق شامل قبل التعديل وبعده بلقطات JSON عبر مكتبة Moshi لمنع التلاعب وتتبع الحركات",
            icon = Icons.Filled.History,
            iconTint = Color(0xFF7C3AED)
        )

        // Section 3: Interface & Design Identity
        Text(
            text = "واجهة المستخدم والهوية البصرية",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        ModernSettingsItem(
            title = "خط القاهرة العربي (Cairo Font)",
            subtitle = "مدمج محلياً في التطبيق لتقديم قراءة عربية فخمة ومريحة للعين في المحلات والأسواق",
            icon = Icons.Filled.FormatPaint,
            iconTint = MaterialTheme.colorScheme.primary
        )

        ModernSettingsItem(
            title = "دعم كامل للغة العربية (RTL First)",
            subtitle = "تصميم موجه من اليمين إلى اليسار مع بطاقات واضحة ومسافات متوافقة مع Material 3",
            icon = Icons.Filled.CheckCircle,
            iconTint = Color(0xFF15803D)
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun ModernSettingsItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
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
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
