package ir.shipaa.mobile

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

/**
 * Shipaa 0.3.1: Mobile-first secure website shell.
 * Course authorization remains entirely server-side on shipaa.ir.
 * This app never fabricates entitlements, lesson URLs or progress sync.
 *
 * Changes over 0.3.0:
 *  - Edge-to-edge insets (Android 15 / targetSdk 35) so header and bottom bar are not hidden.
 *  - File chooser support (receipt upload, avatar, attachments).
 *  - Safer fullscreen video exit + auto landscape while in fullscreen.
 *  - Direct video downloads are blocked; other downloads are handed to the system.
 *  - "ShipaaApp" user-agent suffix so the site/plugin can detect the app.
 *  - Distinct account tab, app icon, offline screen shown also on HTTP errors of the main page.
 */
class MainActivity : Activity() {
    private val origin = "https://shipaa.ir"
    private val home = "$origin/my-account/"
    private lateinit var root: LinearLayout
    private lateinit var browser: WebView
    private lateinit var browserFrame: FrameLayout
    private lateinit var header: LinearLayout
    private lateinit var bottomBar: LinearLayout
    private lateinit var progress: ProgressBar
    private lateinit var errorOverlay: LinearLayout
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val tabs = listOf(
        Triple("⌂", "خانه من", home),
        Triple("▦", "دوره‌ها", "$origin/shop/"),
        Triple("◉", "سایت", "$origin/"),
        Triple("♙", "حساب", "$origin/my-account/edit-account/")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(Color.WHITE)
        }
        // Android 15 enforces edge-to-edge for targetSdk 35: pad the root by system bars + keyboard.
        root.setOnApplyWindowInsetsListener { v, insets ->
            if (fullscreenView != null) {
                v.setPadding(0, 0, 0, 0)
            } else if (Build.VERSION.SDK_INT >= 30) {
                val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                v.setPadding(i.left, i.top, i.right, i.bottom)
            } else {
                @Suppress("DEPRECATION")
                v.setPadding(
                    insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom
                )
            }
            insets
        }

        header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(18), 0, dp(18), 0)
            setBackgroundColor(Color.WHITE)
        }
        val brand = TextView(this).apply {
            text = "شیپا  •  خانه یادگیری"
            textSize = 17f
            setTextColor(Color.rgb(55, 145, 66))
            gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT
        }
        header.addView(brand, LinearLayout.LayoutParams(0, -1, 1f))
        val reload = TextView(this).apply {
            text = "↻"
            textSize = 25f
            setTextColor(Color.rgb(69, 120, 90))
            gravity = Gravity.CENTER
            contentDescription = "بازنشانی صفحه"
            setOnClickListener { browser.reload() }
        }
        header.addView(reload, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(header, LinearLayout.LayoutParams(-1, dp(54)))

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
            progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(108, 192, 78))
        }
        root.addView(progress, LinearLayout.LayoutParams(-1, dp(3)))

        browserFrame = FrameLayout(this)
        browser = WebView(this).apply {
            settings.javaScriptEnabled = true // Required by the existing WordPress dashboard.
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.mediaPlaybackRequiresUserGesture = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.userAgentString = settings.userAgentString + " ShipaaApp/0.3.1"
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)

            setDownloadListener { url, _, _, mimeType, _ ->
                val uri = Uri.parse(url)
                val isVideo = (mimeType ?: "").startsWith("video/") || url.lowercase().substringBefore('?').endsWith(".mp4")
                if (isVideo) {
                    Toast.makeText(this@MainActivity, "دانلود مستقیم ویدیو پشتیبانی نمی‌شود", Toast.LENGTH_SHORT).show()
                } else if (uri.scheme == "https") {
                    openExternal(uri)
                }
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (!request.isForMainFrame) return false
                    val u = request.url
                    if (u.scheme == "https" && (u.host == "shipaa.ir" || u.host == "www.shipaa.ir")) return false
                    if (u.scheme == "http" || u.scheme == "https" || u.scheme == "mailto" || u.scheme == "tel" || u.scheme == "eitaa") return openExternal(u)
                    return true
                }
                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    errorOverlay.visibility = View.GONE
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) errorOverlay.visibility = View.VISIBLE
                }
                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: android.webkit.WebResourceResponse) {
                    if (request.isForMainFrame && errorResponse.statusCode >= 500) errorOverlay.visibility = View.VISIBLE
                }
                override fun onPageFinished(view: WebView, url: String?) {
                    super.onPageFinished(view, url)
                    CookieManager.getInstance().flush()
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    progress.progress = newProgress
                    progress.visibility = if (newProgress >= 100 || fullscreenView != null) View.GONE else View.VISIBLE
                }
                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    if (fullscreenView != null) { callback.onCustomViewHidden(); return }
                    fullscreenView = view
                    fullscreenCallback = callback
                    browserFrame.addView(view, FrameLayout.LayoutParams(-1, -1))
                    browser.visibility = View.GONE
                    header.visibility = View.GONE
                    bottomBar.visibility = View.GONE
                    progress.visibility = View.GONE
                    errorOverlay.visibility = View.GONE
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
                    @Suppress("DEPRECATION")
                    window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    root.setPadding(0, 0, 0, 0)
                }
                override fun onHideCustomView() { exitFullscreen() }

                override fun onShowFileChooser(
                    webView: WebView,
                    callback: ValueCallback<Array<Uri>>,
                    params: FileChooserParams
                ): Boolean {
                    filePathCallback?.onReceiveValue(null)
                    filePathCallback = callback
                    return try {
                        startActivityForResult(params.createIntent(), REQ_FILE)
                        true
                    } catch (_: ActivityNotFoundException) {
                        filePathCallback = null
                        Toast.makeText(this@MainActivity, "برنامه‌ای برای انتخاب فایل پیدا نشد", Toast.LENGTH_SHORT).show()
                        false
                    }
                }
            }
        }
        browserFrame.addView(browser, FrameLayout.LayoutParams(-1, -1))
        errorOverlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(30), dp(30), dp(30), dp(30))
            setBackgroundColor(Color.rgb(248, 251, 249))
            visibility = View.GONE
        }
        val message = TextView(this).apply {
            text = "اتصال برقرار نیست\nاینترنت را بررسی کن و دوباره تلاش کن."
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(61, 80, 67))
        }
        errorOverlay.addView(message)
        val retry = TextView(this).apply {
            text = "تلاش دوباره"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(74, 161, 72))
            setPadding(dp(26), dp(12), dp(26), dp(12))
            setOnClickListener { errorOverlay.visibility = View.GONE; browser.reload() }
        }
        val retryLayout = LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(20) }
        errorOverlay.addView(retry, retryLayout)
        browserFrame.addView(errorOverlay, FrameLayout.LayoutParams(-1, -1))
        root.addView(browserFrame, LinearLayout.LayoutParams(-1, 0, 1f))

        bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.WHITE)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            elevation = dp(6).toFloat()
        }
        tabs.forEach { (icon, title, link) ->
            val tab = TextView(this).apply {
                text = "$icon\n$title"
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(63, 119, 75))
                setOnClickListener { browser.loadUrl(link) }
            }
            bottomBar.addView(tab, LinearLayout.LayoutParams(0, dp(62), 1f))
        }
        root.addView(bottomBar)
        setContentView(root)
        root.requestApplyInsets()
        if (savedInstanceState != null) browser.restoreState(savedInstanceState) else browser.loadUrl(home)
    }

    private fun exitFullscreen() {
        val cb = fullscreenCallback
        val v = fullscreenView
        fullscreenCallback = null
        fullscreenView = null
        v?.let { browserFrame.removeView(it) }
        cb?.onCustomViewHidden()
        browser.visibility = View.VISIBLE
        header.visibility = View.VISIBLE
        bottomBar.visibility = View.VISIBLE
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        root.requestApplyInsets()
    }

    private fun openExternal(uri: Uri): Boolean {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: Exception) {
            Toast.makeText(this, "امکان باز کردن پیوند وجود ندارد", Toast.LENGTH_SHORT).show()
        }
        return true
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQ_FILE) {
            val result = if (resultCode == RESULT_OK) WebChromeClient.FileChooserParams.parseResult(resultCode, data) else null
            filePathCallback?.onReceiveValue(result)
            filePathCallback = null
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        browser.saveState(outState)
        super.onSaveInstanceState(outState)
    }
    override fun onPause() {
        browser.onPause()
        CookieManager.getInstance().flush()
        super.onPause()
    }
    override fun onResume() {
        super.onResume()
        browser.onResume()
    }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (fullscreenView != null) exitFullscreen()
        else if (browser.canGoBack()) browser.goBack()
        else super.onBackPressed()
    }
    override fun onDestroy() {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
        browser.destroy()
        super.onDestroy()
    }

    companion object { private const val REQ_FILE = 1001 }
}
