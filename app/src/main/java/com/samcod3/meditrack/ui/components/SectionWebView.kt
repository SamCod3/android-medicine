package com.samcod3.meditrack.ui.components

import android.os.Build
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

/**
 * WebView composable for rendering HTML content with dark mode support.
 * Uses CSS injection and WebSettingsCompat.setForceDark for dark theme.
 */
@Composable
fun SectionWebView(
    html: String,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = isSystemInDarkTheme()
) {
    val darkCss = if (isDarkTheme) """
        <style>
            * { 
                background-color: #1C1B1F !important; 
                color: #E6E1E5 !important;
            }
            body { 
                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                padding: 16px;
                margin: 0;
                line-height: 1.6;
                font-size: 17px;
            }
            a { color: #D0BCFF !important; }
            h1, h2, h3, h4, h5, h6 { 
                color: #E6E1E5 !important; 
                margin-top: 16px;
                margin-bottom: 8px;
            }
            ul, ol { 
                padding-left: 24px; 
                margin: 8px 0;
            }
            li { margin: 4px 0; }
            p { margin: 8px 0; }
            table { 
                border-collapse: collapse; 
                width: 100%;
                margin: 8px 0;
            }
            td, th { 
                border: 1px solid #49454F !important; 
                padding: 8px; 
            }
            strong, b { color: #D0BCFF !important; }
        </style>
    """ else """
        <style>
            body { 
                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                padding: 16px;
                margin: 0;
                line-height: 1.6;
                font-size: 17px;
                background: #FFFBFE;
                color: #1C1B1F;
            }
            a { color: #6750A4; }
            h1, h2, h3, h4, h5, h6 { 
                color: #1C1B1F; 
                margin-top: 16px;
                margin-bottom: 8px;
            }
            ul, ol { 
                padding-left: 24px; 
                margin: 8px 0;
            }
            li { margin: 4px 0; }
            p { margin: 8px 0; }
            table { 
                border-collapse: collapse; 
                width: 100%;
                margin: 8px 0;
            }
            td, th { 
                border: 1px solid #CAC4D0; 
                padding: 8px; 
            }
            strong, b { color: #6750A4; }
        </style>
    """
    
    val fullHtml = """
        <!DOCTYPE html>
        <html><head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=yes">
            $darkCss
        </head><body>$html</body></html>
    """.trimIndent()
    
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                // Set layout params to MATCH_PARENT to fill available space
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                
                webViewClient = WebViewClient()
                
                // Enable nested scrolling for BottomSheet compatibility
                isNestedScrollingEnabled = true
                
                // Capture touch events to prevent BottomSheet from stealing scroll
                setOnTouchListener { v, event ->
                    when (event.action) {
                        android.view.MotionEvent.ACTION_DOWN, 
                        android.view.MotionEvent.ACTION_MOVE -> {
                            // Tell parent not to intercept touch events
                            v.parent?.requestDisallowInterceptTouchEvent(true)
                        }
                        android.view.MotionEvent.ACTION_UP,
                        android.view.MotionEvent.ACTION_CANCEL -> {
                            v.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                    false // Let WebView handle the event
                }
                
                settings.apply {
                    javaScriptEnabled = false
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    builtInZoomControls = true
                    displayZoomControls = false
                    textZoom = 100
                }
                
                // Force dark mode on API 29+ using webkit
                if (isDarkTheme && WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                    WebSettingsCompat.setForceDark(
                        settings,
                        WebSettingsCompat.FORCE_DARK_ON
                    )
                }
                
                // Disable force dark strategy to use our CSS instead
                if (isDarkTheme && WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
                    WebSettingsCompat.setForceDarkStrategy(
                        settings,
                        WebSettingsCompat.DARK_STRATEGY_WEB_THEME_DARKENING_ONLY
                    )
                }
                
                setBackgroundColor(if (isDarkTheme) 0xFF1C1B1F.toInt() else 0xFFFFFBFE.toInt())
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(null, fullHtml, "text/html", "UTF-8", null)
        },
        modifier = modifier.fillMaxSize()
    )
}
