package in.atulpatare.ranobem.ui;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavGraph;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import java.util.Objects;

import in.atulpatare.ranobem.R;
import in.atulpatare.ranobem.databinding.ActivityHomeBinding;
import in.atulpatare.ranobem.updates.AppUpdateChecker;

/**
 * The app's main screen: the bottom navigation tabs. Opens on Browse unless TARGET_FRAGMENT names
 * another tab.
 */
public class HomeActivity extends AppCompatActivity {
    public static final String TARGET_FRAGMENT = "TARGET_FRAGMENT";
    public static final String TARGET_BROWSE = "BROWSE";
    public static final String TARGET_SEARCH = "SEARCH";
    public static final String TARGET_LIBRARY = "LIBRARY";
    public static final String TARGET_HISTORY = "HISTORY";
    public static final String TARGET_SETTINGS = "SETTINGS";

    private ActivityHomeBinding binding;

    private static int destinationFor(@Nullable String target) {
        if (target == null) return R.id.navigation_browse;
        switch (target) {
            case TARGET_SEARCH:
                return R.id.navigation_search;
            case TARGET_LIBRARY:
                return R.id.navigation_library;
            case TARGET_HISTORY:
                return R.id.navigation_history;
            case TARGET_SETTINGS:
                return R.id.navigation_settings;
            case TARGET_BROWSE:
            default:
                return R.id.navigation_browse;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });
        NavHostFragment host = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_activity_home);
        NavController navController = Objects.requireNonNull(host).getNavController();

        // the tab asked for is where the graph starts, so back from it returns to whoever opened it
        // (e.g. a manga's details) and the other tabs switch normally, rather than it sitting on top of Browse.
        // Set every time, so a rotation or a restored process rebuilds the same graph and the saved
        // back stack lands back on it. The extras (search source, author, genre) reach the first
        // screen as its arguments.
        NavGraph graph = navController.getNavInflater().inflate(R.navigation.mobile_navigation);
        graph.setStartDestination(destinationFor(getIntent().getStringExtra(TARGET_FRAGMENT)));
        navController.setGraph(graph, getIntent().getExtras());
        NavigationUI.setupWithNavController(binding.navView, navController);
    }

    @Override
    protected void onResume() {
        super.onResume();
        AppUpdateChecker.check(this, release -> showUpdateBadge(release != null));
    }

    /**
     * A dot on the Settings tab while a newer version is out, its card is at the top of Settings.
     */
    public void showUpdateBadge(boolean show) {
        if (isFinishing() || isDestroyed()) return;
        if (show) {
            binding.navView.getOrCreateBadge(R.id.navigation_settings).setVisible(true);
        } else {
            binding.navView.removeBadge(R.id.navigation_settings);
        }
    }

}