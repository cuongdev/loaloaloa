package com.loaloaloa.ui.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable

/** "HH:mm" for a minute-of-day (0..1439). Shared by the quiet-hours and shift pickers. */
internal fun minutesLabel(minutes: Int): String =
    "%02d:%02d".format((minutes / 60) % 24, minutes % 60)

/** A 24-hour clock dialog returning the chosen time as a minute-of-day to [onConfirm]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimePickerDialog(
    initialMinutes: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Huỷ") } },
        text = { TimePicker(state = state) },
    )
}
