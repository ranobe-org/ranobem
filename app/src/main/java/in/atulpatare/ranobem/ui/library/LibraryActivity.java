package in.atulpatare.ranobem.ui.library;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;

import in.atulpatare.core.models.Manga;
import in.atulpatare.ranobem.R;
import in.atulpatare.ranobem.config.Config;
import in.atulpatare.ranobem.database.AppDatabase;
import in.atulpatare.ranobem.databinding.ActivityLibraryBinding;
import in.atulpatare.ranobem.ui.browse.adapter.MangaAdapter;
import in.atulpatare.ranobem.ui.details.DetailsActivity;
import in.atulpatare.ranobem.utils.DisplayUtils;
import in.atulpatare.ranobem.utils.SpacingDecorator;

public class LibraryActivity extends AppCompatActivity implements MangaAdapter.OnMangaItemClickListener{
    private ActivityLibraryBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityLibraryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        DisplayUtils utils = new DisplayUtils(this, R.layout.item_manga);
        binding.novelList.setLayoutManager(new GridLayoutManager(this, utils.noOfCols()));
        binding.novelList.addItemDecoration(new SpacingDecorator(utils.spacing()));
        binding.progress.hide();
        AppDatabase.getDatabase().mangaDao().getAll().observe(this, mangas -> {
            if (mangas.isEmpty()) {
                binding.emptyLib.setVisibility(View.VISIBLE);
            } else {
                binding.novelList.setAdapter(new MangaAdapter(mangas, this));
            }
        });
    }

    @Override
    public void onMangaItemClick(Manga item) {
        startActivity(new Intent(this, DetailsActivity.class).putExtra(Config.KEY_MANGA, item));
    }
}