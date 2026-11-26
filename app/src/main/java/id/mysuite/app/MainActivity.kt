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

class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var db: Database
    private lateinit var pm: PluginManager
    private var expectedPluginId: String? = null
    private var moduleOpen: Boolean = false
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private val audioPrefs by lazy { getSharedPreferences("mysuite_audio", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = Database(this)
        pm = PluginManager(this, db)
        web = WebView(this)
        setContentView(web)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = true
        web.settings.allowContentAccess = true
        web.settings.mediaPlaybackRequiresUserGesture = false
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
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (moduleOpen && url != null && url.startsWith("file:")) injectModuleNavigation()
            }
        }
        web.addJavascriptInterface(Bridge(), "MySuite")
        showHome()
    }

    private fun showHome() {
        moduleOpen = false
        web.loadUrl("file:///android_asset/www/index.html")
    }

    private fun injectModuleNavigation() {
        val script = """
            (function() {
              if (document.getElementById('mysuite-module-nav')) return;
              const style = document.createElement('style');
              style.id = 'mysuite-module-nav-style';
              style.textContent = `
                #mysuite-module-nav{position:fixed;top:0;left:0;right:0;z-index:2147483000;display:flex;align-items:center;gap:10px;
                  min-height:52px;padding:8px 14px;background:#0a1830;color:#f3f7ff;border-bottom:1px solid #29415f;
                  font:600 14px system-ui,-apple-system,sans-serif;box-shadow:0 3px 14px rgba(0,0,0,.22)}
                #mysuite-module-nav button{border:0;border-radius:9px;padding:9px 13px;background:#19b9c5;color:#061923;
                  font-weight:750;font-size:13px}
                #mysuite-module-nav .label{opacity:.9;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
                body{padding-top:58px!important;scroll-padding-top:58px}
              `;
              document.head.appendChild(style);
              const bar=document.createElement('div');
              bar.id='mysuite-module-nav';
              bar.innerHTML='<button type="button" id="mysuite-home-btn">⌂ Home</button><span class="label">MySuite</span>';
              document.body.insertBefore(bar,document.body.firstChild);
              document.getElementById('mysuite-home-btn').addEventListener('click',function(){
                if(window.MySuite && MySuite.goHome) MySuite.goHome();
              });
            })();
        """.trimIndent()
        web.evaluateJavascript(script, null)
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
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
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
                else { moduleOpen = true; web.loadUrl(Uri.fromFile(file).toString()) }
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
            else if (item.isFile && name.substringAfterLast('.', "").lowercase() in setOf("mp3", "m4a", "ogg", "wav", "flac", "aac")) {
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
                try { contentResolver.takePersistableUriPermission(uri, flags) } catch (_: Exception) { }
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
        if (moduleOpen) {
            showHome()
        } else {
            super.onBackPressed()
        }
    }
}
