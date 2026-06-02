package com.loaloaloa.webhook

/**
 * The pieces extracted from a pasted Google Form "pre-filled link": the form's `formResponse`
 * POST URL plus the `entry.*` id mapped to each transaction field. Mapping is by the ORDER the
 * entries appear in the link — 1=amount, 2=bank, 3=time, 4=note — which matches the order the
 * form's questions are answered. Positions the link didn't include are left blank.
 */
data class GoogleFormLink(
    val responseUrl: String,
    val amountEntry: String = "",
    val bankEntry: String = "",
    val timeEntry: String = "",
    val noteEntry: String = "",
)

private val PUBLISHED_FORM_ID = Regex("""forms/d/e/([A-Za-z0-9_-]+)""")
private val ENTRY_KEY = Regex("""^entry\.\d+$""")

/**
 * Parse a Google Form link into its `formResponse` URL + ordered entry ids. Accepts the
 * responder/viewform link, a pre-filled link, or a formResponse link — all carry the published
 * `/forms/d/e/{id}/` id. Returns null when no published id is present (e.g. only the
 * `/forms/d/{id}/edit` editor link was pasted, which cannot be POSTed to).
 */
fun parseGoogleFormLink(raw: String): GoogleFormLink? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    val id = PUBLISHED_FORM_ID.find(trimmed)?.groupValues?.get(1) ?: return null
    val responseUrl = "https://docs.google.com/forms/d/e/$id/formResponse"

    val entries = trimmed.substringAfter('?', "")
        .split('&')
        .map { it.substringBefore('=') }
        .filter { ENTRY_KEY.matches(it) }
        .distinct()

    return GoogleFormLink(
        responseUrl = responseUrl,
        amountEntry = entries.getOrElse(0) { "" },
        bankEntry = entries.getOrElse(1) { "" },
        timeEntry = entries.getOrElse(2) { "" },
        noteEntry = entries.getOrElse(3) { "" },
    )
}
