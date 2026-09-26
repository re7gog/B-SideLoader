package dev.re7gog.b_sideloader.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A schema Room exported to `app/schemas`, replayed as the SQL that created it.
 *
 * This is what `MigrationTestHelper` does, minus the part that needs a device: the helper reads
 * the JSON from the test APK's assets, and a local test has no APK — `app/build.gradle.kts` puts
 * the schemas on the test classpath instead. The exported file is the frozen record of what a
 * released build created, so a database built from it is exactly what an upgrading user has.
 */
class ExportedSchema private constructor(
    val version: Int,
    /** Every statement Room ran to create this version, in order, table names filled in. */
    val createStatements: List<String>,
) {

    /**
     * Creates database [name] at this version, lets [populate] add rows, and closes it — the way
     * an older build would have left it on disk.
     */
    fun createDatabase(
        context: Context,
        name: String,
        populate: SupportSQLiteDatabase.() -> Unit = {},
    ) {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) =
                    createStatements.forEach(db::execSQL)

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                    error("$name already exists at version $oldVersion")
            })
            .build()
        FrameworkSQLiteOpenHelperFactory().create(configuration).use { helper ->
            helper.writableDatabase.populate()
        }
    }

    companion object {
        fun load(version: Int, databaseClass: Class<*> = AppsDatabase::class.java): ExportedSchema {
            val path = "${databaseClass.name}/$version.json"
            val text = checkNotNull(ExportedSchema::class.java.classLoader?.getResource(path)) {
                "$path is not on the test classpath; is app/schemas a test resource directory?"
            }.readText()
            val database = Json.parseToJsonElement(text).jsonObject.getValue("database").jsonObject
            return ExportedSchema(version, database.createStatements())
        }

        private fun JsonObject.createStatements(): List<String> = buildList {
            for (entity in objects("entities")) {
                val table = entity.string("tableName")
                add(entity.string("createSql").withName("TABLE_NAME", table))
                for (index in entity.objects("indices")) {
                    add(index.string("createSql").withName("TABLE_NAME", table))
                }
            }
            for (view in objects("views")) {
                add(view.string("createSql").withName("VIEW_NAME", view.string("viewName")))
            }
            // room_master_table and the identity hash, which Room checks on open.
            (get("setupQueries") as? JsonArray).orEmpty().forEach { add(it.jsonPrimitive.content) }
        }

        /** An optional array of objects; Room omits the key when there is nothing in it. */
        private fun JsonObject.objects(key: String): List<JsonObject> =
            (get(key) as? JsonArray).orEmpty().map { it.jsonObject }

        private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

        private fun String.withName(placeholder: String, name: String): String =
            replace("\${$placeholder}", name)
    }
}
