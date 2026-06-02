package com.loaloaloa.parser

/** Result of parsing a notification's text: the absolute amount and its direction. */
data class ParsedAmount(
    val amount: Long,     // absolute VND, always >= 0
    val isIncome: Boolean,
)
