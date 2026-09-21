package com.fitnessrider.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnessrider.theme.TopBarGreen

@Composable
fun TopNavBar(
    title: String,
    leading: @Composable RowScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
    centerContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .shadow(4.dp)
            .background(TopBarGreen)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            content = leading
        )

        Spacer(modifier = Modifier.weight(1f))

        if (centerContent != null) {
            centerContent()
        } else {
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            content = trailing
        )
    }
}
