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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private const val NOVEL_AI = "https://novelai.net/stories"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge: draw behind system bars (enableEdgeToEdge needs a newer
        // activity library than the CI runner has, so use the WindowCompat call)
        WindowCompat.setDecorFitsSystemWindows(window, false)
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

                    // Refresh icon floating in NovelAI's blank top-bar center
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = 8.dp)
                            .size(40.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { webView?.reload() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh page",
                            tint = Color(0xFFB8BEC9),
                            modifier = Modifier.size(22.dp),
                        )
                    }
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
