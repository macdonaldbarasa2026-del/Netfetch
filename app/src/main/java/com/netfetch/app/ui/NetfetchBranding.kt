package com.netfetch.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netfetch.app.ui.theme.CreamBackground
import com.netfetch.app.ui.theme.TextMuted

@Composable
fun NetfetchWatermark() {
    Text(
        text = "© 2026 Created by MacDonald | Powered by Mixfia",
        fontSize = 10.sp,
        fontStyle = FontStyle.Italic,
        color = TextMuted.copy(alpha = 0.85f),
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = Modifier
            .fillMaxWidth()
            .background(CreamBackground)
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp)
    )
}
