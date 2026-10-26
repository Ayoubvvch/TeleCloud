package com.acmods.telecloud

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.*
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import java.net.URLDecoder

class MainActivity : AppCompatActivity() {

    private lateinit var rootContainer: FrameLayout
    private lateinit var webView: WebView
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var lastBackPressTime = 0L

    private var pendingDownloadUrl: String? = null
    private var pendingDownloadFilename: String? = null

    private val permissionLauncher: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (allGranted) {
                val url = pendingDownloadUrl
                val name = pendingDownloadFilename
                if (url != null && name != null) {
                    enqueueDownload(url, name)
                }
            } else {
                Toast.makeText(this, "Permission is required to proceed with downloads", Toast.LENGTH_SHORT).show()
            }
            pendingDownloadUrl = null
            pendingDownloadFilename = null
        }

    private val filePickerLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (fileChooserCallback == null) return@registerForActivityResult
            val uris = if (result.resultCode == RESULT_OK && result.data != null) {
                val data = result.data
                when {
                    data?.clipData != null -> {
                        val count = data.clipData!!.itemCount
                        Array(count) { i -> data.clipData!!.getItemAt(i).uri }
                    }
                    data?.data != null -> arrayOf(data.data!!)
                    else -> null
                }
            } else null
            fileChooserCallback?.onReceiveValue(uris)
            fileChooserCallback = null
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // AMOLED / Dark Space System Bars (#090D16)
        window.statusBarColor = Color.parseColor("#090D16")
        window.navigationBarColor = Color.parseColor("#090D16")
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        rootContainer = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#090D16"))
        }

        webView = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#090D16"))
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            overScrollMode = View.OVER_SCROLL_NEVER

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
                mediaPlaybackRequiresUserGesture = false
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(false)
                displayZoomControls = false
            }
        }

        // Enable Cookies & Third-Party Cookies for authenticated streaming
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        rootContainer.addView(webView)
        setContentView(rootContainer)

        // Javascript Bridge for Native Video & Storage
        webView.addJavascriptInterface(AndroidBridge(this), "AndroidBridge")

        // Intercept standard downloads if triggered directly
        webView.setDownloadListener { url, _, contentDisposition, mimetype, _ ->
            val filename = URLUtil.guessFileName(url, contentDisposition, mimetype)
            startDownload(url, filename)
        }

        // Fast Local Origin Asset Loader
        val assetLoader = WebViewAssetLoader.Builder()
            .setDomain("appassets.androidplatform.net")
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView.webViewClient = object : WebViewClientCompat() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()
                return if (url.startsWith("https://appassets.androidplatform.net")) {
                    false
                } else if (url.contains("/api/tg-cloud/stream") && (url.contains("download=1") || url.contains(".mp4"))) {
                    val filename = URLUtil.guessFileName(url, null, "video/mp4")
                    startDownload(url, filename)
                    true
                } else {
                    try {
                        val uri = Uri.parse(url)
                        val scheme = uri.scheme?.lowercase()
                        if (scheme == "http" || scheme == "https" || scheme == "tg") {
                            startActivity(Intent(Intent.ACTION_VIEW, uri))
                            true
                        } else {
                            false
                        }
                    } catch (e: Exception) {
                        false
                    }
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                webView.visibility = View.GONE
                rootContainer.addView(
                    customView,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )

                // Fullscreen immersive mode
                val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
                windowInsetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }

            override fun onHideCustomView() {
                if (customView == null) return
                rootContainer.removeView(customView)
                customView = null
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
                webView.visibility = View.VISIBLE

                val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
                windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }

            override fun onShowFileChooser(
                mWebView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = filePathCallback

                val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                }
                return try {
                    filePickerLauncher.launch(intent)
                    true
                } catch (e: Exception) {
                    fileChooserCallback?.onReceiveValue(null)
                    fileChooserCallback = null
                    false
                }
            }

            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("TeleCloud")
                    .setMessage(message ?: "")
                    .setPositiveButton("OK") { _, _ -> result?.confirm() }
                    .setOnCancelListener { result?.cancel() }
                    .show()
                return true
            }

            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("TeleCloud")
                    .setMessage(message ?: "")
                    .setPositiveButton("Confirm") { _, _ -> result?.confirm() }
                    .setNegativeButton("Cancel") { _, _ -> result?.cancel() }
                    .setOnCancelListener { result?.cancel() }
                    .show()
                return true
            }
        }

        // Back Navigation Handler
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (customView != null) {
                    webView.webChromeClient?.onHideCustomView()
                } else if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    val now = System.currentTimeMillis()
                    if (now - lastBackPressTime < 2000) {
                        finish()
                    } else {
                        lastBackPressTime = now
                        Toast.makeText(this@MainActivity, "Press back again to exit TeleCloud", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })

        // Initial permissions check (only if strictly required)
        checkAndRequestPermissions()

        // Launch TeleCloud UI
        webView.loadUrl("https://appassets.androidplatform.net/assets/web/index.html")
    }

    private fun getRequiredPermissions(): Array<String> {
        val list = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            list.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        return list.toTypedArray()
    }

    fun hasStoragePermissions(): Boolean {
        for (perm in getRequiredPermissions()) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    fun checkAndRequestPermissions() {
        val missing = getRequiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    fun startDownload(url: String, filename: String) {
        if (!hasStoragePermissions()) {
            pendingDownloadUrl = url
            pendingDownloadFilename = filename
            checkAndRequestPermissions()
            return
        }
        enqueueDownload(url, filename)
    }

    private fun enqueueDownload(url: String, filename: String) {
        try {
            var cleanName = filename.trim().ifEmpty { "telecloud_video_" + System.currentTimeMillis() + ".mp4" }
            try {
                cleanName = URLDecoder.decode(cleanName, "UTF-8")
            } catch (_: Exception) {}

            cleanName = cleanName.replace(Regex("[/\\\\?%*:|\"<>]"), "_")
            val isApk = cleanName.endsWith(".apk", ignoreCase = true)
            if (!isApk && !cleanName.endsWith(".mp4", ignoreCase = true) && !cleanName.contains(".")) {
                cleanName += ".mp4"
            }

            val mimeType = if (isApk) "application/vnd.android.package-archive" else "video/mp4"
            val desc = if (isApk) "TeleCloud - App Update" else "TeleCloud - Downloading media"

            val uri = Uri.parse(url)
            val cookie = try { CookieManager.getInstance().getCookie(url) } catch (_: Exception) { null }
            val request = DownloadManager.Request(uri).apply {
                setTitle(cleanName)
                setDescription(desc)
                if (!cookie.isNullOrEmpty()) {
                    addRequestHeader("Cookie", cookie)
                }
                addRequestHeader("User-Agent", "TeleCloudApp/2.2.0")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, cleanName)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                setMimeType(mimeType)
            }
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            Toast.makeText(this, "Starting download: $cleanName in Downloads folder", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to start download: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun openExternalPlayer(url: String, title: String) {
        try {
            val cookie = try { CookieManager.getInstance().getCookie(url) } catch (_: Exception) { null }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(url), "video/*")
                putExtra(Intent.EXTRA_TITLE, title)
                if (!cookie.isNullOrEmpty()) {
                    putExtra("headers", arrayOf("Cookie", cookie))
                    val headersBundle = Bundle().apply {
                        putString("Cookie", cookie)
                        putString("User-Agent", "TeleCloudApp/2.2.0")
                    }
                    putExtra("android.media.intent.extra.HTTP_HEADERS", headersBundle)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(Intent.createChooser(intent, "Play with..."))
        } catch (e: Exception) {
            Toast.makeText(this, "No compatible video player found on device", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    override fun onDestroy() {
        customView?.let { webView.webChromeClient?.onHideCustomView() }
        webView.destroy()
        super.onDestroy()
    }

    class AndroidBridge(private val activity: MainActivity) {

        @JavascriptInterface
        fun downloadFile(url: String, filename: String) {
            activity.runOnUiThread {
                activity.startDownload(url, filename)
            }
        }

        @JavascriptInterface
        fun openExternalPlayer(url: String, title: String) {
            activity.runOnUiThread {
                activity.openExternalPlayer(url, title)
            }
        }

        @JavascriptInterface
        fun setKeepScreenOn(keepOn: Boolean) {
            activity.runOnUiThread {
                if (keepOn) {
                    activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }

        @JavascriptInterface
        fun showToast(message: String) {
            activity.runOnUiThread {
                Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun requestStoragePermission() {
            activity.runOnUiThread {
                activity.checkAndRequestPermissions()
            }
        }

        @JavascriptInterface
        fun isStoragePermissionGranted(): Boolean {
            return activity.hasStoragePermissions()
        }

        @JavascriptInterface
        fun getAppVersion(): String {
            return BuildConfig.VERSION_NAME
        }

        @JavascriptInterface
        fun openBrowser(url: String) {
            activity.runOnUiThread {
                try {
                    val uri = Uri.parse(url)
                    val scheme = uri.scheme?.lowercase()
                    if (scheme == "http" || scheme == "https") {
                        activity.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    }
                } catch (_: Exception) {
                    Toast.makeText(activity, "Unable to open link", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun setCookie(url: String, cookieString: String) {
            activity.runOnUiThread {
                try {
                    CookieManager.getInstance().setCookie(url, cookieString)
                    CookieManager.getInstance().flush()
                } catch (_: Exception) {}
            }
        }

        @JavascriptInterface
        fun getCookie(url: String): String {
            return try {
                CookieManager.getInstance().getCookie(url) ?: ""
            } catch (_: Exception) {
                ""
            }
        }

        @JavascriptInterface
        fun clearCookies() {
            activity.runOnUiThread {
                try {
                    CookieManager.getInstance().removeAllCookies(null)
                    CookieManager.getInstance().flush()
                } catch (_: Exception) {}
            }
        }
    }
}
