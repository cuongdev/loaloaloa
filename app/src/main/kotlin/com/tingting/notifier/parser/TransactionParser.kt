package com.tingting.notifier.parser

/**
 * Extracts an absolute VND amount and its direction (income/outgoing) from a bank
 * notification's combined text. Ports the original TingTing generic regex set:
 * one ordered set of patterns covers every Vietnamese balance-change format, so a
 * single parser handles all banks.
 */
class TransactionParser {

    private data class Rule(
        val regex: Regex,
        val amountGroup: Int,
        val signGroups: List<Int>,
        val keywordGroup: Int? = null,
    )

    private val opts = setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)

    // Order matters: more specific patterns first.
    private val rules: List<Rule> = listOf(
        // R1: "TK 0123(VND) +500,000"
        Rule(Regex("""TK\s++\d++\((?:VND|VNĐ)\)\s*+([+-])\s*+([\d.,]++)""", opts), amountGroup = 2, signGroups = listOf(1)),
        // R2: "So tien GD: +500000" / "PS: +500000"
        Rule(Regex("""(?:So tien GD|Số tiền GD|PS)\s*+:\s*+([+-])?\s*+([\d.,]++)""", opts), amountGroup = 2, signGroups = listOf(1)),
        // R3: "+VND 500000" or "VND(+)500000"
        Rule(Regex("""(?:([+-])\s*+(?:VND|VNĐ)|(?:VND|VNĐ)\s*+\(([+-])\))\s*+([\d.,]++)""", opts), amountGroup = 3, signGroups = listOf(1, 2)),
        // R4: "tang/giam 500.000 VND"
        Rule(Regex("""(tăng|tang|giảm|giam)[ \t]++([\d.,]++)(?:[ \t]++(?:VND|VNĐ|[₫đ]))?(?:[ \t]++(?:vào|vao|SD)|[ \t]*+\.?+$)""", opts), amountGroup = 2, signGroups = emptyList(), keywordGroup = 1),
        // R5: "+500000 VND"
        Rule(Regex("""([+-])\s*+([\d.,]++)\s*+(?:VND|VNĐ)""", opts), amountGroup = 2, signGroups = listOf(1)),
        // R6: "+500.000đ"
        Rule(Regex("""([+-])\s*+([\d., \t]++)\s*+[₫đ]""", opts), amountGroup = 2, signGroups = listOf(1)),
        // R7: "Số tiền: 500.000đ"
        Rule(Regex("""(?:Số tiền|So tien)\s*+:?\s*+([+-])?\s*+([\d., \t]++)\s*+(?:VND|VNĐ|[₫đd])""", opts), amountGroup = 2, signGroups = listOf(1)),
    )

    private val increaseKeywords = setOf("tăng", "tang")
    private val excludeKeywords = setOf("hoá đơn", "hóa đơn", "hoa don", "kỳ thanh toán")

    /**
     * @return the parsed amount/direction, or null when the text contains no
     *   recognizable amount or is an excluded bill reminder.
     */
    fun parse(text: String): ParsedAmount? {
        if (text.isBlank()) return null
        val lower = text.lowercase()
        if (excludeKeywords.any { lower.contains(it) }) return null

        for (rule in rules) {
            val match = rule.regex.find(text) ?: continue
            val groups = match.groupValues
            val digits = groups.getOrElse(rule.amountGroup) { "" }.filter { it.isDigit() }
            val amount = digits.toLongOrNull() ?: continue
            if (amount <= 0) continue

            val isIncome = when {
                rule.keywordGroup != null -> {
                    val kw = groups.getOrElse(rule.keywordGroup) { "" }.lowercase()
                    kw in increaseKeywords
                }
                else -> {
                    val sign = rule.signGroups
                        .map { groups.getOrElse(it) { "" } }
                        .firstOrNull { it.isNotEmpty() }
                    when (sign) {
                        "-" -> false
                        else -> true // "+" or no sign → treat as income (app reports received money)
                    }
                }
            }
            return ParsedAmount(amount, isIncome)
        }
        return null
    }
}
