package com.mrcuddle.novelai

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

private const val NOVEL_AI = "https://novelai.net/stories"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent { NovelAIApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun NovelAIApp() {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(value = false) }
    var isRefreshing by remember { mutableStateOf(value = false) }
    val isInPreview = LocalInspectionMode.current

    var filePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
    ) { uris ->
        filePathCallback?.onReceiveValue(uris.toTypedArray())
        filePathCallback = null
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(Modifier.fillMaxSize()) {
            if (isInPreview) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("NovelAI WebView Placeholder (Preview Mode)")
                }
            } else {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        isRefreshing = true
                        webView?.reload()
                    },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            WebView(context).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                )
                                webView = this
                                configureNovelAIWebView(
                                    context = context,
                                    view = this,
                                    onHistoryChanged = { canGoBack = it },
                                    onPageLoadFinished = { isRefreshing = false },
                                ) { callback ->
                                    filePathCallback?.onReceiveValue(null)
                                    filePathCallback = callback
                                    filePickerLauncher.launch("*/*")
                                }
                                loadUrl(NOVEL_AI)
                            }
                        },
                    )
                }
            }
        }
    }

    BackHandler(enabled = canGoBack) {
        webView?.goBack()
    }
}

@Preview(showBackground = true)
@Composable
private fun NovelAIAppPreview() {
    NovelAIApp()
}

@SuppressLint("SetJavaScriptEnabled")
private fun configureNovelAIWebView(
    context: Context,
    view: WebView,
    onHistoryChanged: (Boolean) -> Unit,
    onPageLoadFinished: () -> Unit,
    onShowFileChooserRequest: (ValueCallback<Array<Uri>>) -> Unit,
) {
    with(view.settings) {
        javaScriptEnabled = true
        domStorageEnabled = true
        loadsImagesAutomatically = true
        javaScriptCanOpenWindowsAutomatically = true
        setSupportMultipleWindows(false)
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        cacheMode = WebSettings.LOAD_DEFAULT
        userAgentString = "$userAgentString NovelAI-Android/2.0"
    }

    CookieManager.getInstance().apply {
        setAcceptCookie(true)
        setAcceptThirdPartyCookies(view, true)
    }

    view.webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
            val url = request.url
            val host = url.host.orEmpty()
            return if (host.endsWith("novelai.net") || host.contains("novelai")) {
                false
            } else {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, url)
                    v.context.startActivity(intent)
                } catch (_: Exception) {
                    // Ignore if no activity can handle the external intent
                }
                true
            }
        }

        override fun doUpdateVisitedHistory(v: WebView, url: String?, isReload: Boolean) {
            onHistoryChanged(v.canGoBack())
        }

        override fun onPageFinished(v: WebView, url: String?) {
            onPageLoadFinished()
        }
    }

    view.webChromeClient = object : WebChromeClient() {
        override fun onShowFileChooser(
            webView: WebView?,
            filePathCallback: ValueCallback<Array<Uri>>?,
            fileChooserParams: FileChooserParams?,
        ): Boolean {
            if (filePathCallback != null) {
                onShowFileChooserRequest(filePathCallback)
                return true
            }
            return false
        }
    }

    view.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
        try {
            if (url.startsWith("http://") || url.startsWith("https://")) {
                val request = DownloadManager.Request(Uri.parse(url))
                    .setMimeType(mimeType)
                    .setTitle("NovelAI download")
                    .setDescription("Downloading from NovelAI")
                    .setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
                    )
                    .addRequestHeader("User-Agent", userAgent)
                    .setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS,
                        guessFilename(contentDisposition, mimeType),
                    )

                CookieManager.getInstance().getCookie(url)?.let {
                    request.addRequestHeader("Cookie", it)
                }

                val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                manager.enqueue(request)
            }
        } catch (_: Exception) {
            // Ignore download errors
        }
    }
}

private fun guessFilename(contentDisposition: String?, mimeType: String?): String {
    val match = Regex("""filename="?([^";]+)""").find(contentDisposition.orEmpty())
    return match?.groupValues?.get(1) ?: when (mimeType) {
        "application/json" -> "novelai-export.json"
        "text/plain" -> "novelai-story.txt"
        "text/markdown" -> "novelai-story.md"
        else -> "novelai-download"
    }
}
