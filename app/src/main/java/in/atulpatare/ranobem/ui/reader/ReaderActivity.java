package in.atulpatare.ranobem.ui.reader;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.Objects;

import in.atulpatare.core.models.Chapter;
import in.atulpatare.ranobem.R;
import in.atulpatare.ranobem.config.Config;
import in.atulpatare.ranobem.databinding.ActivityReaderBinding;
import in.atulpatare.ranobem.ui.chapters.ChaptersViewModel;
import in.atulpatare.ranobem.utils.HtmlBuilder;
import in.atulpatare.ranobem.utils.NumberUtils;

public class ReaderActivity extends AppCompatActivity {
    ActivityReaderBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityReaderBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());


        Chapter chapter = getIntent().getParcelableExtra(Config.KEY_CHAPTER);
        assert chapter != null;

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        ChaptersViewModel viewModel = new ViewModelProvider(this).get(ChaptersViewModel.class);
        viewModel.getChapter(chapter).observe(this, this::setUI);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setUI(Chapter chapter) {
        binding.list.setLayoutManager(new LinearLayoutManager(this));
        binding.list.setHasFixedSize(false);
        binding.list.setAdapter(new PageAdapter(chapter.pages));
    }
}