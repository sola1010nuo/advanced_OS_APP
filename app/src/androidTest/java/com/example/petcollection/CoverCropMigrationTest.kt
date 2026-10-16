package com.example.petcollection

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.example.petcollection.data.CollectionDatabase
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class CoverCropMigrationTest {
    @Test fun migrationAddsOptionalCoverRegionWithoutChangingExistingCoverOrPhoto() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "cover-migration-${UUID.randomUUID()}.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name).callback(object : SupportSQLiteOpenHelper.Callback(5) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE breed_covers (breedId TEXT NOT NULL PRIMARY KEY, encounterId TEXT NOT NULL)")
                    db.execSQL("INSERT INTO breed_covers VALUES ('corgi', 'existing-photo')")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build())
        try {
            val db = helper.writableDatabase
            CollectionDatabase.MIGRATION_5_6.migrate(db)
            db.query("SELECT breedId, encounterId, cropRegion FROM breed_covers").use {
                assertTrue(it.moveToFirst())
                assertEquals("corgi", it.getString(0))
                assertEquals("existing-photo", it.getString(1))
                assertTrue(it.isNull(2))
                assertFalse(it.moveToNext())
            }
        } finally { helper.close(); context.deleteDatabase(name) }
    }
}
