package in.atulpatare.ranobem.ui.details;

import android.os.Bundle;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;

import in.atulpatare.core.models.Manga;
import in.atulpatare.ranobem.config.Config;
import in.atulpatare.ranobem.databinding.ActivityDetailsBinding;
import in.atulpatare.ranobem.ui.chapters.ChapterFragment;

public class DetailsActivity extends AppCompatActivity {
    private ActivityDetailsBinding binding;

    private Manga manga;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityDetailsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        binding.readChapter.setOnClickListener(v -> navigateToChapterList());

        DetailsViewModel viewModel = new ViewModelProvider(this).get(DetailsViewModel.class);

//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//            manga = getIntent().getParcelableExtra(Config.KEY_MANGA, Manga.class);
//        } else {
        manga = getIntent().getParcelableExtra(Config.KEY_MANGA);
//        }

        Log.d("DEBUG", manga.toString());

        assert manga != null;
        viewModel.getDetails(manga.sourceId, manga).observe(this, this::setUpUi);
    }

    private void setUpUi(Manga manga) {
        this.manga = manga;
        Glide.with(binding.novelCover.getContext()).load(manga.cover).into(binding.novelCover);
        binding.novelName.setText(manga.name);
        binding.rating.setRating((float) manga.rating / 2);
        binding.summary.setText(manga.summary);
        binding.status.setText(manga.status);
        binding.authors.setText(manga.author);
        binding.progress.hide();
    }


    private void navigateToChapterList() {
        if (manga == null) return;
        Bundle bundle = new Bundle();
        bundle.putParcelable(Config.KEY_MANGA, manga);
        ChapterFragment chapters = new ChapterFragment();
        chapters.setArguments(bundle);
        chapters.show(getSupportFragmentManager(), "chapters-sheet");
    }
}