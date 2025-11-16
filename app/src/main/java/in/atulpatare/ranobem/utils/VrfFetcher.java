package in.atulpatare.ranobem.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class VrfFetcher {

    @SuppressLint("SetJavaScriptEnabled")
    public static void fetchVrf(Context context, String pageUrl, String containing, onCompleteListener listener) {
        WebView webView = new WebView(context);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUserAgentString(
                "Mozilla/5.0 (Android) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/99 Mobile Safari/537.36"
        );

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                // nothing as of now
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String reqUrl = request.getUrl().toString();
                if (reqUrl.contains(containing)) {
                    if (listener != null) {
                        listener.onVrf(reqUrl);
                    }

                    // Clean up shortly after to avoid race condition with LiveData
                    new Handler(Looper.getMainLooper()).postDelayed(() -> cleanupWebView(webView), 150);
                }
                return super.shouldInterceptRequest(view, request);
            }
        });

        webView.loadUrl(pageUrl);
    }

    private static void cleanupWebView(WebView webView) {
        try {
            webView.stopLoading();
            webView.clearHistory();
            webView.clearCache(true);
            webView.destroy();
        } catch (Exception e) {
            // nothing
        }
    }

    public interface onCompleteListener {
        public void onVrf(String vrf);
    }
}
