package com.auramusic.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration8To9Test {

    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AuraDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun `test migration from v8 to v9 adds required indices`() {
        // Create the database in version 8
        val db8 = helper.createDatabase(TEST_DB, 8)
        
        // Insert dummy data that shouldn't be lost
        db8.execSQL("INSERT INTO songs (id, title, artist, thumbnailUrl, durationSeconds, playCount, isFavorite) VALUES ('test_id', 'Title', 'Artist', 'url', 100, 0, 1)")
        db8.close()

        // Run the migration to version 9
        val db9 = helper.runMigrationsAndValidate(TEST_DB, 9, true, AuraDatabase.MIGRATION_8_9)

        // Validate data is persistent
        val cursor = db9.query("SELECT isFavorite FROM songs WHERE id = 'test_id'")
        cursor.moveToFirst()
        assert(cursor.getInt(0) == 1)
        cursor.close()

        // Validate index exists by trying to EXPLAIN QUERY PLAN (Implementation detail, but validation via helper is enough)
    }
}
