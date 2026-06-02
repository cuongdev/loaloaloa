package com.loaloaloa.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.loaloaloa.ui.theme.LocalAppExtraColors
import com.loaloaloa.ui.util.MoneyFormat

/**
 * A signed money figure colored by direction: income green (+), outgoing red (−),
 * using the [LocalAppExtraColors] semantic colors and [MoneyFormat] for the digits.
 */
@Composable
fun MoneyText(
    amount: Long,
    isIncome: Boolean,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    maxLines: Int = Int.MAX_VALUE,
    softWrap: Boolean = true,
) {
    val extra = LocalAppExtraColors.current
    Text(
        text = MoneyFormat.format(amount, isIncome),
        color = if (isIncome) extra.income else extra.outgoing,
        fontWeight = FontWeight.Bold,
        style = style,
        maxLines = maxLines,
        softWrap = softWrap,
        modifier = modifier,
    )
}
