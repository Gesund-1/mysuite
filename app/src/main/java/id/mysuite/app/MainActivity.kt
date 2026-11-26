package id.mysuite.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.ValueCallback
import android.widget.Toast
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Button
import android.view.View
import android.view.Gravity
import android.graphics.Color
import android.graphics.Typeface

class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var externalBar: LinearLayout
    private lateinit var externalTitle: TextView
    private var pluginReturnUrl: String? = null
    private var viewingExternalPage = false
    private lateinit var db: Database
    private lateinit var pm: PluginManager
    private var expectedPluginId: String? = null
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private val audioPrefs by lazy { getSharedPreferences("mysuite_audio", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = Database(this)
        pm = PluginManager(this, db)
        web = WebView(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(11, 18, 34)) }
        externalBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12, 8, 12, 8)
            setBackgroundColor(Color.rgb(8, 24, 52))
            visibility = View.GONE
        }
        val backButton = Button(this).apply {
            text = "‹  Kembali ke Vault"
            isAllCaps = false
            setOnClickListener { returnToPlugin() }
        }
        externalTitle = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            setPadding(12, 0, 4, 0)
        }
        externalBar.addView(backButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        externalBar.addView(externalTitle, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(externalBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(web, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = true
        web.settings.allowContentAccess = true
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView?,
                callback: ValueCallback<Array<Uri>>?,
                params: WebChromeClient.FileChooserParams?
            ): Boolean {
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = callback
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "audio/*"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
                return try { startActivityForResult(intent, 42); true }
                catch (e: Exception) { fileChooserCallback = null; callback?.onReceiveValue(null); false }
            }
        }
        web.webViewClient = WebViewClient()
        web.addJavascriptInterface(Bridge(), "MySuite")
        showHome()
    }

    private fun showHome() {
        viewingExternalPage = false
        pluginReturnUrl = null
        externalBar.visibility = View.GONE
        web.settings.allowFileAccess = true
        web.settings.allowContentAccess = true
        web.addJavascriptInterface(Bridge(), "MySuite")
        web.loadUrl("file:///android_asset/www/index.html")
    }

    private fun returnToPlugin() {
        val target = pluginReturnUrl
        viewingExternalPage = false
        externalBar.visibility = View.GONE
        web.settings.allowFileAccess = true
        web.settings.allowContentAccess = true
        web.addJavascriptInterface(Bridge(), "MySuite")
        if (target != null) web.loadUrl(target) else showHome()
    }
    private fun choosePluginZip(expectedId: String? = null) {
        expectedPluginId = expectedId
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
        }
        startActivityForResult(intent, 41)
    }

    inner class Bridge {
        @JavascriptInterface fun plugins(): String = pm.list().toString()
        @JavascriptInterface fun importPlugin() { runOnUiThread { choosePluginZip() } }
        @JavascriptInterface fun pickAudioFolder() {
            runOnUiThread {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                }
                startActivityForResult(intent, 43)
            }
        }
        @JavascriptInterface fun audioLibrary(): String = readAudioLibrary().toString()
        @JavascriptInterface fun extractArticle(rawUrl: String): String {
            val uri = Uri.parse(rawUrl.trim())
            require(uri.scheme == "https" || uri.scheme == "http") { "URL artikel harus HTTP atau HTTPS" }
            val host = uri.host?.lowercase() ?: error("Host URL tidak valid")
            require(host != "localhost" && host != "127.0.0.1" && host != "0.0.0.0" &&
                !host.startsWith("10.") && !host.startsWith("192.168.") &&
                !host.startsWith("169.254.") && !Regex("^172\\.(1[6-9]|2[0-9]|3[0-1])\\.").containsMatchIn(host)) {
                "Alamat lokal tidak diizinkan"
            }
            val connection = URL(rawUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 10000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 MySuite/1.1")
            try {
                connection.connect()
                require(connection.responseCode in 200..299) { "Server artikel merespons ${connection.responseCode}" }
                val html = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    val out = StringBuilder(); val buffer = CharArray(8192); var total = 0
                    while (true) { val n = reader.read(buffer); if (n < 0) break; total += n; if (total > 1_000_000) break; out.append(buffer, 0, n) }
                    out.toString()
                }
                val title = Regex("(?is)<title[^>]*>(.*?)</title>").find(html)?.groupValues?.get(1)
                    ?.replace(Regex("<[^>]+>"), " ")?.replace(Regex("\\s+"), " ")?.trim().orEmpty().ifBlank { rawUrl }
                val cleaned = html.replace(Regex("(?is)<(script|style|nav|header|footer|aside)[^>]*>.*?</\\1>"), " ")
                    .replace(Regex("(?is)<br\\s*/?>|</p>|</div>|</h[1-6]>"), "\n")
                val text = Html.fromHtml(cleaned, Html.FROM_HTML_MODE_COMPACT).toString()
                    .replace(Regex("[\\t ]+"), " ").replace(Regex("\\n{3,}"), "\n\n").trim().take(50000)
                return JSONObject().put("title", title).put("content", text).toString()
            } finally { connection.disconnect() }
        }
        @JavascriptInterface fun updatePlugin(id: String) { runOnUiThread { choosePluginZip(id) } }
        @JavascriptInterface fun deletePlugin(id: String) {
            runOnUiThread {
                try { pm.uninstall(id); Toast.makeText(this@MainActivity, "Plugin dihapus", Toast.LENGTH_SHORT).show(); showHome() }
                catch (e: Exception) { Toast.makeText(this@MainActivity, "Gagal menghapus: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
        @JavascriptInterface fun openPlugin(id: String) {
            runOnUiThread {
                val file = pm.pluginFile(id)
                if (file == null) Toast.makeText(this@MainActivity, "Plugin tidak ditemukan", Toast.LENGTH_SHORT).show()
                else {
                    pluginReturnUrl = Uri.fromFile(file).toString()
                    viewingExternalPage = false
                    externalBar.visibility = View.GONE
                    web.settings.allowFileAccess = true
                    web.settings.allowContentAccess = true
                    web.addJavascriptInterface(Bridge(), "MySuite")
                    web.loadUrl(pluginReturnUrl!!)
                }
            }
        }
        @JavascriptInterface fun openBookmarkInApp(rawUrl: String, title: String) {
            runOnUiThread {
                val uri = Uri.parse(rawUrl.trim())
                if (uri.scheme != "https" && uri.scheme != "http") {
                    Toast.makeText(this@MainActivity, "URL harus HTTP atau HTTPS", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                if (pluginReturnUrl == null && web.url?.startsWith("file:") == true) pluginReturnUrl = web.url
                viewingExternalPage = true
                externalTitle.text = title.ifBlank { uri.host ?: "Website" }
                externalBar.visibility = View.VISIBLE
                // Halaman eksternal tidak diberi akses JavaScript bridge MySuite.
                web.removeJavascriptInterface("MySuite")
                web.settings.allowFileAccess = false
                web.settings.allowContentAccess = false
                web.loadUrl(uri.toString())
            }
        }
        @JavascriptInterface fun getData(plugin: String, key: String): String = db.get(plugin, key) ?: "null"
        @JavascriptInterface fun setData(plugin: String, key: String, value: String) { db.put(plugin, key, value) }
        @JavascriptInterface fun goHome() { runOnUiThread { showHome() } }
    }

    private fun readAudioLibrary(): JSONObject {
        val result = JSONObject()
        val playlists = JSONArray().put("all")
        val tracks = JSONArray()
        val raw = audioPrefs.getString("tree_uri", null)
        if (raw == null) return result.put("playlists", playlists).put("lagu", tracks)
        val root = DocumentFile.fromTreeUri(this, Uri.parse(raw))
            ?: return result.put("playlists", playlists).put("lagu", tracks)
        val children = root.listFiles().filter { it.isDirectory && !it.name.orEmpty().startsWith(".") && !it.name.equals("all", true) }
        children.mapNotNull { it.name }.sortedBy { it.lowercase() }.forEach { playlists.put(it) }
        fun walk(folder: DocumentFile, relative: String, playlist: String) {
            folder.listFiles().forEach { item ->
                val name = item.name ?: return@forEach
                if (name.startsWith(".")) return@forEach
                val path = if (relative.isEmpty()) name else "$relative/$name"
                if (item.isDirectory) walk(item, path, playlist)
                else if (name.substringAfterLast('.', "").lowercase() in setOf("mp3", "m4a", "ogg", "wav", "flac", "aac")) {
                    tracks.put(JSONObject().put("judul", name.substringBeforeLast('.', name)).put("file", path)
                        .put("url", item.uri.toString()).put("playlist", playlist))
                }
            }
        }
        root.listFiles().forEach { item ->
            val name = item.name ?: return@forEach
            if (item.isDirectory && !name.startsWith(".")) walk(item, name, name)
            else if (item.isFile && name.substringAfterLast('.', "").lowercase() == "mp3") {
                tracks.put(JSONObject().put("judul", name.substringBeforeLast('.', name)).put("file", name)
                    .put("url", item.uri.toString()).put("playlist", "all"))
            }
        }
        return result.put("playlists", playlists).put("lagu", tracks)
    }

    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 43) {
            if (resultCode == Activity.RESULT_OK && data?.data != null) {
                val uri = data.data!!
                val flags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                val readFlags = flags or Intent.FLAG_GRANT_READ_URI_PERMISSION
                try { contentResolver.takePersistableUriPermission(uri, readFlags) } catch (_: Exception) { }
                audioPrefs.edit().putString("tree_uri", uri.toString()).apply()
                val payload = JSONObject.quote(readAudioLibrary().toString())
                web.evaluateJavascript("window.MySuiteAudioLibraryLoaded && window.MySuiteAudioLibraryLoaded($payload)", null)
                Toast.makeText(this, "Folder audio berhasil dipilih", Toast.LENGTH_SHORT).show()
            }
            return
        }
        if (requestCode == 42) {
            val callback = fileChooserCallback
            fileChooserCallback = null
            if (callback != null) {
                val uris = mutableListOf<Uri>()
                if (resultCode == Activity.RESULT_OK && data != null) {
                    data.clipData?.let { clip -> for (i in 0 until clip.itemCount) uris.add(clip.getItemAt(i).uri) }
                    if (uris.isEmpty()) data.data?.let { uris.add(it) }
                }
                callback.onReceiveValue(if (uris.isEmpty()) null else uris.toTypedArray())
            }
            return
        }
        if (requestCode != 41) return
        val expected = expectedPluginId
        expectedPluginId = null
        if (resultCode != Activity.RESULT_OK || data?.data == null) return
        try {
            contentResolver.openInputStream(data.data!!)?.use { pm.install(it, expected) }
                ?: error("File ZIP tidak dapat dibaca")
            Toast.makeText(this, if (expected == null) "Plugin berhasil diimpor" else "Plugin berhasil diperbarui", Toast.LENGTH_LONG).show()
            showHome()
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_LONG).show()
            showHome()
        }
    }

    @Deprecated("Deprecated in Android API")
    override fun onBackPressed() {
        if (viewingExternalPage) returnToPlugin()
        else if (web.url?.contains("/plugins/") == true || pluginReturnUrl != null) showHome()
        else super.onBackPressed()
    }
}
