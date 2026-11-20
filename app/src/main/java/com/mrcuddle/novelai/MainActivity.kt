package com.mrcuddle.novelai

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.graphics.Bitmap
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.view.WindowManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Environment
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.URLUtil
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private const val HOME = "https://novelai.net/stories"
private const val PREFS = "novelai_local"
private const val LAST_URL = "last_trusted_url"

private fun trusted(url: String): Boolean {
    val uri = Uri.parse(url)
    val host = uri.host?.lowercase() ?: return false
    return uri.scheme == "https" && (host == "novelai.net" || host.endsWith(".novelai.net"))
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val shortcutManager = getSystemService(ShortcutManager::class.java)
        val resume = ShortcutInfo.Builder(this, "resume")
            .setShortLabel("Resume writing")
            .setIcon(Icon.createWithResource(this, R.drawable.novelai_icon))
            .setIntent(Intent(this, MainActivity::class.java).setAction(Intent.ACTION_MAIN))
            .build()
        val fresh = ShortcutInfo.Builder(this, "new")
            .setShortLabel("Open NovelAI home")
            .setIcon(Icon.createWithResource(this, R.drawable.novelai_icon))
            .setIntent(Intent(this, MainActivity::class.java).setAction("com.mrcuddle.novelai.NEW_STORY"))
            .build()
        shortcutManager?.dynamicShortcuts = listOf(resume, fresh)
        setContent { NovelAIApp(intent?.action == "com.mrcuddle.novelai.NEW_STORY") }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun NovelAIApp(startFresh: Boolean) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    val isPreview = LocalInspectionMode.current
    var rendererGeneration by remember { mutableStateOf(0) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }
    var pageError by remember { mutableStateOf<String?>(null) }
    var confirmExit by remember { mutableStateOf(false) }
    var fileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        fileCallback?.onReceiveValue(uris.toTypedArray())
        fileCallback = null
    }
    val initialUrl = remember {
        prefs.getString(LAST_URL, HOME).orEmpty().takeIf { !startFresh && trusted(it) } ?: HOME
    }


    MaterialTheme(colorScheme = darkColorScheme()) {
        Column(Modifier.fillMaxSize()) {
            if (isPreview) {
                Text("NovelAI Android", Modifier.padding(16.dp))
            } else {
                androidx.compose.foundation.layout.Box(Modifier.weight(1f).fillMaxWidth()) {
                    androidx.compose.runtime.key(rendererGeneration) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(-1, -1)
                                webView = this
                                isSaveEnabled = true
                                with(settings) {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    loadsImagesAutomatically = true
                                    javaScriptCanOpenWindowsAutomatically = false
                                    setSupportMultipleWindows(false)
                                    allowFileAccess = false
                                    allowContentAccess = true
                                    allowFileAccessFromFileURLs = false
                                    allowUniversalAccessFromFileURLs = false
                                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                    safeBrowsingEnabled = true
                                    cacheMode = WebSettings.LOAD_DEFAULT
                                }
                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                        if (trusted(request.url.toString())) return false
                                        val scheme = request.url.scheme
                                        if (scheme in listOf("https", "http", "mailto", "tel")) {
                                            try {
                                                ctx.startActivity(Intent(Intent.ACTION_VIEW, request.url).addCategory(Intent.CATEGORY_BROWSABLE))
                                            } catch (_: Exception) {}
                                        }
                                        return true
                                    }
                                    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                                        // Renderer is unusable. Compose releases it and creates a fresh WebView.
                                        if (webView === view) webView = null
                                        pageError = null
                                        loading = true
                                        rendererGeneration++
                                        return true
                                    }
                                    override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                                        loading = true
                                        pageError = null
                                    }
                                    override fun onPageFinished(view: WebView, url: String) {
                                        loading = false
                                        if (trusted(url)) prefs.edit().putString(LAST_URL, url).apply()
                                        CookieManager.getInstance().flush()
                                    }
                                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
                                        if (request.isForMainFrame) {
                                            loading = false
                                            pageError = error.description?.toString() ?: "Page could not load"
                                        }
                                    }
                                }
                                webChromeClient = object : WebChromeClient() {
                                    override fun onShowFileChooser(
                                        webView: WebView?,
                                        filePathCallback: ValueCallback<Array<Uri>>?,
                                        fileChooserParams: FileChooserParams?
                                    ): Boolean {
                                        if (filePathCallback == null) return false
                                        fileCallback?.onReceiveValue(null)
                                        fileCallback = filePathCallback
                                        filePicker.launch("*/*")
                                        return true
                                    }
                                }
                                setDownloadListener { url, agent, disposition, mime, _ ->
                                    try {
                                        if (Uri.parse(url).scheme != "https") return@setDownloadListener
                                        val name = URLUtil.guessFileName(url, disposition, mime)
                                            .substringAfterLast('/').substringAfterLast('\\')
                                            .replace(Regex("[\\x00-\\x1f\\x7f]"), "_")
                                            .take(120).ifBlank { "novelai-download" }
                                        val request = DownloadManager.Request(Uri.parse(url))
                                            .setTitle(name)
                                            .setMimeType(mime)
                                            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                                            .addRequestHeader("User-Agent", agent)
                                        CookieManager.getInstance().getCookie(url)?.let { request.addRequestHeader("Cookie", it) }
                                        (ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                                    } catch (_: Exception) {}
                                }
                                loadUrl(initialUrl)
                            }
                        },
                        onRelease = { released ->
                            try { released.stopLoading() } catch (_: Exception) {}
                            released.webChromeClient = null
                            released.webViewClient = WebViewClient()
                            (released.parent as? ViewGroup)?.removeView(released)
                            try { released.destroy() } catch (_: Exception) {}
                            if (webView === released) webView = null
                        }
                    )
                    }
                    if (loading) CircularProgressIndicator(Modifier.align(Alignment.Center))
                    pageError?.let { error ->
                        Column(
                            Modifier.align(Alignment.Center).padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(error)
                            Button(onClick = { pageError = null; webView?.reload() }) { Text("Retry") }
                        }
                    }
                }

            }
        }
        if (confirmExit) {
            AlertDialog(
                onDismissRequest = { confirmExit = false },
                title = { Text("Leave NovelAI?") },
                text = { Text("Make sure your work is saved before leaving.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmExit = false
                        (context as? ComponentActivity)?.finish()
                    }) { Text("Exit") }
                },
                dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Stay") } }
            )
        }
    }
    BackHandler {
        if (confirmExit) {
            confirmExit = false
        } else if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            confirmExit = true
        }
    }
}
