package id.mysuite.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

class PluginManager(private val context: Context, private val db: Database) {
    private val root = File(context.filesDir, "plugins").apply { mkdirs() }
    private val idPattern = Regex("[a-z0-9_.-]{2,50}")

    @Synchronized fun install(input: InputStream, expectedId: String? = null): String {
        val staging = File(context.cacheDir, "plugin-staging-${System.nanoTime()}").apply { mkdirs() }
        val backupRoot = File(context.cacheDir, "plugin-backup-${System.nanoTime()}")
        var total = 0L
        var entries = 0
        try {
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries++
                    require(entries <= 2000) { "Plugin memiliki terlalu banyak file" }
                    require(!entry.name.startsWith("/") && !entry.name.contains('\\')) { "Path ZIP tidak valid" }
                    val target = File(staging, entry.name).canonicalFile
                    require(target.path.startsWith(staging.canonicalPath + File.separator) || target == staging) { "Path ZIP tidak aman" }
                    if (entry.isDirectory) target.mkdirs() else {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { out ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = zip.read(buffer)
                                if (count < 0) break
                                total += count
                                require(total <= 30L * 1024 * 1024) { "Plugin melebihi batas 30 MB" }
                                out.write(buffer, 0, count)
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
            val manifestFile = File(staging, "manifest.json")
            require(manifestFile.isFile) { "manifest.json tidak ditemukan" }
            val manifest = JSONObject(manifestFile.readText(Charsets.UTF_8))
            val id = manifest.getString("id")
            val name = manifest.getString("name").trim()
            val version = manifest.getString("version").trim()
            require(idPattern.matches(id)) { "ID plugin tidak valid" }
            require(name.isNotBlank() && name.length <= 80) { "Nama plugin tidak valid" }
            require(version.isNotBlank() && version.length <= 40) { "Versi plugin tidak valid" }
            require(expectedId == null || expectedId == id) { "ID ZIP tidak cocok dengan plugin yang dipilih untuk diperbarui" }
            require(manifest.optInt("api", 1) <= 1) { "Plugin membutuhkan API MySuite lebih baru" }
            require(File(staging, "web/index.html").isFile) { "web/index.html tidak ditemukan" }

            val destination = File(root, id)
            backupRoot.mkdirs()
            val backup = File(backupRoot, id)
            if (destination.exists()) require(destination.renameTo(backup)) { "Tidak dapat mengamankan plugin versi lama" }
            if (!staging.renameTo(destination)) {
                if (backup.exists()) backup.renameTo(destination)
                error("Gagal memasang plugin baru; versi lama dipulihkan")
            }
            try {
                val values = ContentValues().apply {
                    put("id", id); put("name", name); put("version", version)
                    put("enabled", 1); put("installed_at", System.currentTimeMillis())
                }
                db.writableDatabase.insertWithOnConflict("plugins", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            } catch (e: Exception) {
                destination.deleteRecursively()
                if (backup.exists()) backup.renameTo(destination)
                throw e
            }
            backup.deleteRecursively()
            return id
        } finally {
            staging.deleteRecursively()
            backupRoot.deleteRecursively()
        }
    }

    @Synchronized fun uninstall(id: String) {
        require(idPattern.matches(id)) { "ID plugin tidak valid" }
        val target = File(root, id)
        if (!target.exists()) { db.removePlugin(id); return }
        val trash = File(context.cacheDir, "plugin-delete-${System.nanoTime()}")
        require(target.renameTo(trash)) { "Gagal menghapus folder plugin" }
        try { db.removePlugin(id); trash.deleteRecursively() }
        catch (e: Exception) { trash.renameTo(target); throw e }
    }

    fun list(): JSONArray {
        val result = JSONArray()
        db.readableDatabase.rawQuery("SELECT id,name,version,enabled FROM plugins ORDER BY name", null).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                if (File(root, "$id/web/index.html").isFile) result.put(JSONObject()
                    .put("id", id).put("name", cursor.getString(1)).put("version", cursor.getString(2))
                    .put("enabled", cursor.getInt(3) == 1))
            }
        }
        return result
    }

    fun pluginFile(id: String): File? {
        if (!idPattern.matches(id)) return null
        val file = File(root, "$id/web/index.html").canonicalFile
        return if (file.isFile && file.path.startsWith(root.canonicalPath + File.separator)) file else null
    }
}
