package com.fitnessrider.ui.paywall

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fitnessrider.model.PaywallPricing
import com.fitnessrider.theme.*
import com.fitnessrider.ui.components.TopNavBar

@Composable
fun PaywallScreen(
    onDismiss: () -> Unit,
    onActivateClick: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CanvasWhite)
        ) {
            TopNavBar(
                title = "VIP 專業方案",
                leading = {
                    TextButton(onClick = onDismiss) {
                        Text(text = "關閉", color = Color.White, fontSize = 16.sp)
                    }
                }
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        imageVector = Icons.Default.WorkspacePremium,
                        contentDescription = null,
                        tint = TopBarGreen,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "解鎖 FitnessRider 專業教練工具",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary
                    )
                }

                PricingPlanCard(planName = "月繳", priceTwd = PaywallPricing.MONTHLY_PRICE_TWD, periodLabel = "/ 月")
                PricingPlanCard(planName = "季繳", priceTwd = PaywallPricing.QUARTERLY_PRICE_TWD, periodLabel = "/ 季")
                PricingPlanCard(
                    planName = "年繳",
                    priceTwd = PaywallPricing.YEARLY_PRICE_TWD,
                    periodLabel = "/ 年",
                    badgeText = PaywallPricing.YEARLY_BADGE_TEXT,
                    highlighted = true
                )

                Surface(
                    color = CardHeaderBackground,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = "VIP 專屬權益", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        PaywallPricing.BENEFITS.forEach { benefit ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = TopBarGreenDark,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = benefit, fontSize = 13.sp, color = TextPrimary)
                            }
                        }
                    }
                }

                Button(
                    onClick = onActivateClick,
                    colors = ButtonDefaults.buttonColors(containerColor = TopBarGreen),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(text = "🔑 輸入授權序號開通", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                Text(
                    text = "商店訂閱功能尚未開放，請先透過授權序號開通 VIP；未來商店上架後可直接於此完成訂閱。",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun PricingPlanCard(
    planName: String,
    priceTwd: Int,
    periodLabel: String,
    badgeText: String? = null,
    highlighted: Boolean = false
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = if (highlighted) TopBarGreen.copy(alpha = 0.08f) else CardBackground),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (highlighted) 2.dp else 1.dp,
                color = if (highlighted) TopBarGreen else CardBorder,
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (badgeText != null) {
                Text(text = badgeText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TopBarGreenDark)
                Spacer(modifier = Modifier.height(6.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = planName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.weight(1f))
                Text(text = PaywallPricing.formatTwd(priceTwd), fontSize = 20.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                Text(text = " $periodLabel", fontSize = 13.sp, color = TextSecondary)
            }
        }
    }
}
