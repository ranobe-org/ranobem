package in.atulpatare.ranobem.ui;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavGraph;
import androidx.navigation.Navigation;
import androidx.navigation.ui.NavigationUI;

import in.atulpatare.ranobem.R;
import in.atulpatare.ranobem.databinding.ActivityHomeBinding;

public class HomeActivity extends AppCompatActivity {
    public static final String TARGET_FRAGMENT = "TARGET_FRAGMENT";
    public static final String TARGET_SEARCH = "SEARCH";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        ActivityHomeBinding binding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_home);
        String target = getIntent().getStringExtra(TARGET_FRAGMENT);

        // search starts the graph, so back returns to whoever opened it (e.g. a manga's details)
        // and the intent extras (source, author, genre) reach the search screen as arguments
        if (TARGET_SEARCH.equals(target) && savedInstanceState == null) {
            NavGraph graph = navController.getNavInflater().inflate(R.navigation.mobile_navigation);
            graph.setStartDestination(R.id.navigation_search);
            navController.setGraph(graph, getIntent().getExtras());
        }
        NavigationUI.setupWithNavController(binding.navView, navController);

        if (target != null && savedInstanceState == null) {
            switch (target) {
                case "LIBRARY":
                    navController.navigate(R.id.navigation_library);
                    break;
                case "HISTORY":
                    navController.navigate(R.id.navigation_history);
                    break;
            }
        }
    }

}