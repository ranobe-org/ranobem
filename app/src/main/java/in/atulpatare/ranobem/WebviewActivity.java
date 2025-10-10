package in.atulpatare.ranobem;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import in.atulpatare.core.util.VrfExtractor;
import in.atulpatare.ranobem.databinding.ActivityMainBinding;
import in.atulpatare.ranobem.databinding.ActivityWebViewBinding;

    public class WebviewActivity extends AppCompatActivity {

        @SuppressLint("SetJavaScriptEnabled")
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            EdgeToEdge.enable(this);
            ActivityWebViewBinding binding = ActivityWebViewBinding.inflate(getLayoutInflater());
            setContentView(binding.getRoot());
            ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });

            WebSettings webSettings = binding.webview.getSettings();
            webSettings.setBuiltInZoomControls(true);
            webSettings.setJavaScriptEnabled(true);
            binding.webview.loadUrl("https://mangafire.to/manga/blue-lockk.kw9j9");
            // https://mangafire.to/ajax/read/kw9j9/chapter/en?vrf=ZBYeRCjYBk0tkZnKW4kTuWBYw641e-csvu6vl7UY4zcaviixmK7VJ-tjpFEsOUq42nE5ZBdEYGJfpA
            // https://mangafire.to/ajax/read/1n2xq/chapter/en?vrf=ZBYeRCjYBk0tkZnKW4kTuWBYw-w1e-csvu6varUY4zcvviixdq7VJ1djpFEsOUq42nE5ZBdEYGJfpA
            binding.webview.setWebViewClient(new WebViewClient() {
                @Nullable
                @Override
                public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                    if (request.getUrl().toString().contains("/ajax/read")) {
                        Log.d("VRF", request.getUrl().toString());
                        Log.d("VRF", VrfExtractor.extractVrf(request.getUrl().toString()));
                    }

                    return super.shouldInterceptRequest(view, request);
                }
            });
        }
    }