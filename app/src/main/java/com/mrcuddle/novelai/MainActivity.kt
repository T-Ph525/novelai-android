package com.mrcuddle.novelai

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.ViewGroup
import android.webkit.URLUtil
import androidx.compose.runtime.DisposableEffect
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
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

private const val NOVEL_AI = "https://novelai.net/stories"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NovelAIApp() }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun NovelAIApp() {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(value = false) }
    val isInPreview = LocalInspectionMode.current

    var filePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
    ) { uris ->
        filePathCallback?.onReceiveValue(uris.toTypedArray())
        filePathCallback = null
    }

    DisposableEffect(webView) {
        onDispose {
            webView?.apply {
                stopLoading()
                webChromeClient = null
                webViewClient = WebViewClient()
                (parent as? ViewGroup)?.removeView(this)
                destroy()
            }
        }
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
                Box(Modifier.fillMaxSize()) {
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
    onShowFileChooserRequest: (ValueCallback<Array<Uri>>) -> Unit,
) {
    with(view.settings) {
        javaScriptEnabled = true
        domStorageEnabled = true
        loadsImagesAutomatically = true
        javaScriptCanOpenWindowsAutomatically = false
        allowFileAccess = false
        allowContentAccess = true
        allowFileAccessFromFileURLs = false
        allowUniversalAccessFromFileURLs = false
        safeBrowsingEnabled = true
        setSupportMultipleWindows(false)
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        cacheMode = WebSettings.LOAD_DEFAULT
        userAgentString = "$userAgentString NovelAI-Android/2.0"
    }

    CookieManager.getInstance().apply {
        setAcceptCookie(true)
        setAcceptThirdPartyCookies(view, false)
    }

    view.webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
            val url = request.url
            val scheme = url.scheme?.lowercase()
            val host = url.host?.lowercase().orEmpty()
            // Only the actual NovelAI domain (and its subdomains) stays in the WebView.
            // Avoid substring checks that would trust attacker-controlled lookalike hosts.
            if (scheme == "https" && (host == "novelai.net" || host.endsWith(".novelai.net"))) {
                return false
            }
            if (scheme == "https" || scheme == "http" || scheme == "mailto" || scheme == "tel") {
                try {
                    v.context.startActivity(Intent(Intent.ACTION_VIEW, url).apply {
                        addCategory(Intent.CATEGORY_BROWSABLE)
                    })
                } catch (_: Exception) {
                    // No compatible external handler is installed.
                }
            }
            return true
        }

        override fun doUpdateVisitedHistory(v: WebView, url: String?, isReload: Boolean) {
            onHistoryChanged(v.canGoBack())
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
            if (Uri.parse(url).scheme == "https") {
                val request = DownloadManager.Request(Uri.parse(url))
                    .setMimeType(mimeType)
                    .setTitle(guessFilename(contentDisposition, mimeType))
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
    val suggested = URLUtil.guessFileName(null, contentDisposition, mimeType)
    val fallback = when (mimeType) {
        "application/json" -> "novelai-export.json"
        "text/plain" -> "novelai-story.txt"
        "text/markdown" -> "novelai-story.md"
        else -> "novelai-download"
    }
    // Strip path separators and control characters from untrusted response headers.
    return (suggested.takeIf { it.isNotBlank() } ?: fallback)
        .substringAfterLast('/').substringAfterLast('\\')
        .replace(Regex("[\\x00-\\x1f\\x7f]"), "_")
        .take(120)
        .ifBlank { fallback }
}
