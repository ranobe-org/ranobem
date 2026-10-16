package in.atulpatare.ranobem;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import in.atulpatare.ranobem.config.Config;
import in.atulpatare.ranobem.databinding.ActivityMainBinding;
import in.atulpatare.ranobem.ui.HomeActivity;
import in.atulpatare.ranobem.ui.downloads.DownloadsActivity;
import in.atulpatare.ranobem.updates.AppUpdateChecker;


public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        binding.footer.setText(getString(R.string.footer_version, getString(R.string.build_with), BuildConfig.VERSION_NAME));

        binding.library.setOnClickListener(v -> openHome("LIBRARY"));
        binding.browse.setOnClickListener(v -> openHome("BROWSE"));
        binding.quickSearch.setOnClickListener(v -> openHome("SEARCH"));
        binding.quickHistory.setOnClickListener(v -> openHome("HISTORY"));

        binding.releases.setOnClickListener(v -> navigateToLink(Config.RELEASES_LINK));
        binding.discord.setOnClickListener(v -> navigateToLink(Config.DISCORD_LINK));
        binding.downloads.setOnClickListener(v -> startActivity(new Intent(this, DownloadsActivity.class)));
        binding.settings.setOnClickListener(v -> openHome(HomeActivity.TARGET_SETTINGS));
    }

    @Override
    protected void onResume() {
        super.onResume();
        AppUpdateChecker.check(this, this::showUpdate);
    }

    private void showUpdate(@Nullable AppUpdateChecker.Release release) {
        if (isFinishing() || isDestroyed()) return;
        if (release == null) {
            binding.updateCard.setVisibility(View.GONE);
            return;
        }
        binding.updateTitle.setText(getString(R.string.update_title, release.version()));
        binding.updateDesc.setText(getString(R.string.update_desc, BuildConfig.VERSION_NAME));
        // the APK straight away when the release has one, its page otherwise
        binding.updateDownload.setOnClickListener(v -> navigateToLink(release.apkUrl != null ? release.apkUrl : release.pageUrl));
        binding.updateNotes.setOnClickListener(v -> navigateToLink(release.pageUrl));
        binding.updateDismiss.setOnClickListener(v -> {
            AppUpdateChecker.dismiss(this, release);
            binding.updateCard.setVisibility(View.GONE);
        });
        binding.updateCard.setVisibility(View.VISIBLE);
    }

    private void openHome(String target) {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.putExtra("TARGET_FRAGMENT", target);
        startActivity(intent);
    }

    private void navigateToLink(String url) {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }
}