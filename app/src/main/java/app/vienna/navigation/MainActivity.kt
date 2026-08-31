package app.vienna.navigation

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.nativead.AdChoicesView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import kotlin.math.roundToInt
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.View
import android.view.WindowInsets
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.JsResult
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
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
        private const val PREFS = "vienna_native"
        private const val PENDING_AUTH_STATE = "pending_auth_state"
        private const val PENDING_AUTH_VERIFIER = "pending_auth_verifier"
        private const val THEME_MODE = "theme_mode"
        private const val THEME_BLACK = "black"
        private const val THEME_LIGHT = "light"
        private const val APP_SCHEME = "vienna"
        private const val START_URL_PATH = "/mobile/entry"
        private const val LOADER_FADE_MS = 180L
        private const val ADMOB_NATIVE_UNIT_ID = "ca-app-pub-8278559850014123/5575870629"
        private const val ADMOB_TEST_NATIVE_UNIT_ID = "ca-app-pub-3940256099942544/2247696110"
        private const val NATIVE_AD_MAX_AGE_MS = 55L * 60L * 1000L
    }

    private lateinit var root: FrameLayout
    private lateinit var webView: WebView
    private lateinit var loadingOverlay: FrameLayout
    private lateinit var loadingProgress: ProgressBar
    private lateinit var loadingLabel: TextView
    private lateinit var brandBanner: ImageView
    private lateinit var nativeAdHost: FrameLayout

    @Volatile private var adsInitialized = false
    private var nativeAdLoading = false
    private var currentNativeAd: NativeAd? = null
    private var currentNativeAdLoadedAt = 0L
    private var pendingNativeAdBounds: String? = null

    private var geoOrigin: String? = null
    private var geoCallback: GeolocationPermissions.Callback? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var firstContentShown = false
    private var lastMainFrameUrl: String? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val baseUrl = BuildConfig.VIENNA_BASE_URL.trimEnd('/')

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        root = findViewById(R.id.root)
        webView = findViewById(R.id.webView)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        loadingProgress = findViewById(R.id.loadingProgress)
        loadingLabel = findViewById(R.id.loadingLabel)
        brandBanner = findViewById(R.id.brandBanner)
        nativeAdHost = findViewById(R.id.nativeAdHost)

        configureSystemUi()
        configureAds()
        configureWebView()

        if (!handleAuthDeepLink(intent)) {
            loadStartPage()
        }
    }

    private fun nativeTheme(): String = getSharedPreferences(PREFS, MODE_PRIVATE)
        .getString(THEME_MODE, THEME_LIGHT)
        .let { if (it == THEME_BLACK) THEME_BLACK else THEME_LIGHT }

    private fun configureSystemUi() {
        applyNativeTheme(nativeTheme())

        if (Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
        }

        // Android 15 força edge-to-edge para apps target 35. Mantém os controles do site
        // fora da barra de status/navegação sem adicionar dependência AndroidX.
        if (Build.VERSION.SDK_INT >= 35) {
            root.setOnApplyWindowInsetsListener { view, insets ->
                val safe = insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                )
                view.setPadding(0, safe.top, 0, safe.bottom)
                insets
            }
        }
    }

    private fun applyNativeTheme(mode: String) {
        val black = mode == THEME_BLACK
        val background = Color.parseColor(if (black) "#171512" else "#FFFBF2")
        val muted = Color.parseColor(if (black) "#C7BBB3" else "#706861")
        val track = Color.parseColor(if (black) "#3A332E" else "#F1DED1")
        val primary = Color.parseColor("#F59A62")

        window.statusBarColor = background
        window.navigationBarColor = background
        window.decorView.systemUiVisibility = if (black) {
            0
        } else {
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }

        root.setBackgroundColor(background)
        webView.setBackgroundColor(background)
        loadingOverlay.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            if (black) intArrayOf(Color.parseColor("#171512"), Color.parseColor("#211E1B"), Color.parseColor("#171512"))
            else intArrayOf(Color.parseColor("#FFFBF2"), Color.parseColor("#FFF1E7"), Color.parseColor("#FFFBF2"))
        )
        loadingLabel.setTextColor(muted)
        loadingProgress.progressTintList = ColorStateList.valueOf(primary)
        loadingProgress.progressBackgroundTintList = ColorStateList.valueOf(track)
        brandBanner.setImageResource(if (black) R.drawable.vienna_brand_banner_dark else R.drawable.vienna_brand_banner_light)
        brandBanner.contentDescription = getString(R.string.app_name)
        root.contentDescription = null
        loadingOverlay.elevation = if (black) 0f else 1f
        refreshNativeAdTheme()
    }

    @Suppress("SetJavaScriptEnabled")
    private fun configureWebView() {
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, false)
        }

        webView.apply {
            setBackgroundColor(Color.parseColor(if (nativeTheme() == THEME_BLACK) "#171512" else "#FFFBF2"))
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            isFocusableInTouchMode = true
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setGeolocationEnabled(true)
            loadsImagesAutomatically = true
            blockNetworkImage = false
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = false
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(false)
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            cacheMode = WebSettings.LOAD_DEFAULT
            defaultTextEncodingName = "UTF-8"
            textZoom = 100
            userAgentString = "$userAgentString VIENNA-Android/${BuildConfig.VERSION_NAME}"
            if (Build.VERSION.SDK_INT >= 26) {
                safeBrowsingEnabled = true
            }
        }

        webView.addJavascriptInterface(ViennaNativeBridge(), "ViennaNative")

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

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                if (!url.isNullOrBlank()) lastMainFrameUrl = url
                hideNativeSearchAd(keepCached = true)
                if (!firstContentShown) showLoading("Abrindo a VIENNA…")
                super.onPageStarted(view, url, favicon)
            }

            override fun onPageCommitVisible(view: WebView?, url: String?) {
                revealWebContent()
                super.onPageCommitVisible(view, url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                view?.let(::applyNativePolish)
                revealWebContent()
                super.onPageFinished(view, url)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    loadingOverlay.animate().cancel()
                    loadingOverlay.visibility = View.GONE
                    showOfflinePage(lastMainFrameUrl ?: "$baseUrl$START_URL_PATH")
                }
                super.onReceivedError(view, request, error)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                loadingProgress.progress = newProgress.coerceIn(0, 100)
                super.onProgressChanged(view, newProgress)
            }

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
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ),
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
                    val picker = fileChooserParams?.createIntent()
                        ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                            type = "*/*"
                            addCategory(Intent.CATEGORY_OPENABLE)
                        }
                    startActivityForResult(picker, FILE_PICKER_REQUEST)
                    true
                } catch (_: ActivityNotFoundException) {
                    fileCallback = null
                    false
                }
            }

            override fun onJsAlert(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
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

    private fun loadStartPage() {
        showLoading("Abrindo a VIENNA…")
        webView.loadUrl("$baseUrl$START_URL_PATH")
    }

    private fun showLoading(message: String) {
        loadingLabel.text = message
        loadingProgress.progress = 8
        loadingOverlay.animate().cancel()
        loadingOverlay.alpha = 1f
        loadingOverlay.visibility = View.VISIBLE
    }

    private fun revealWebContent() {
        if (firstContentShown && loadingOverlay.visibility != View.VISIBLE) return
        firstContentShown = true
        webView.animate().cancel()
        webView.animate().alpha(1f).setDuration(140L).start()
        loadingOverlay.animate().cancel()
        loadingOverlay.animate()
            .alpha(0f)
            .setDuration(LOADER_FADE_MS)
            .withEndAction {
                loadingOverlay.visibility = View.GONE
                loadingOverlay.alpha = 1f
            }
            .start()
    }

    private inner class ViennaNativeBridge {
        @JavascriptInterface
        fun setTheme(mode: String?) {
            val normalized = if (mode == THEME_BLACK) THEME_BLACK else THEME_LIGHT
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(THEME_MODE, normalized).apply()
            mainHandler.post { applyNativeTheme(normalized) }
        }

        @JavascriptInterface
        fun nativeAdsAvailable(): Boolean = true

        @JavascriptInterface
        fun showSearchNativeAd(boundsJson: String?) {
            if (boundsJson.isNullOrBlank()) return
            mainHandler.post { handleNativeSearchAdRequest(boundsJson) }
        }

        @JavascriptInterface
        fun hideSearchNativeAd() {
            mainHandler.post { hideNativeSearchAd(keepCached = true) }
        }
    }

    private fun configureAds() {
        nativeAdHost.visibility = View.GONE
        nativeAdHost.isClickable = false
        nativeAdHost.isFocusable = false
        Thread {
            try {
                MobileAds.initialize(this) {
                    adsInitialized = true
                    mainHandler.post {
                        pendingNativeAdBounds?.let { bounds ->
                            pendingNativeAdBounds = null
                            handleNativeSearchAdRequest(bounds)
                        }
                    }
                }
            } catch (_: Exception) {
                adsInitialized = false
            }
        }.start()
    }

    private fun nativeAdUnitId(): String = if (BuildConfig.DEBUG) {
        // Google requires test inventory during development/debugging.
        ADMOB_TEST_NATIVE_UNIT_ID
    } else {
        ADMOB_NATIVE_UNIT_ID
    }

    private fun handleNativeSearchAdRequest(boundsJson: String) {
        if (!isTrustedCurrentPage()) {
            hideNativeSearchAd(keepCached = true)
            return
        }

        if (!updateNativeAdBounds(boundsJson)) {
            hideNativeSearchAd(keepCached = true)
            return
        }

        if (!adsInitialized) {
            pendingNativeAdBounds = boundsJson
            return
        }

        val freshAd = currentNativeAd?.takeIf {
            System.currentTimeMillis() - currentNativeAdLoadedAt < NATIVE_AD_MAX_AGE_MS
        }
        if (freshAd != null) {
            if (nativeAdHost.childCount == 0) renderNativeAd(freshAd)
            nativeAdHost.visibility = View.VISIBLE
            notifySearchAdState("ready")
            return
        }

        currentNativeAd?.destroy()
        currentNativeAd = null
        nativeAdHost.removeAllViews()
        loadNativeSearchAd()
    }

    private fun updateNativeAdBounds(boundsJson: String): Boolean {
        return try {
            val json = JSONObject(boundsJson)
            val dpr = json.optDouble("dpr", resources.displayMetrics.density.toDouble())
                .coerceIn(0.75, 6.0)
            val left = (json.optDouble("left", -1.0) * dpr).roundToInt()
            val top = (json.optDouble("top", -1.0) * dpr).roundToInt()
            val width = (json.optDouble("width", 0.0) * dpr).roundToInt()
            val height = (json.optDouble("height", 0.0) * dpr).roundToInt()

            val maxWidth = webView.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
            val maxHeight = webView.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
            if (left < 0 || top < 0 || width < dp(220) || height < dp(96)) return false
            if (left >= maxWidth || top >= maxHeight) return false

            val safeWidth = width.coerceAtMost(maxWidth - left)
            val safeHeight = height.coerceAtMost(maxHeight - top)
            if (safeWidth < dp(220) || safeHeight < dp(96)) return false

            nativeAdHost.layoutParams = FrameLayout.LayoutParams(safeWidth, safeHeight).apply {
                leftMargin = left
                topMargin = top
            }
            nativeAdHost.translationZ = dp(18).toFloat()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun loadNativeSearchAd() {
        if (nativeAdLoading) return
        nativeAdLoading = true

        val adLoader = AdLoader.Builder(this, nativeAdUnitId())
            .forNativeAd { ad ->
                nativeAdLoading = false
                if (isFinishing || isDestroyed) {
                    ad.destroy()
                    return@forNativeAd
                }
                currentNativeAd?.destroy()
                currentNativeAd = ad
                currentNativeAdLoadedAt = System.currentTimeMillis()
                renderNativeAd(ad)
                nativeAdHost.visibility = View.VISIBLE
                notifySearchAdState("ready")
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    nativeAdLoading = false
                    hideNativeSearchAd(keepCached = false)
                    notifySearchAdState("failed")
                }
            })
            .build()

        adLoader.loadAd(AdRequest.Builder().build())
    }

    private fun renderNativeAd(ad: NativeAd) {
        nativeAdHost.removeAllViews()
        val black = nativeTheme() == THEME_BLACK
        val cardBg = Color.parseColor(if (black) "#211E1B" else "#FFFFFF")
        val border = Color.parseColor(if (black) "#3B342F" else "#F1DED1")
        val text = Color.parseColor(if (black) "#FFF8F2" else "#27231F")
        val muted = Color.parseColor(if (black) "#C7BBB3" else "#706861")
        val soft = Color.parseColor(if (black) "#2C2723" else "#FFF4EC")
        val accent = Color.parseColor("#F59A62")

        val adView = NativeAdView(this).apply {
            background = roundedDrawable(cardBg, border, 22f)
            elevation = dp(5).toFloat()
            setPadding(dp(14), dp(11), dp(12), dp(11))
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val adBadge = TextView(this).apply {
            text = "Anúncio"
            setTextColor(accent)
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(8), dp(3), dp(8), dp(3))
            background = roundedDrawable(soft, Color.TRANSPARENT, 9f)
        }
        val advertiser = TextView(this).apply {
            setTextColor(muted)
            textSize = 11f
            maxLines = 1
            setPadding(dp(8), 0, dp(6), 0)
        }
        val adChoices = AdChoicesView(this)
        topRow.addView(adBadge, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        topRow.addView(advertiser, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        topRow.addView(adChoices, LinearLayout.LayoutParams(dp(28), dp(28)))
        column.addView(topRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val contentRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(5), 0, 0)
        }
        val media = MediaView(this).apply {
            background = roundedDrawable(soft, Color.TRANSPARENT, 14f)
        }
        contentRow.addView(media, LinearLayout.LayoutParams(dp(82), dp(74)))

        val copy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(11), 0, 0, 0)
        }
        val headline = TextView(this).apply {
            setTextColor(text)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 2
        }
        val body = TextView(this).apply {
            setTextColor(muted)
            textSize = 11f
            maxLines = 2
            setPadding(0, dp(2), 0, dp(5))
        }
        val cta = Button(this).apply {
            isAllCaps = false
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(14), dp(7), dp(14), dp(7))
            background = roundedDrawable(accent, Color.TRANSPARENT, 12f)
        }
        copy.addView(headline, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        copy.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        copy.addView(cta, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        contentRow.addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        column.addView(contentRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        adView.addView(column, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        nativeAdHost.addView(adView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        advertiser.text = ad.advertiser.orEmpty()
        advertiser.visibility = if (ad.advertiser.isNullOrBlank()) View.GONE else View.VISIBLE
        headline.text = ad.headline
        body.text = ad.body.orEmpty()
        body.visibility = if (ad.body.isNullOrBlank()) View.GONE else View.VISIBLE
        cta.text = ad.callToAction.orEmpty()
        cta.visibility = if (ad.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE
        media.visibility = if (ad.mediaContent != null) View.VISIBLE else View.GONE

        adView.headlineView = headline
        adView.bodyView = body
        adView.advertiserView = advertiser
        adView.callToActionView = cta
        adView.mediaView = media
        adView.adChoicesView = adChoices
        adView.setNativeAd(ad)
    }

    private fun refreshNativeAdTheme() {
        val ad = currentNativeAd ?: return
        if (::nativeAdHost.isInitialized && nativeAdHost.childCount > 0) renderNativeAd(ad)
    }

    private fun hideNativeSearchAd(keepCached: Boolean) {
        if (::nativeAdHost.isInitialized) nativeAdHost.visibility = View.GONE
        pendingNativeAdBounds = null
        if (!keepCached) {
            currentNativeAd?.destroy()
            currentNativeAd = null
            currentNativeAdLoadedAt = 0L
            if (::nativeAdHost.isInitialized) nativeAdHost.removeAllViews()
        }
    }

    private fun notifySearchAdState(state: String) {
        val safe = JSONObject.quote(state)
        webView.evaluateJavascript(
            "window.ViennaSearchAds&&window.ViennaSearchAds.onNativeAdState($safe);",
            null
        )
    }

    private fun isTrustedCurrentPage(): Boolean {
        val current = webView.url ?: return false
        return try { isTrustedHost(Uri.parse(current)) } catch (_: Exception) { false }
    }

    private fun roundedDrawable(fill: Int, stroke: Int, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusDp * resources.displayMetrics.density
            setColor(fill)
            if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun applyNativePolish(view: WebView) {
        // Pequena ponte de acabamento: mantém o WebView responsivo e sincroniza
        // o Black/White salvo pelo site com o splash nativo da próxima abertura.
        val script = """
            (function () {
              if (window.__viennaNativePolish) return;
              window.__viennaNativePolish = true;

              var viewport = document.querySelector('meta[name="viewport"]');
              if (!viewport) {
                viewport = document.createElement('meta');
                viewport.name = 'viewport';
                viewport.content = 'width=device-width, initial-scale=1, viewport-fit=cover';
                document.head.appendChild(viewport);
              }

              var style = document.createElement('style');
              style.id = 'vienna-native-polish';
              style.textContent = `
                html { -webkit-text-size-adjust: 100%; text-rendering: optimizeLegibility; }
                body { -webkit-font-smoothing: antialiased; -webkit-tap-highlight-color: transparent; }
                button, a, input, select, textarea, [role="button"] { touch-action: manipulation; }
                @media (prefers-reduced-motion: reduce) {
                  html:focus-within { scroll-behavior: auto !important; }
                }
              `;
              if (!document.getElementById(style.id)) document.head.appendChild(style);

              function syncViennaNativeTheme(value) {
                var mode = value || document.documentElement.dataset.rairoTheme ||
                  localStorage.getItem('rairo.theme.mode.v60') || 'light';
                try {
                  if (window.ViennaNative && window.ViennaNative.setTheme) {
                    window.ViennaNative.setTheme(mode === 'black' ? 'black' : 'light');
                  }
                } catch (_) {}
              }
              syncViennaNativeTheme();
              window.addEventListener('rairo:themechange', function (event) {
                syncViennaNativeTheme(event && event.detail ? event.detail.theme : null);
              });
              new MutationObserver(function () { syncViennaNativeTheme(); }).observe(
                document.documentElement,
                { attributes: true, attributeFilter: ['data-rairo-theme'] }
              );
            })();
        """.trimIndent()
        view.evaluateJavascript(script, null)
    }

    private fun routeNavigation(uri: Uri): Boolean {
        if (uri.scheme == APP_SCHEME) {
            if (uri.host == "retry") {
                webView.loadUrl(uri.getQueryParameter("url") ?: "$baseUrl$START_URL_PATH")
            } else {
                handleAuthUri(uri)
            }
            return true
        }

        if (uri.scheme == "tel" || uri.scheme == "mailto" || uri.scheme == "sms") {
            openExternal(uri)
            return true
        }

        if (isGoogleLoginUrl(uri)) {
            // O clique do usuário já é a confirmação. Abre direto no navegador seguro,
            // removendo um popup e mantendo o mesmo PKCE + deep link do backend.
            startBrowserGoogleLogin()
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
        if (uri.scheme != APP_SCHEME || uri.host != "auth" || uri.path != "/callback") return false
        handleAuthUri(uri)
        return true
    }

    private fun handleAuthUri(uri: Uri) {
        val code = uri.getQueryParameter("code").orEmpty()
        val state = uri.getQueryParameter("state").orEmpty()
        val expected = getSharedPreferences(PREFS, MODE_PRIVATE)
            .getString(PENDING_AUTH_STATE, "")
            .orEmpty()

        if (code.isBlank() || state.isBlank() || expected.isBlank() || state != expected) {
            Toast.makeText(this, "Não foi possível validar o retorno do Google.", Toast.LENGTH_LONG).show()
            webView.loadUrl("$baseUrl/login")
            return
        }
        val verifier = getSharedPreferences(PREFS, MODE_PRIVATE)
            .getString(PENDING_AUTH_VERIFIER, "")
            .orEmpty()
        if (verifier.isBlank()) {
            Toast.makeText(this, "A tentativa de login expirou. Tente novamente.", Toast.LENGTH_LONG).show()
            webView.loadUrl("$baseUrl/login")
            return
        }
        exchangeMobileCode(code, state, verifier)
    }

    private fun exchangeMobileCode(code: String, state: String, verifier: String) {
        showLoading("Concluindo seu acesso…")
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
                    setRequestProperty("User-Agent", "VIENNA-Android/${BuildConfig.VERSION_NAME}")
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
                val entry = response.optString("entry", START_URL_PATH)

                mainHandler.post {
                    val cookie = "$cookieName=$rememberToken; Path=/; Max-Age=$maxAge; Secure; HttpOnly; SameSite=Lax"
                    CookieManager.getInstance().setCookie(baseUrl, cookie) {
                        CookieManager.getInstance().flush()
                        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                            .remove(PENDING_AUTH_STATE)
                            .remove(PENDING_AUTH_VERIFIER)
                            .apply()
                        webView.loadUrl(if (entry.startsWith("/")) "$baseUrl$entry" else entry)
                    }
                }
            } catch (_: Exception) {
                mainHandler.post {
                    loadingOverlay.visibility = View.GONE
                    Toast.makeText(
                        this,
                        "O login expirou ou não pôde ser concluído. Tente novamente.",
                        Toast.LENGTH_LONG
                    ).show()
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

    private fun showOfflinePage(retryUrl: String) {
        firstContentShown = true
        webView.alpha = 1f
        val safeRetryUrl = retryUrl
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
        val html = """
            <!doctype html>
            <html lang="pt-BR">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
              <style>
                *{box-sizing:border-box}
                html,body{margin:0;min-height:100%;background:${if (nativeTheme() == THEME_BLACK) "#171512" else "#FFFBF2"};color:${if (nativeTheme() == THEME_BLACK) "#FFFBF2" else "#27231F"};font-family:system-ui,-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif}
                body{min-height:100vh;display:grid;place-items:center;padding:28px}
                main{width:min(100%,380px);background:${if (nativeTheme() == THEME_BLACK) "#211E1B" else "#FFFFFF"};border:1px solid ${if (nativeTheme() == THEME_BLACK) "#3B342F" else "#F0DED3"};border-radius:28px;padding:28px;text-align:center;box-shadow:0 18px 52px rgba(33,33,50,.08)}
                .mark{width:58px;height:58px;margin:0 auto 18px;border-radius:18px;background:linear-gradient(135deg,#FFC39B,#F59A62,#D9703F);display:grid;place-items:center;color:#fff;font-size:28px;font-weight:800}
                h1{font-size:22px;line-height:1.15;margin:0 0 9px}
                p{font-size:15px;line-height:1.5;color:${if (nativeTheme() == THEME_BLACK) "#C7BBB3" else "#706861"};margin:0 0 22px}
                a{display:block;width:100%;border-radius:16px;padding:14px 18px;background:linear-gradient(135deg,#FFC39B,#F59A62,#D9703F);color:#fff;font-size:15px;font-weight:750;text-decoration:none}
                a:active{transform:scale(.985)}
              </style>
            </head>
            <body>
              <main>
                <div class="mark">➤</div>
                <h1>Sem conexão</h1>
                <p>Confira sua internet e tente abrir a VIENNA novamente.</p>
                <a href="$safeRetryUrl">Tentar novamente</a>
              </main>
            </body>
            </html>
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
                Toast.makeText(
                    this,
                    "Ative a localização nas configurações para usar navegação GPS.",
                    Toast.LENGTH_LONG
                ).show()
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
        currentNativeAd?.destroy()
        currentNativeAd = null
        mainHandler.removeCallbacksAndMessages(null)
        webView.stopLoading()
        webView.webChromeClient = null
        webView.webViewClient = WebViewClient()
        webView.removeAllViews()
        webView.destroy()
        super.onDestroy()
    }
}
