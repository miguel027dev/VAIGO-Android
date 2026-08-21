package online.vaigo.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.View
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom

class MainActivity : Activity() {

    companion object {
        private const val LOCATION_REQUEST = 4201
        private const val FILE_PICKER_REQUEST = 4202
        private const val PREFS = "vaigo_native"
        private const val PENDING_AUTH_STATE = "pending_auth_state"
        private const val PENDING_AUTH_VERIFIER = "pending_auth_verifier"
    }

    private lateinit var webView: WebView
    private lateinit var loadingOverlay: FrameLayout
    private var geoOrigin: String? = null
    private var geoCallback: GeolocationPermissions.Callback? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val baseUrl = BuildConfig.VAIGO_BASE_URL.trimEnd('/')

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        loadingOverlay = findViewById(R.id.loadingOverlay)

        configureWebView()

        if (!handleAuthDeepLink(intent)) {
            webView.loadUrl("$baseUrl/mobile/entry")
        }
    }

    @Suppress("SetJavaScriptEnabled")
    private fun configureWebView() {
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, false)
        }

        webView.setBackgroundColor(Color.rgb(247, 247, 251))
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            geolocationEnabled = true
            loadsImagesAutomatically = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = false
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(false)
            userAgentString = "$userAgentString VAIGO-Android/${BuildConfig.VERSION_NAME}"
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                safeBrowsingEnabled = true
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                return routeNavigation(uri)
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                val uri = url?.let(Uri::parse) ?: return false
                return routeNavigation(uri)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                loadingOverlay.visibility = View.GONE
                super.onPageFinished(view, url)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    loadingOverlay.visibility = View.GONE
                    showOfflinePage()
                }
                super.onReceivedError(view, request, error)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                if (origin == null || callback == null || !isTrustedOrigin(origin)) {
                    callback?.invoke(origin, false, false)
                    return
                }
                if (hasLocationPermission()) {
                    callback.invoke(origin, true, true)
                    return
                }
                geoOrigin = origin
                geoCallback = callback
                requestPermissions(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                    LOCATION_REQUEST
                )
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: WebChromeClient.FileChooserParams?
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = filePathCallback
                return try {
                    val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "*/*"
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }
                    startActivityForResult(intent, FILE_PICKER_REQUEST)
                    true
                } catch (_: ActivityNotFoundException) {
                    fileCallback = null
                    false
                }
            }

            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(message ?: "")
                    .setPositiveButton("OK") { _, _ -> result?.confirm() }
                    .setOnCancelListener { result?.cancel() }
                    .show()
                return true
            }
        }

        webView.setDownloadListener { url, _, _, _, _ -> openExternal(Uri.parse(url)) }
    }

    private fun routeNavigation(uri: Uri): Boolean {
        if (uri.scheme == "vaigo") {
            handleAuthUri(uri)
            return true
        }

        if (uri.scheme == "tel" || uri.scheme == "mailto" || uri.scheme == "sms") {
            openExternal(uri)
            return true
        }

        if (isGoogleLoginUrl(uri)) {
            showGoogleLoginDialog()
            return true
        }

        if (uri.scheme == "http" || uri.scheme == "https") {
            if (!isTrustedHost(uri)) {
                openExternal(uri)
                return true
            }
            return false
        }
        return false
    }

    private fun isGoogleLoginUrl(uri: Uri): Boolean {
        if (!isTrustedHost(uri)) return false
        val path = uri.path?.trimEnd('/') ?: return false
        return path == "/login/google" || path == "/auth/google"
    }

    private fun showGoogleLoginDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.google_dialog_title)
            .setMessage(R.string.google_dialog_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.google_dialog_continue) { _, _ -> startBrowserGoogleLogin() }
            .show()
    }

    private fun startBrowserGoogleLogin() {
        val state = randomState()
        val verifier = randomState()
        val challenge = pkceChallenge(verifier)
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString(PENDING_AUTH_STATE, state)
            .putString(PENDING_AUTH_VERIFIER, verifier)
            .apply()
        val url = Uri.parse("$baseUrl/mobile/auth/google/start").buildUpon()
            .appendQueryParameter("state", state)
            .appendQueryParameter("challenge", challenge)
            .appendQueryParameter("return_uri", BuildConfig.MOBILE_RETURN_URI)
            .build()
        openExternal(url)
    }

    private fun randomState(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun pkceChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun handleAuthDeepLink(intent: Intent?): Boolean {
        val uri = intent?.data ?: return false
        if (uri.scheme != "vaigo" || uri.host != "auth" || uri.path != "/callback") return false
        handleAuthUri(uri)
        return true
    }

    private fun handleAuthUri(uri: Uri) {
        val code = uri.getQueryParameter("code").orEmpty()
        val state = uri.getQueryParameter("state").orEmpty()
        val expected = getSharedPreferences(PREFS, MODE_PRIVATE).getString(PENDING_AUTH_STATE, "").orEmpty()

        if (code.isBlank() || state.isBlank() || expected.isBlank() || state != expected) {
            Toast.makeText(this, "Não foi possível validar o retorno do Google.", Toast.LENGTH_LONG).show()
            webView.loadUrl("$baseUrl/login")
            return
        }
        val verifier = getSharedPreferences(PREFS, MODE_PRIVATE).getString(PENDING_AUTH_VERIFIER, "").orEmpty()
        if (verifier.isBlank()) {
            Toast.makeText(this, "A tentativa de login expirou. Tente novamente.", Toast.LENGTH_LONG).show()
            webView.loadUrl("$baseUrl/login")
            return
        }
        exchangeMobileCode(code, state, verifier)
    }

    private fun exchangeMobileCode(code: String, state: String, verifier: String) {
        loadingOverlay.visibility = View.VISIBLE
        Thread {
            try {
                val endpoint = URL("$baseUrl/mobile/auth/exchange")
                val conn = (endpoint.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 12_000
                    readTimeout = 12_000
                    doOutput = true
                    useCaches = false
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "VAIGO-Android/${BuildConfig.VERSION_NAME}")
                }
                val body = JSONObject()
                    .put("code", code)
                    .put("state", state)
                    .put("verifier", verifier)
                    .toString()
                    .toByteArray(Charsets.UTF_8)
                conn.outputStream.use { it.write(body) }

                val status = conn.responseCode
                val stream = if (status in 200..299) conn.inputStream else conn.errorStream
                val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val response = JSONObject(responseText.ifBlank { "{}" })

                if (status !in 200..299 || !response.optBoolean("ok")) {
                    throw IllegalStateException(response.optString("error", "exchange_failed"))
                }

                val cookieName = response.getString("cookie_name")
                val rememberToken = response.getString("remember_token")
                val maxAge = response.optLong("max_age", 60L * 60L * 24L * 365L)
                val entry = response.optString("entry", "/mobile/entry")

                mainHandler.post {
                    val cookie = "$cookieName=$rememberToken; Path=/; Max-Age=$maxAge; Secure; HttpOnly; SameSite=Lax"
                    CookieManager.getInstance().setCookie(baseUrl, cookie) {
                        CookieManager.getInstance().flush()
                        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                            .remove(PENDING_AUTH_STATE)
                            .remove(PENDING_AUTH_VERIFIER)
                            .apply()
                        Toast.makeText(this, "Login concluído.", Toast.LENGTH_SHORT).show()
                        webView.loadUrl(if (entry.startsWith("/")) "$baseUrl$entry" else entry)
                    }
                }
            } catch (_: Exception) {
                mainHandler.post {
                    loadingOverlay.visibility = View.GONE
                    Toast.makeText(this, "O login expirou ou não pôde ser concluído. Tente novamente.", Toast.LENGTH_LONG).show()
                    webView.loadUrl("$baseUrl/login")
                }
            }
        }.start()
    }

    private fun hasLocationPermission(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun isTrustedHost(uri: Uri): Boolean {
        val trusted = Uri.parse(baseUrl)
        val candidateHost = uri.host?.lowercase() ?: return false
        val trustedHost = trusted.host?.lowercase() ?: return false
        val sameHost = candidateHost == trustedHost
        val sameWwwFamily = candidateHost.removePrefix("www.") == trustedHost.removePrefix("www.")
        return uri.scheme == trusted.scheme && (sameHost || sameWwwFamily)
    }

    private fun isTrustedOrigin(origin: String): Boolean = try {
        isTrustedHost(Uri.parse(origin))
    } catch (_: Exception) {
        false
    }

    private fun openExternal(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Nenhum aplicativo disponível para abrir esse link.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showOfflinePage() {
        val html = """
            <!doctype html><html><meta name=viewport content='width=device-width,initial-scale=1'>
            <body style='margin:0;min-height:100vh;display:grid;place-items:center;background:#f7f7fb;font-family:system-ui;color:#17171d'>
            <main style='padding:28px;text-align:center;max-width:360px'>
              <div style='font-size:38px'>↻</div><h2>Sem conexão</h2>
              <p style='color:#666673;line-height:1.5'>Verifique sua internet e tente novamente.</p>
              <button onclick='location.reload()' style='border:0;background:#5957e8;color:white;padding:13px 18px;border-radius:14px;font-weight:700'>Tentar novamente</button>
            </main></body></html>
        """.trimIndent()
        webView.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthDeepLink(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_REQUEST) {
            val granted = grantResults.any { it == PackageManager.PERMISSION_GRANTED }
            geoCallback?.invoke(geoOrigin, granted, granted)
            geoCallback = null
            geoOrigin = null
            if (!granted && !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                Toast.makeText(this, "Ative a localização nas configurações para usar navegação GPS.", Toast.LENGTH_LONG).show()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_PICKER_REQUEST) {
            val result = if (resultCode == RESULT_OK && data?.data != null) arrayOf(data.data!!) else null
            fileCallback?.onReceiveValue(result)
            fileCallback = null
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onPause() {
        CookieManager.getInstance().flush()
        webView.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onDestroy() {
        webView.stopLoading()
        webView.webChromeClient = null
        webView.webViewClient = WebViewClient()
        webView.destroy()
        super.onDestroy()
    }
}
