package in.atulpatare.ranobem.ui.search.modal;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import in.atulpatare.ranobem.databinding.SheetGenresBinding;

public class FilterModal extends BottomSheetDialogFragment {
    public static final String TAG = "filter-modal";
    private final HashMap<String, String> genres;
    private final List<String> selectedGenres;
    private final UpdateFilterListener listener;

    public FilterModal(HashMap<String, String> genres, UpdateFilterListener listener, List<String> selectedGenres) {
        this.genres = genres;
        this.listener = listener;
        this.selectedGenres = selectedGenres;
        Log.d("FILTERS", selectedGenres.toString());
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        SheetGenresBinding binding = SheetGenresBinding.inflate(inflater, container, false);
        for (Map.Entry<String, String> entry : genres.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            boolean checked = selectedGenres.contains(key);

            Chip chip = new Chip(getContext());
            chip.setTag(key);
            chip.setText(value);
            chip.setCheckable(true);
            chip.setChecked(checked);
            binding.genreList.addView(chip);
        }

        binding.applyFilter.setOnClickListener(v -> {
            List<String> selectedKeys = new ArrayList<>();

            // Iterate through all children of the ChipGroup
            for (int i = 0; i < binding.genreList.getChildCount(); i++) {
                View child = binding.genreList.getChildAt(i);

                if (child instanceof Chip) {
                    Chip chip = (Chip) child;
                    if (chip.isChecked()) {
                        // Retrieve that URL-friendly key we stored earlier
                        String urlKey = (String) chip.getTag();
                        selectedKeys.add(urlKey);
                    }
                }
            }

            this.dismiss();
            listener.onUpdate(selectedKeys);
        });

        binding.clearFilter.setOnClickListener(v -> {
            // Loop through every view inside the ChipGroup
            for (int i = 0; i < binding.genreList.getChildCount(); i++) {
                View child = binding.genreList.getChildAt(i);

                // Ensure it's a Chip before trying to uncheck it
                if (child instanceof Chip) {
                    ((Chip) child).setChecked(false);
                }
            }
            // Optional: If you want to trigger an immediate search update
            // updateSearch(new ArrayList<>());
            this.dismiss();
            listener.onUpdate(new ArrayList<>());
        });

        return binding.getRoot();
    }

    public interface UpdateFilterListener {
        void onUpdate(List<String> selectedFilters);
    }
}
