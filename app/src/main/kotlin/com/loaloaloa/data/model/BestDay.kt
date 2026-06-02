package com.loaloaloa.data.model

/**
 * Result of the all-time "best income day" aggregate: the local day key
 * (`yyyy-MM-dd`) and the summed income for that day. Returned by
 * [com.loaloaloa.database.dao.TransactionDao.bestIncomeDay].
 */
data class BestDay(
    val day: String,
    val total: Long,
)
