package com.rydius.mobile

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.net.URI

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var swipeRefresh: SwipeRefreshLayout

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var geolocationOrigin: String? = null
    private var geolocationCallback: GeolocationPermissions.Callback? = null

    private val trustedHost: String by lazy {
        runCatching { URI(BuildConfig.BASE_URL).host.orEmpty() }.getOrDefault("")
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        filePathCallback?.onReceiveValue(uris)
        filePathCallback = null
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        geolocationCallback?.invoke(geolocationOrigin, granted, false)
        geolocationOrigin = null
        geolocationCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        swipeRefresh = findViewById(R.id.swipeRefresh)

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowContentAccess = true
        settings.allowFileAccess = false
        settings.databaseEnabled = true
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.loadsImagesAutomatically = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.javaScriptCanOpenWindowsAutomatically = true

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val targetUri = request?.url ?: return false
                return handleExternalSchemes(targetUri) || handleExternalDomains(targetUri)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
                swipeRefresh.isRefreshing = false
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    view?.loadData(
                        "<html><body style='font-family:sans-serif;padding:24px;'>" +
                            "<h3>Connection issue</h3>" +
                            "<p>RideMate couldn't load. Check internet or backend URL.</p>" +
                            "</body></html>",
                        "text/html",
                        "UTF-8"
                    )
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                runOnUiThread {
                    val originHost = runCatching { URI(request?.origin.toString()).host }.getOrNull()
                    if (originHost != null && originHost == trustedHost) {
                        request?.grant(request.resources)
                    } else {
                        request?.deny()
                    }
                }
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback

                val intent = fileChooserParams?.createIntent()
                if (intent != null) {
                    filePickerLauncher.launch(intent)
                    return true
                }

                this@MainActivity.filePathCallback = null
                return false
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                if (hasLocationPermission()) {
                    callback?.invoke(origin, true, false)
                    return
                }

                geolocationOrigin = origin
                geolocationCallback = callback
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }

        swipeRefresh.setOnRefreshListener {
            webView.reload()
        }

        onBackPressedDispatcher.addCallback(this) {
            if (webView.canGoBack()) {
                webView.goBack()
            } else {
                finish()
            }
        }

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl(BuildConfig.BASE_URL)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
        super.onDestroy()
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarse = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fine || coarse
    }

    private fun handleExternalSchemes(uri: Uri): Boolean {
        val scheme = uri.scheme ?: return false
        if (scheme == "http" || scheme == "https") {
            return false
        }

        return runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
            true
        }.getOrDefault(false)
    }

    private fun handleExternalDomains(uri: Uri): Boolean {
        val host = uri.host ?: return false
        if (host == trustedHost) {
            return false
        }

        val isMapsLink = host.contains("maps", ignoreCase = true)
        val isCommonExternal = host.contains("wa.me", ignoreCase = true) ||
            host.contains("whatsapp", ignoreCase = true) ||
            host.contains("youtube", ignoreCase = true)

        if (isMapsLink || isCommonExternal) {
            return runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
                true
            }.getOrDefault(false)
        }

        return false
    }
}
