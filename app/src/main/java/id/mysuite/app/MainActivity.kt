package id.mysuite.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import java.io.File

class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var db: Database
    private lateinit var pm: PluginManager
    private var pendingPlugin=false
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); db=Database(this); pm=PluginManager(this,db); web=WebView(this); setContentView(web)
        web.settings.javaScriptEnabled=true; web.settings.domStorageEnabled=true; web.settings.allowFileAccess=true; web.settings.allowContentAccess=true
        web.webChromeClient=WebChromeClient(); web.webViewClient=WebViewClient(); web.addJavascriptInterface(Bridge(),"MySuite")
        web.loadUrl("file:///android_asset/www/index.html")
    }
    inner class Bridge {
        @JavascriptInterface fun plugins():String=pm.list().toString()
        @JavascriptInterface fun importPlugin(){ runOnUiThread { pendingPlugin=true; val i=Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type="application/zip" }; startActivityForResult(i,41) } }
        @JavascriptInterface fun openPlugin(id:String){ runOnUiThread { val f=pm.pluginFile(id); if(f==null) Toast.makeText(this@MainActivity,"Plugin tidak ditemukan",Toast.LENGTH_SHORT).show() else web.loadUrl(Uri.fromFile(f).toString()) } }
        @JavascriptInterface fun getData(plugin:String,key:String):String=db.get(plugin,key) ?: "null"
        @JavascriptInterface fun setData(plugin:String,key:String,value:String){ db.put(plugin,key,value) }
        @JavascriptInterface fun goHome(){ runOnUiThread { web.loadUrl("file:///android_asset/www/index.html") } }
    }
    @Deprecated("Deprecated in Android API") override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){ super.onActivityResult(requestCode,resultCode,data); if(requestCode==41 && resultCode==Activity.RESULT_OK && data?.data!=null){ try { contentResolver.openInputStream(data.data!!)?.use { pm.install(it) }; Toast.makeText(this,"Plugin berhasil dipasang",Toast.LENGTH_LONG).show(); web.loadUrl("file:///android_asset/www/index.html") } catch(e:Exception){ Toast.makeText(this,"Gagal memasang: ${e.message}",Toast.LENGTH_LONG).show() } } }
    override fun onBackPressed(){ if(web.url?.contains("/plugins/")==true) web.loadUrl("file:///android_asset/www/index.html") else super.onBackPressed() }
}
