package com.samseptiano.webgpu

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                var gpuStatus by remember {
                    mutableStateOf("Menyiapkan WebGPU...")
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF101827)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "WebGPU Demo",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White
                        )

                        Text(
                            text = gpuStatus,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF93C5FD)
                        )

                        WebGPUView(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            onStatusChanged = { status ->
                                gpuStatus = status
                            }
                        )
                    }
                }
            }
        }
    }
}


@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebGPUView(
    modifier: Modifier = Modifier,
    onStatusChanged: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentStatusCallback by androidx.compose.runtime
        .rememberUpdatedState(onStatusChanged)

    val assetLoader = remember(context) {
        androidx.webkit.WebViewAssetLoader.Builder()
            .addPathHandler(
                "/assets/",
                androidx.webkit.WebViewAssetLoader.AssetsPathHandler(context)
            )
            .build()
    }

    val webView = remember(context) {
        WebView(context).apply {
            setBackgroundColor(android.graphics.Color.MAGENTA)

            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true

            WebView.setWebContentsDebuggingEnabled(true)

            addJavascriptInterface(
                object {
                    @JavascriptInterface
                    fun updateStatus(message: String) {
                        post {
                            currentStatusCallback(message)
                        }
                    }
                },
                "AndroidBridge"
            )

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(
                    message: android.webkit.ConsoleMessage
                ): Boolean {
                    android.util.Log.d(
                        "WebGPU-Debug",
                        "${message.message()} " +
                                "(${message.sourceId()}:${message.lineNumber()})"
                    )
                    return true
                }

                override fun onReceivedTitle(
                    view: WebView,
                    title: String
                ) {
                    android.util.Log.d(
                        "WebGPU-Debug",
                        "Page title: $title"
                    )
                }
            }

            object : WebViewClientCompat() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: android.webkit.WebResourceRequest
                ): android.webkit.WebResourceResponse? {
                    return assetLoader.shouldInterceptRequest(request.url)
                }

                override fun onPageStarted(
                    view: WebView,
                    url: String,
                    favicon: android.graphics.Bitmap?
                ) {
                    currentStatusCallback("Memuat: $url")
                    android.util.Log.d("WebGPU-Debug", "Started: $url")
                }

                override fun onPageFinished(
                    view: WebView,
                    url: String
                ) {
                    android.util.Log.d("WebGPU-Debug", "Finished: $url")

                    view.evaluateJavascript(
                        """
                                (function() {
                                    const c = document.getElementById('gpuCanvas');
                                    const r = c ? c.getBoundingClientRect() : null;
                                    return JSON.stringify({
                                        href: location.href,
                                        readyState: document.readyState,
                                        innerWidth: window.innerWidth,
                                        innerHeight: window.innerHeight,
                                        bodyWidth: document.body?.clientWidth,
                                        bodyHeight: document.body?.clientHeight,
                                        canvasFound: !!c,
                                        canvasWidth: c?.width,
                                        canvasHeight: c?.height,
                                        canvasRectWidth: r?.width,
                                        canvasRectHeight: r?.height,
                                        gpuAvailable: !!navigator.gpu
                                    });
                                })();
                                """.trimIndent()
                    ) { result ->
                        android.util.Log.d(
                            "WebGPU-Debug",
                            "DOM diagnostics: $result"
                        )
                        currentStatusCallback("HTML redered")
                    }
                }
            }.also { webViewClient = it }

            loadUrl(
                "https://appassets.androidplatform.net/assets/index.html"
            )
        }
    }

    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .background(Color.Magenta)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { webView },
            update = { view ->
                view.layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        )
    }

    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.removeJavascriptInterface("AndroidBridge")
            webView.destroy()
        }
    }
}