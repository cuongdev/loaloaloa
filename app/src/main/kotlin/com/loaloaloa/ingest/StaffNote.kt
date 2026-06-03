package com.loaloaloa.ingest

/**
 * Pure helper that stamps the names of the employees currently on shift onto a transaction note.
 * Kept side-effect-free (like [AnnouncePolicy]) so it is trivially unit-tested; the only caller
 * is [TransactionIngestor], which reads the active-staff list from settings and passes it here.
 */
object StaffNote {

    private const val PREFIX = "NV: "
    private const val SEPARATOR = " — "

    /**
     * Append the active employees to [base]. Names are trimmed, blanks dropped, and duplicates
     * removed (preserving first-seen order). With no usable names the [base] is returned unchanged,
     * so a non-staff device (empty [activeStaff]) keeps its note exactly as before.
     */
    fun combine(base: String, activeStaff: List<String>): String {
        val names = activeStaff.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (names.isEmpty()) return base
        val tag = PREFIX + names.joinToString(", ")
        return if (base.isBlank()) tag else base + SEPARATOR + tag
    }
}
