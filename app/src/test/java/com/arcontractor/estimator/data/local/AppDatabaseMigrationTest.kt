package com.arcontractor.estimator.data.local

import android.os.Build
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
class AppDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun `migration 2 to 3 preserves projects and defaults currency to USD`() {
        helper.createDatabase(DB_NAME, 2).apply {
            execSQL(
                """
                INSERT INTO projects (
                    id, name, clientName, contractorName, contractorContact,
                    contractorLicense, taxRate, taxPolicy,
                    createdAtEpochMillis, updatedAtEpochMillis
                ) VALUES (
                    'p1', 'Existing job', 'Client A', 'Builder Co', 'hello@builder.example',
                    'GC-1', 0.08, 'TAX_TOTAL_SUBTOTAL', 1000, 2000
                )
                """.trimIndent()
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            DB_NAME,
            3,
            true,
            AppDatabase.MIGRATION_2_3
        )

        migrated.query("SELECT id, name, taxRate, currencyCode FROM projects").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("p1", cursor.getString(0))
            assertEquals("Existing job", cursor.getString(1))
            assertEquals(0.08, cursor.getDouble(2), 0.0)
            assertEquals("USD", cursor.getString(3))
        }
        migrated.close()
    }

    private companion object {
        const val DB_NAME = "migration-test.db"
    }
}
