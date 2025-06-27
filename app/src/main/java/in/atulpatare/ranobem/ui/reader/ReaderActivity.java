package in.atulpatare.ranobem.ui.reader;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;

import in.atulpatare.core.models.Chapter;
import in.atulpatare.ranobem.R;
import in.atulpatare.ranobem.config.Config;
import in.atulpatare.ranobem.databinding.ActivityReaderBinding;
import in.atulpatare.ranobem.model.ChapterList;
import in.atulpatare.ranobem.ui.chapters.ChaptersViewModel;

public class ReaderActivity extends AppCompatActivity {
    ActivityReaderBinding binding;
    Chapter currentChapter;
    ChapterList list;

    ChaptersViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityReaderBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        currentChapter = getIntent().getParcelableExtra(Config.KEY_CHAPTER);
        list = getIntent().getParcelableExtra(Config.KEY_CHAPTER_LIST);

        assert currentChapter != null;
        assert list != null;

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        viewModel = new ViewModelProvider(this).get(ChaptersViewModel.class);
        viewModel.getChapter(currentChapter).observe(this, this::setUI);

        binding.nextChapter.setOnClickListener(v -> loadNextChapter());

        if (getNextChapter() == null) {
            binding.nextChapter.setEnabled(false);
        }
    }

    private Chapter getNextChapter() {
        int index = -1;
        for (int i = 0; i < list.chapters.size(); i++) {
            Chapter c = list.chapters.get(i);
            if (c.index == currentChapter.index ) {
                index = i;
            }
        }
        if(index > -1 && list.chapters.size() > (index + 1) ) {
            Log.d("DEBUG", index + " " + index + 1);
            Log.d("DEBUG first", currentChapter.toString());
            Log.d("DEBUG second", list.chapters.get(index + 1).toString());
            return list.chapters.get(index + 1);
        }
        return null;
    }

    private void loadNextChapter() {
        Chapter next = getNextChapter();
        if (next != null) {
            Toast.makeText(ReaderActivity.this, "Getting next chapter", Toast.LENGTH_LONG).show();
            currentChapter = next;
            viewModel.getChapter(currentChapter).observe(this, this::setUI);
        }
    }

    private void setUI(Chapter chapter) {
        Log.d("DEBUG", "got next chapter " + chapter);
        binding.chapterTitle.setText(String.format("%s %s", chapter.index, chapter.name));
        binding.list.setLayoutManager(new LinearLayoutManager(this));
        binding.list.setHasFixedSize(false);
        binding.list.setAdapter(new PageAdapter(chapter.pages));
    }
}