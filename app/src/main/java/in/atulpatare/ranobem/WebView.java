package in.atulpatare.ranobem;

import static android.webkit.WebSettings.LOAD_NO_CACHE;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.WebViewClient;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.HashMap;

import in.atulpatare.ranobem.databinding.ActivityWebViewBinding;

public class WebView extends AppCompatActivity {

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        in.atulpatare.ranobem.databinding.ActivityWebViewBinding binding = ActivityWebViewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        String homePage = "https://mangafire.to/home";

        binding.webview.loadUrl(homePage, new HashMap<>() {{
            put("referer", "https://mangafire.to/home");
            put("user-agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36");
        }});
        CookieManager.getInstance().acceptCookie();
        CookieManager.getInstance().acceptThirdPartyCookies(binding.webview);
        binding.webview.getSettings().setJavaScriptEnabled(true);
        binding.webview.getSettings().setDomStorageEnabled(true);
        binding.webview.getSettings().setCacheMode(LOAD_NO_CACHE);
        binding.webview.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(android.webkit.WebView view, String url) {
                super.onPageFinished(view, url);
                String cookies = CookieManager.getInstance().getCookie(homePage);
                Log.d("COOKIES", cookies != null ? cookies : "empty");
            }
        });
    }
}