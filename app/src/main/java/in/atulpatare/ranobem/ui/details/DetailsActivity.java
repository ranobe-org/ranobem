package in.atulpatare.ranobem.ui.details;

import android.os.Bundle;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

import in.atulpatare.core.models.Manga;
import in.atulpatare.ranobem.R;
import in.atulpatare.ranobem.config.Config;
import in.atulpatare.ranobem.databinding.ActivityDetailsBinding;
import in.atulpatare.ranobem.ui.chapters.ChapterFragment;
import in.atulpatare.ranobem.ui.details.sheet.WatchVideoSheet;

public class DetailsActivity extends AppCompatActivity {
    private ActivityDetailsBinding binding;

    private Manga manga;  private RewardedAd rewardedAd;
    private final FullScreenContentCallback callback = new FullScreenContentCallback() {

        @Override
        public void onAdDismissedFullScreenContent() {
            rewardedAd = null;
        }

        @Override
        public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
            rewardedAd = null;
            loadVideoAd();
        }

        @Override
        public void onAdImpression() {
            rewardedAd = null;
        }

    };

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

        binding.readChapter.setOnClickListener(v -> showAdOrNavigate());

        DetailsViewModel viewModel = new ViewModelProvider(this).get(DetailsViewModel.class);

//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//            manga = getIntent().getParcelableExtra(Config.KEY_MANGA, Manga.class);
//        } else {
        manga = getIntent().getParcelableExtra(Config.KEY_MANGA);
//        }

        assert manga != null;
        viewModel.getDetails(manga.sourceId, manga).observe(this, this::setUpUi);

        if (Config.isFree()) {
            loadVideoAd();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (rewardedAd != null) {
            rewardedAd = null;
        }
    }

    private void loadVideoAd() {
        AdRequest adRequest = new AdRequest.Builder().build();
        RewardedAd.load(this, getString(R.string.ad_read_manga_video_id),
                adRequest, new RewardedAdLoadCallback() {
                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        rewardedAd = null;
                    }

                    @Override
                    public void onAdLoaded(@NonNull RewardedAd ad) {
                        Log.d("DEBUG", "AD LOADED,.....");
                        rewardedAd = ad;
                        rewardedAd.setFullScreenContentCallback(callback);
                    }
                });
    }

    private void showAdOrNavigate() {
        if (Config.isFree()) {
            // show ad
            WatchVideoSheet sheet = new WatchVideoSheet(() -> {
                // show ad
                if (rewardedAd != null) {
                    rewardedAd.show(this, rewardItem -> navigateToChapterList());
                } else {
                    navigateToChapterList();
                }
            });
            sheet.show(getSupportFragmentManager(), WatchVideoSheet.TAG);
        } else {
            navigateToChapterList();
        }
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