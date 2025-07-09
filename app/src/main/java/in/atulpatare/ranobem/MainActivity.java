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
import in.atulpatare.ranobem.ui.browse.BrowseActivity;


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
            binding.downloadProContainer.setVisibility(View.VISIBLE);
        }

        binding.browse.setOnClickListener(v -> startActivity(new Intent(this, BrowseActivity.class)));
        binding.discord.setOnClickListener(v -> navigateToLink("https://discord.gg/6CQ6u64dca"));
        binding.x.setOnClickListener(v -> navigateToLink("https://x.com/atul_patare"));
        binding.lightnovels.setOnClickListener(v -> navigateToLink("https://play.google.com/store/apps/details?id=org.ranobe.downloader"));
        binding.downloadPro.setOnClickListener(v -> navigateToLink("https://play.google.com/store/apps/details?id=in.atulpatare.ranobem.pro"));
    }

    private void navigateToLink(String url) {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }
}