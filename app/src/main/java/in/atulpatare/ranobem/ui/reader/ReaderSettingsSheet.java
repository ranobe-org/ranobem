package in.atulpatare.ranobem.ui.reader;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.slider.Slider;

import java.util.List;

import in.atulpatare.ranobem.R;
import in.atulpatare.ranobem.databinding.SheetReaderSettingsBinding;

/**
 * Bottom sheet with every reader option. Changes are saved and applied immediately.
 */
public class ReaderSettingsSheet extends BottomSheetDialog {
    private final SheetReaderSettingsBinding binding;
    private final ReaderSettings settings;
    private final Callback callback;

    public ReaderSettingsSheet(@NonNull Context context, ReaderSettings settings, Callback callback) {
        super(context);
        this.settings = settings;
        this.callback = callback;
        binding = SheetReaderSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        getBehavior().setSkipCollapsed(true);
        getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        bind();
    }

    private void bind() {
        // reading mode
        int[] modes = {R.id.mode_webtoon, R.id.mode_ltr, R.id.mode_rtl, R.id.mode_vertical};
        binding.modeGroup.check(modes[settings.getReadingMode()]);
        onChecked(binding.modeGroup, modes, mode -> {
            settings.setReadingMode(mode);
            updateSections();
            callback.onLayoutChanged();
        });

        binding.splitTall.setChecked(settings.isSplitTallImages());
        binding.splitTall.setOnCheckedChangeListener((v, checked) -> {
            settings.setSplitTallImages(checked);
            callback.onLayoutChanged();
        });

        // paged scale
        int[] pageScales = {R.id.page_fit_screen, R.id.page_fit_width};
        binding.pageScaleGroup.check(pageScales[settings.getPageScale()]);
        onChecked(binding.pageScaleGroup, pageScales, scale -> {
            settings.setPageScale(scale);
            callback.onLayoutChanged();
        });

        // webtoon scale
        int[] webtoonScales = {R.id.webtoon_smart, R.id.webtoon_fit_width, R.id.webtoon_fit_screen};
        binding.webtoonScaleGroup.check(webtoonScales[settings.getWebtoonScale()]);
        onChecked(binding.webtoonScaleGroup, webtoonScales, scale -> {
            settings.setWebtoonScale(scale);
            callback.onLayoutChanged();
        });

        binding.paddingSlider.setValue(settings.getWebtoonPadding());
        binding.paddingValue.setText(getContext().getString(R.string.reader_percent, settings.getWebtoonPadding()));
        binding.paddingSlider.addOnChangeListener((slider, value, fromUser) ->
                binding.paddingValue.setText(getContext().getString(R.string.reader_percent, (int) value)));
        binding.paddingSlider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(@NonNull Slider slider) {
            }

            @Override
            public void onStopTrackingTouch(@NonNull Slider slider) {
                settings.setWebtoonPadding((int) slider.getValue());
                callback.onLayoutChanged();
            }
        });

        binding.pageGap.setChecked(settings.isPageGap());
        binding.pageGap.setOnCheckedChangeListener((v, checked) -> {
            settings.setPageGap(checked);
            callback.onLayoutChanged();
        });

        // background
        int[] backgrounds = {R.id.bg_black, R.id.bg_gray, R.id.bg_white};
        binding.backgroundGroup.check(backgrounds[settings.getBackground()]);
        onChecked(binding.backgroundGroup, backgrounds, background -> {
            settings.setBackground(background);
            callback.onLayoutChanged();
        });

        // orientation
        int[] orientations = {R.id.orientation_free, R.id.orientation_portrait, R.id.orientation_landscape};
        binding.orientationGroup.check(orientations[settings.getOrientation()]);
        onChecked(binding.orientationGroup, orientations, orientation -> {
            settings.setOrientation(orientation);
            callback.onDisplayChanged();
        });

        // display
        binding.fullscreen.setChecked(settings.isFullscreen());
        binding.fullscreen.setOnCheckedChangeListener((v, checked) -> {
            settings.setFullscreen(checked);
            callback.onDisplayChanged();
        });

        binding.keepScreenOn.setChecked(settings.isKeepScreenOn());
        binding.keepScreenOn.setOnCheckedChangeListener((v, checked) -> {
            settings.setKeepScreenOn(checked);
            callback.onDisplayChanged();
        });

        binding.showPageNumber.setChecked(settings.isShowPageNumber());
        binding.showPageNumber.setOnCheckedChangeListener((v, checked) -> {
            settings.setShowPageNumber(checked);
            callback.onDisplayChanged();
        });

        binding.customBrightness.setChecked(settings.isCustomBrightness());
        binding.brightnessSlider.setValue(Math.max(0.01f, Math.min(1f, settings.getBrightness())));
        binding.customBrightness.setOnCheckedChangeListener((v, checked) -> {
            settings.setCustomBrightness(checked);
            updateSections();
            callback.onDisplayChanged();
        });
        binding.brightnessSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (!fromUser) return;
            settings.setBrightness(value);
            callback.onDisplayChanged();
        });

        // controls
        binding.tapZones.setChecked(settings.isTapZones());
        binding.tapZones.setOnCheckedChangeListener((v, checked) -> {
            settings.setTapZones(checked);
            callback.onDisplayChanged();
        });

        binding.volumeKeys.setChecked(settings.isVolumeKeys());
        binding.volumeKeys.setOnCheckedChangeListener((v, checked) -> {
            settings.setVolumeKeys(checked);
            callback.onDisplayChanged();
        });

        binding.modeSuggestions.setChecked(settings.isModeSuggestions());
        binding.modeSuggestions.setOnCheckedChangeListener((v, checked) -> settings.setModeSuggestions(checked));

        updateSections();
    }

    private void updateSections() {
        boolean paged = settings.isPaged();
        binding.pagedSection.setVisibility(paged ? View.VISIBLE : View.GONE);
        binding.webtoonSection.setVisibility(paged ? View.GONE : View.VISIBLE);
        binding.brightnessRow.setVisibility(settings.isCustomBrightness() ? View.VISIBLE : View.GONE);

        int[] descriptions = {R.string.reader_mode_webtoon_desc, R.string.reader_mode_ltr_desc,
                R.string.reader_mode_rtl_desc, R.string.reader_mode_vertical_desc};
        binding.modeDesc.setText(descriptions[settings.getReadingMode()]);
    }

    /**
     * Maps a single selection chip group to the index of the checked chip in {@code ids}.
     */
    private static void onChecked(ChipGroup group, int[] ids, OnIndexSelected listener) {
        group.setOnCheckedStateChangeListener((chipGroup, checkedIds) -> {
            int index = indexOf(ids, checkedIds);
            if (index >= 0) listener.onSelected(index);
        });
    }

    private static int indexOf(int[] ids, List<Integer> checkedIds) {
        if (checkedIds.isEmpty()) return -1;
        int checked = checkedIds.get(0);
        for (int i = 0; i < ids.length; i++) {
            if (ids[i] == checked) return i;
        }
        return -1;
    }

    private interface OnIndexSelected {
        void onSelected(int index);
    }

    public interface Callback {
        /**
         * Reading mode, scale, spacing or background changed, pages need to be laid out again.
         */
        void onLayoutChanged();

        /**
         * Window level options changed (orientation, fullscreen, brightness, controls...).
         */
        void onDisplayChanged();
    }
}
