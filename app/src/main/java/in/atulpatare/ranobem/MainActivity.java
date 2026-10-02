package in.atulpatare.ranobem;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import in.atulpatare.ranobem.config.Config;
import in.atulpatare.ranobem.databinding.ActivityMainBinding;
import in.atulpatare.ranobem.ui.HomeActivity;
import in.atulpatare.ranobem.ui.downloads.DownloadsActivity;


public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        if (Config.isFree()) {
            binding.proCard.setVisibility(View.VISIBLE);
        }

        binding.footer.setText(getString(R.string.footer_version, getString(R.string.build_with), BuildConfig.VERSION_NAME));

        binding.library.setOnClickListener(v -> openHome("LIBRARY"));
        binding.browse.setOnClickListener(v -> openHome("BROWSE"));
        binding.quickSearch.setOnClickListener(v -> openHome("SEARCH"));
        binding.quickHistory.setOnClickListener(v -> openHome("HISTORY"));

        // the banner jumps down to the card explaining it
        int scrollGap = getResources().getDimensionPixelSize(R.dimen.why_scroll_gap);
        binding.warningBanner.setOnClickListener(v ->
                binding.main.smoothScrollTo(0, binding.whySection.getTop() - scrollGap));
        binding.whyLink.setOnClickListener(v -> navigateToLink(Config.KEEP_ANDROID_OPEN_LINK));
        binding.updatesForm.setOnClickListener(v -> navigateToLink(Config.GOOGLE_FORM_LINK));
        binding.discord.setOnClickListener(v -> navigateToLink(Config.DISCORD_LINK));
        binding.downloadPro.setOnClickListener(v -> navigateToLink(Config.PRO_LINK));
        binding.downloads.setVisibility(Config.isFree() ? View.GONE : View.VISIBLE);
        binding.downloads.setOnClickListener(v -> startActivity(new Intent(this, DownloadsActivity.class)));
        binding.settings.setOnClickListener(v -> openHome(HomeActivity.TARGET_SETTINGS));
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