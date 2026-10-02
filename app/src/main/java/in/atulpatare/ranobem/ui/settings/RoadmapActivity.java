package in.atulpatare.ranobem.ui.settings;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.snackbar.Snackbar;

import in.atulpatare.ranobem.R;
import in.atulpatare.ranobem.config.Config;
import in.atulpatare.ranobem.databinding.ActivityRoadmapBinding;

/**
 * Features planned for upcoming versions, and a way to suggest more.
 */
public class RoadmapActivity extends AppCompatActivity {
    private ActivityRoadmapBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityRoadmapBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        binding.toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        // planned features are labels, not buttons
        for (int i = 0; i < binding.features.getChildCount(); i++) {
            if (binding.features.getChildAt(i) instanceof SettingOptionView) {
                binding.features.getChildAt(i).setClickable(false);
            }
        }
        binding.suggestFeatureLink.setOnClickListener(v -> openLink(Config.DISCORD_LINK));
    }

    private void openLink(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Snackbar.make(binding.getRoot(), R.string.no_browser, Snackbar.LENGTH_LONG).show();
        }
    }
}
