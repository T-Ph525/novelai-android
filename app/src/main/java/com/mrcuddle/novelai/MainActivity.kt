package com.mrcuddle.novelai

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private const val NOVEL_AI = "https://novelai.net/"

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

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun NovelAIApp() {
    var webView by remember { mutableStateOf<WebView?>(null) }
    val isInPreview = LocalInspectionMode.current

    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(Modifier.fillMaxSize()) {
            if (isInPreview) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("NovelAI WebView Placeholder (Preview Mode)")
                }
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            webView = this
                            configureNovelAIWebView(context, this)
                            loadUrl(NOVEL_AI)
                        }
                    }
                )
            }
        }
    }

    BackHandler(enabled = webView?.canGoBack() == true) {
        webView?.goBack()
    }
}

@Preview(showBackground = true)
@Composable
private fun NovelAIAppPreview() {
    NovelAIApp()
}

@SuppressLint("SetJavaScriptEnabled")
private fun configureNovelAIWebView(context: Context, view: WebView) {
    with(view.settings) {
        javaScriptEnabled = true
        domStorageEnabled = true
        loadsImagesAutomatically = true
        javaScriptCanOpenWindowsAutomatically = true
        setSupportMultipleWindows(true)
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
            val host = request.url.host.orEmpty()
            return if (host.endsWith("novelai.net")) {
                false
            } else {
                v.context.startActivity(
                    android.content.Intent(android.content.Intent.ACTION_VIEW, request.url)
                )
                true
            }
        }
    }

    view.webChromeClient = WebChromeClient()

    view.setDownloadListener(
        DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            val request = DownloadManager.Request(Uri.parse(url))
                .setMimeType(mimeType)
                .setTitle("NovelAI download")
                .setDescription("Downloading from NovelAI")
                .setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                .addRequestHeader("User-Agent", userAgent)
                .setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    guessFilename(contentDisposition, mimeType)
                )

            CookieManager.getInstance().getCookie(url)?.let {
                request.addRequestHeader("Cookie", it)
            }

            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
        }
    )
}

private fun guessFilename(contentDisposition: String?, mimeType: String?): String {
    val match = Regex("""filename="?([^";]+)""").find(contentDisposition.orEmpty())
    if (match != null) return match.groupValues[1]
    return when (mimeType) {
        "application/json" -> "novelai-export.json"
        "text/plain" -> "novelai-story.txt"
        "text/markdown" -> "novelai-story.md"
        else -> "novelai-download"
    }
}