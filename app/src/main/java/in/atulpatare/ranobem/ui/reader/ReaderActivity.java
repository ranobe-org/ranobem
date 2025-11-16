package in.atulpatare.ranobem.ui.reader;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;

import java.util.List;

import in.atulpatare.core.models.Chapter;
import in.atulpatare.core.models.Manga;
import in.atulpatare.ranobem.R;
import in.atulpatare.ranobem.config.Config;
import in.atulpatare.ranobem.database.AppDatabase;
import in.atulpatare.ranobem.databinding.ActivityReaderBinding;
import in.atulpatare.ranobem.model.ChapterList;
import in.atulpatare.ranobem.model.History;
import in.atulpatare.ranobem.ui.chapters.ChaptersViewModel;
import in.atulpatare.ranobem.ui.details.DetailsActivity;
import in.atulpatare.ranobem.utils.VrfFetcher;

public class ReaderActivity extends AppCompatActivity implements VrfFetcher.onCompleteListener {
    ActivityReaderBinding binding;
    Chapter currentChapter;
    ChapterList list;

    Manga manga;

    ChaptersViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityReaderBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        currentChapter = getIntent().getParcelableExtra(Config.KEY_CHAPTER);
        manga = getIntent().getParcelableExtra(Config.KEY_MANGA);
        String from = getIntent().getStringExtra(Config.KEY_PAGE);

        assert currentChapter != null;
        assert manga != null;
        assert from != null;

        binding.appbar.setTitle(manga.name);
        binding.appbar.setNavigationOnClickListener(v -> {
            // if previous page is history, clear reader activity
            if (from.equals(Config.PAGE_HISTORY)) {
                startActivity(new Intent(this, DetailsActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        .putExtra(Config.KEY_MANGA, manga));
            }
            // finish stuff
            this.finish();
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        viewModel = new ViewModelProvider(this).get(ChaptersViewModel.class);
        viewModel.getError().observe(this, this::setUpError);

        loadChapters();
        loadAllChapters();

        binding.nextChapter.setOnClickListener(v -> loadNextChapter());
    }

    private void setUpError(String error) {
        binding.progress.hide();
        Snackbar.make(binding.getRoot(), error, Snackbar.LENGTH_LONG).show();
    }

    private void loadAllChapters() {
        if (manga.sourceId == 1) {
            String url = "https://mangafire.to" + manga.url.replace("/manga", "/read");
            VrfFetcher.fetchVrf(this, url, "/ajax/read/" + manga.id, vrf -> {
                Manga m = manga;
                m.url = vrf.replace("https://mangafire.to", "");
                new Handler(Looper.getMainLooper()).post(() -> {
                    viewModel.getChapters(m).observe(ReaderActivity.this, this::setAllChapters);
                });
            });
        } else {
            viewModel.getChapters(manga).observe(this, this::setAllChapters);
        }
    }

    private void setAllChapters(List<Chapter> chapters) {
        list = new ChapterList(chapters);
        binding.nextChapter.setEnabled(getNextChapter() != null);
    }

    private void loadChapters() {
        if (currentChapter.sourceId == 1) {
            VrfFetcher.fetchVrf(getApplicationContext(), "https://mangafire.to" + currentChapter.url, "/ajax/read/chapter", this);
        } else {
            viewModel.getChapter(currentChapter).observe(this, this::setUI);
        }
    }

    @Override
    public void onVrf(String vrf) {
        Chapter c = currentChapter;
        // https://mangafire.to/ajax/read/kw9j9/chapter/en?vrf=ZBYeRCjYBk0tkZnKW4kTuWBYw641e-csvu6vl7UY4zcaviixmK7VJ-tjpFEsOUq42nE5ZBdEYGJfpA
        c.url = vrf.replace("https://mangafire.to", "");
        new Handler(Looper.getMainLooper()).post(() -> {
            viewModel.getChapter(c).observe(this, this::setUI);
        });
    }

    private Chapter getNextChapter() {
        int index = -1;
        for (int i = 0; i < list.chapters.size(); i++) {
            Chapter c = list.chapters.get(i);
            if (c.id == currentChapter.id) {
                index = i;
                break;
            }
        }
        if (index > -1 && list.chapters.size() > (index + 1)) {
            return list.chapters.get(index + 1);
        }
        return null;
    }

    private void loadNextChapter() {
        binding.progress.show();
        Chapter next = getNextChapter();
        if (next != null) {
            Toast.makeText(ReaderActivity.this, "Getting next chapter", Toast.LENGTH_LONG).show();
            currentChapter = next;
            if (currentChapter.sourceId == 1) {
                VrfFetcher.fetchVrf(getApplicationContext(), "https://mangafire.to" + currentChapter.url, "/ajax/read/chapter", this);
            } else {
                viewModel.getChapter(currentChapter).observe(this, this::setUI);
            }
        }
    }

    private void setUI(Chapter chapter) {
        binding.progress.hide();
        saveChapterToHistory(chapter);
        binding.chapterTitle.setText(String.format("Chapter %s %s", chapter.index, chapter.name));
        binding.list.setLayoutManager(new LinearLayoutManager(this));
        binding.list.setHasFixedSize(false);
        binding.list.setAdapter(new PageAdapter(chapter.pages));
    }

    private void saveChapterToHistory(Chapter item) {
        History history = new History(manga, item);
        AppDatabase.databaseExecutor.execute(() -> AppDatabase.getDatabase().historyDao().insert(history));
    }
}