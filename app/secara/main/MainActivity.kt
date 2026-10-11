package id.kynime.app

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * KYNIME - pembungkus WebView + login Google lewat Chrome Custom Tab.
 * Google melarang login di dalam WebView, jadi situs memanggil window.KynimeNative.openAuth(url) dan aplikasi membuka
 * halaman "Pilih akun" Google (sama persis dengan di browser) di Custom Tab. Setelah berhasil, halaman /gcb.html menitipkan
 * hasilnya ke server; situs (di WebView) mengambilnya lewat polling, lalu aplikasi dibawa ke depan lagi (focusApp).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var root: FrameLayout
    private lateinit var web: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private var authPending = false

    private val siteUri: Uri by lazy { Uri.parse(BuildConfig.SITE_URL) }
    private val siteHost: String by lazy { siteUri.host ?: "" }

    private val filePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(r.resultCode, r.data))
        fileCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = FrameLayout(this)
        web = WebView(this)
        root.addView(web, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        setContentView(root)

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            allowFileAccess = false
            allowContentAccess = true
            javaScriptCanOpenWindowsAutomatically = false
            userAgentString = userAgentString + " KynimeApp/1"
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(web, true)
        }
        web.addJavascriptInterface(Bridge(), "KynimeNative")

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                if (!req.isForMainFrame) return false
                val u = req.url
                val host = u.host ?: ""
                if ((u.scheme == "http" || u.scheme == "https") &&
                    (host == siteHost || host.endsWith(".$siteHost"))) return false
                openExternal(u)
                return true
            }

            override fun onReceivedError(view: WebView, req: WebResourceRequest, err: WebResourceError) {
                if (req.isForMainFrame) showOffline()
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                // sinyal ke situs bahwa bridge login native tersedia
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView, cb: ValueCallback<Array<Uri>>, params: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = cb
                return try {
                    filePicker.launch(params.createIntent()); true
                } catch (e: Exception) {
                    fileCallback = null; false
                }
            }

            override fun onShowCustomView(view: View, cb: CustomViewCallback) {
                if (customView != null) { cb.onCustomViewHidden(); return }
                customView = view; customCallback = cb
                web.visibility = View.GONE
                root.addView(view, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                immersive(true)
            }

            override fun onHideCustomView() { hideCustomView() }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    customView != null -> hideCustomView()
                    web.canGoBack() -> web.goBack()
                    else -> finish()
                }
            }
        })

        if (savedInstanceState == null) web.loadUrl(BuildConfig.SITE_URL) else web.restoreState(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    // saat Custom Tab terbuka, WebView tetap berjalan agar polling hasil login tidak berhenti
    override fun onPause() { super.onPause(); if (!authPending) web.onPause(); CookieManager.getInstance().flush() }
    override fun onResume() {
        super.onResume(); web.onResume()
        if (authPending) { authPending = false; poke() }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); poke() }
    private fun poke() { web.post { web.evaluateJavascript("window.__kynimeResume && window.__kynimeResume()", null) } }
    override fun onDestroy() { web.destroy(); super.onDestroy() }

    private fun hideCustomView() {
        val v = customView ?: return
        root.removeView(v)
        customView = null
        customCallback?.onCustomViewHidden(); customCallback = null
        web.visibility = View.VISIBLE
        immersive(false)
    }

    private fun immersive(on: Boolean) {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        if (on) {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            c.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun openExternal(u: Uri) {
        try { startActivity(Intent(Intent.ACTION_VIEW, u)) } catch (_: Exception) { }
    }

    private fun showOffline() {
        val html = """
            <html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head>
            <body style="margin:0;background:#09090b;color:#fff;font-family:sans-serif;display:grid;place-items:center;height:100vh;text-align:center">
            <div><h2>Tidak ada koneksi</h2><p style="opacity:.7">Periksa internet lalu coba lagi.</p>
            <button onclick="KynimeNative.reload()" style="padding:12px 28px;border:0;border-radius:12px;background:#e50914;color:#fff;font-size:16px">Coba lagi</button></div>
            </body></html>
        """.trimIndent()
        web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
    }

    /* ================= Login Google (Chrome Custom Tab) ================= */

    private fun openAuth(url: String) {
        val u = Uri.parse(url)
        // hanya halaman login Google yang boleh dibuka lewat jembatan ini
        if (u.scheme != "https" || u.host != "accounts.google.com") return
        authPending = true
        try {
            CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(this, u)
        } catch (e: Exception) {
            openExternal(u) // tidak ada Custom Tabs: buka browser biasa
        }
    }

    private fun bringToFront() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }

    inner class Bridge {
        @JavascriptInterface fun openAuth(url: String) { runOnUiThread { this@MainActivity.openAuth(url) } }
        @JavascriptInterface fun focusApp() { runOnUiThread { bringToFront() } }
        @JavascriptInterface fun reload() { runOnUiThread { web.loadUrl(BuildConfig.SITE_URL) } }
        @JavascriptInterface fun version(): String = "2"
    }
}
