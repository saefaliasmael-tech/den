package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.navigation.AppDestinations

@Composable
fun PaymentsScreen(
    onNavigateToDestination: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Icon and Title
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = CircleShape,
            modifier = Modifier.size(72.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Payments,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(18.dp)
                    .size(36.dp)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.payments_foundational_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "سندات القبض والتخصيص المالي للفواتير",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Philosophy Cards
        AccountingRuleCard(
            title = "التخصيص التلقائي بالأقدمية (FIFO Allocation)",
            description = "تُوزع مبالغ الدفعات بترتيب الأقدمية على الفواتير المفتوحة للعميل لسداد الذمم القديمة أولاً، مما يضمن تنظيم الدفتر تلقائياً.",
            icon = Icons.Filled.FormatListNumbered,
            tint = MaterialTheme.colorScheme.primary
        )

        AccountingRuleCard(
            title = "حظر الرصيد الدائن التام (Zero Credit Policy)",
            description = "يرفض النظام أي دفعة تتجاوز إجمالي الدين المستحق على الزبون، لمنع نشوء أرصدة سالبة وحفظ سلامة الميزانية.",
            icon = Icons.Filled.Block,
            tint = Color(0xFFDC2626)
        )

        AccountingRuleCard(
            title = "إلغاء السند وتخصيصاته معاً (Atomic Reversal)",
            description = "عند إلغاء سند قبض، يتم إلغاء كافة تخصيصاته للفواتير ذرياً في نفس العملية، وإعادة احتساب ذمة العميل دون أي تأخير.",
            icon = Icons.Filled.SyncAlt,
            tint = Color(0xFF059669)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { onNavigateToDestination(AppDestinations.INVOICES) },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.testTag("payments_go_to_invoices_button")
        ) {
            Icon(imageVector = Icons.Filled.ReceiptLong, contentDescription = null)
            Spacer(modifier = Modifier.size(8.dp))
            Text(stringResource(R.string.nav_invoices))
        }
    }
}
