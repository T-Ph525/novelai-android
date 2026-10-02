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
import androidx.activity.compose.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

private const val NOVEL_AI = "https://novelai.net/stories"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { NovelAIApp() }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun NovelAIApp() {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(value = false) }
    val isInPreview = LocalInspectionMode.current

    // Edge refresh handle: shows briefly, then auto-hides until you tap the right edge
    var edgeButtonVisible by remember { mutableStateOf(true) }
    var edgeButtonShowTick by remember { mutableStateOf(0) }
    val edgeButtonAlpha by animateFloatAsState(
        targetValue = if (edgeButtonVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "edgeButtonAlpha",
    )

    fun showEdgeButton() {
        edgeButtonShowTick++
        edgeButtonVisible = true
    }

    LaunchedEffect(edgeButtonShowTick) {
        delay(4000)
        edgeButtonVisible = false
    }

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
                                    onLoadStarted = { showEdgeButton() },
                                ) { callback ->
                                    filePathCallback?.onReceiveValue(null)
                                    filePathCallback = callback
                                    filePickerLauncher.launch("*/*")
                                }
                                loadUrl(NOVEL_AI)
                            }
                        },
                    )

                    // Invisible strip along the right edge: tap it to bring the handle back
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(24.dp)
                            .height(96.dp)
                            .pointerInput(Unit) {
                                detectTapGestures { showEdgeButton() }
                            },
                    )

                    // Edge-style refresh handle: half-tucked into the right edge, vertically centered
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .offset(x = 24.dp)
                            .size(48.dp)
                            .graphicsLayer { alpha = edgeButtonAlpha }
                            .shadow(6.dp, CircleShape)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable(enabled = edgeButtonVisible) {
                                webView?.reload()
                                showEdgeButton()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh page",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
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
    onLoadStarted: () -> Unit,
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

        override fun onPageStarted(v: WebView, url: String?, favicon: android.graphics.Bitmap?) {
            onLoadStarted()
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
