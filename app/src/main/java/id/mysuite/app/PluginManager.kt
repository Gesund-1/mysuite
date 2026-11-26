package id.mysuite.app

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipInputStream

class PluginManager(private val context: Context, private val db: Database) {
    private val root = File(context.filesDir,"plugins").apply { mkdirs() }
    fun install(input: java.io.InputStream): String {
        val staging=File(context.cacheDir,"plugin-staging-${System.nanoTime()}").apply { mkdirs() }
        var total=0L
        try {
            ZipInputStream(input).use { zip ->
                while(true) {
                    val e=zip.nextEntry ?: break
                    val out=File(staging,e.name).canonicalFile
                    require(out.path.startsWith(staging.canonicalPath+File.separator) || out==staging) { "Path ZIP tidak aman" }
                    if(e.isDirectory) out.mkdirs() else {
                        out.parentFile?.mkdirs()
                        out.outputStream().use { os -> val buf=ByteArray(8192); while(true) { val n=zip.read(buf); if(n<0) break; total+=n; require(total<=30L*1024*1024) { "Plugin melebihi 30 MB" }; os.write(buf,0,n) } }
                    }
                    zip.closeEntry()
                }
            }
            val mf=File(staging,"manifest.json"); require(mf.isFile) { "manifest.json tidak ditemukan" }
            val m=JSONObject(mf.readText()); val id=m.getString("id"); val name=m.getString("name"); val version=m.getString("version")
            require(id.matches(Regex("[a-z0-9_.-]{2,50}"))) { "ID plugin tidak valid" }
            require(m.optInt("api",1)<=1) { "Plugin membutuhkan API lebih baru" }
            require(File(staging,"web/index.html").isFile) { "web/index.html tidak ditemukan" }
            val dest=File(root,id); if(dest.exists()) dest.deleteRecursively()
            require(staging.renameTo(dest)) { "Gagal memasang plugin" }
            val cv=android.content.ContentValues().apply { put("id",id); put("name",name); put("version",version); put("enabled",1); put("installed_at",System.currentTimeMillis()) }
            db.writableDatabase.insertWithOnConflict("plugins",null,cv,android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
            return id
        } finally { staging.deleteRecursively() }
    }
    fun list(): org.json.JSONArray {
        val a=org.json.JSONArray(); db.readableDatabase.rawQuery("SELECT id,name,version,enabled FROM plugins ORDER BY name",null).use { c -> while(c.moveToNext()) a.put(JSONObject().put("id",c.getString(0)).put("name",c.getString(1)).put("version",c.getString(2)).put("enabled",c.getInt(3)==1).put("path",File(root,c.getString(0)).absolutePath+"/web/index.html")) }; return a
    }
    fun pluginFile(id:String): File? { if(!id.matches(Regex("[a-z0-9_.-]{2,50}"))) return null; val f=File(root,"$id/web/index.html"); return if(f.isFile) f else null }
}
