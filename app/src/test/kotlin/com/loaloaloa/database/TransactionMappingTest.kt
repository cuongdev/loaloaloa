package com.loaloaloa.database

import com.google.common.truth.Truth.assertThat
import com.loaloaloa.data.model.TransactionModel
import com.loaloaloa.database.entity.TransactionEntity
import com.loaloaloa.database.entity.toEntity
import com.loaloaloa.database.entity.toModel
import org.junit.Test

class TransactionMappingTest {

    private val model = TransactionModel(
        appId = "com.VCB",
        bankName = "Vietcombank",
        amount = 500_000,
        isIncome = true,
        rawText = "+500.000d",
        timestamp = 1_717_200_000_000,
    )

    @Test fun `model maps to entity with id 0 by default`() {
        val e = model.toEntity()
        assertThat(e.id).isEqualTo(0L)
        assertThat(e.appId).isEqualTo("com.VCB")
        assertThat(e.bankName).isEqualTo("Vietcombank")
        assertThat(e.amount).isEqualTo(500_000)
        assertThat(e.isIncome).isTrue()
        assertThat(e.rawText).isEqualTo("+500.000d")
        assertThat(e.timestamp).isEqualTo(1_717_200_000_000)
    }

    @Test fun `entity maps back to model dropping id`() {
        val e = TransactionEntity(
            id = 42,
            appId = "com.mbmobile",
            bankName = "MB Bank",
            amount = 1_200_000,
            isIncome = false,
            rawText = "-1.200.000d",
            timestamp = 1_717_300_000_000,
        )
        val m = e.toModel()
        assertThat(m).isEqualTo(
            TransactionModel(
                appId = "com.mbmobile",
                bankName = "MB Bank",
                amount = 1_200_000,
                isIncome = false,
                rawText = "-1.200.000d",
                timestamp = 1_717_300_000_000,
            )
        )
    }

    @Test fun `round trip model to entity to model preserves fields`() {
        assertThat(model.toEntity().toModel()).isEqualTo(model)
    }
}
