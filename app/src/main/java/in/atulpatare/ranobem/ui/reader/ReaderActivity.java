package in.atulpatare.ranobem.ui.reader;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.SystemBarStyle;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import in.atulpatare.core.models.Chapter;
import in.atulpatare.core.models.Manga;
import in.atulpatare.ranobem.R;
import in.atulpatare.ranobem.config.Config;
import in.atulpatare.ranobem.database.AppDatabase;
import in.atulpatare.ranobem.databinding.ActivityReaderBinding;
import in.atulpatare.ranobem.model.ChapterList;
import in.atulpatare.ranobem.model.History;
import in.atulpatare.ranobem.ui.chapters.ChaptersViewModel;
import in.atulpatare.ranobem.ui.details.DetailsActivity;
import in.atulpatare.ranobem.utils.NumberUtils;
import in.atulpatare.ranobem.utils.VrfFetcher;

public class ReaderActivity extends AppCompatActivity implements ReaderSettingsSheet.Callback {
    private static final long MENU_ANIMATION_MS = 200;
    // reading mode suggestion: how many measured pages are needed before judging a chapter
    private static final int SUGGESTION_SAMPLE = 6;
    // taller than this (height / width) is a strip, between the two bounds a typical manga page
    private static final float SUGGESTION_STRIP_RATIO = 2.4f;
    private static final float SUGGESTION_PAGE_MIN = 1.3f;
    private static final float SUGGESTION_PAGE_MAX = 1.65f;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final PagerSnapHelper snapHelper = new PagerSnapHelper();

    ActivityReaderBinding binding;
    Chapter currentChapter;
    ChapterList list;
    Manga manga;
    ChaptersViewModel viewModel;

    private ReaderSettings settings;
    private PageAdapter adapter;
    private PageLoader pageLoader;
    private LinearLayoutManager layoutManager;
    private GestureDetector gestureDetector;
    private WindowInsetsControllerCompat insetsController;
    private String from;

    private int appliedMode = -1;
    private int currentPage = -1;
    private boolean onTransitionPage;
    private boolean menuVisible;
    private boolean loadingChapter;
    private boolean chaptersFailed;
    // whether the current gesture started on a control, decided on touch down because the
    // control may be gone by the time a single tap is confirmed
    private boolean touchOnControl;
    // the current chapter was already judged for a reading mode suggestion
    private boolean modeSuggestionChecked;
    private Snackbar suggestion;
    // incremented for every chapter request, stale responses are ignored
    private int loadToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this, SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT));
        super.onCreate(savedInstanceState);
        binding = ActivityReaderBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        currentChapter = savedInstanceState != null
                ? savedInstanceState.getParcelable(Config.KEY_CHAPTER)
                : getIntent().getParcelableExtra(Config.KEY_CHAPTER);
        manga = getIntent().getParcelableExtra(Config.KEY_MANGA);
        from = getIntent().getStringExtra(Config.KEY_PAGE);

        if (currentChapter == null || manga == null) {
            finish();
            return;
        }

        settings = new ReaderSettings(this);
        insetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        insetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        insetsController.setAppearanceLightStatusBars(false);
        insetsController.setAppearanceLightNavigationBars(false);

        setupToolbar();
        setupInsets();
        setupList();
        setupControls();
        setupGestures();

        viewModel = new ViewModelProvider(this).get(ChaptersViewModel.class);
        viewModel.getError().observe(this, this::setUpError);

        applyLayoutSettings();
        applyDisplaySettings();
        setMenuVisible(false, false);

        loadChapter(currentChapter, R.string.reader_loading_chapter);
        loadAllChapters();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (currentChapter != null) outState.putParcelable(Config.KEY_CHAPTER, currentChapter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveProgress();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseLoader();
    }

    private void releaseLoader() {
        if (pageLoader != null) {
            pageLoader.release();
            pageLoader = null;
        }
    }

    // region setup

    private void setupToolbar() {
        binding.appbar.setTitle(manga.name);
        binding.appbar.setNavigationOnClickListener(v -> close());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                close();
            }
        });
    }

    private void close() {
        // if previous page is history, open the manga details instead of going back to history
        if (Config.PAGE_HISTORY.equals(from)) {
            startActivity(new Intent(this, DetailsActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(Config.KEY_MANGA, manga));
        }
        finish();
    }

    private void setupInsets() {
        int gutter = dp(8);
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            int types = WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout();
            // stable insets so the menus don't jump when system bars are shown or hidden
            Insets stable = insets.getInsetsIgnoringVisibility(types);
            Insets current = insets.getInsets(types);
            Insets cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout());

            binding.topBar.setPadding(stable.left, stable.top, stable.right, 0);
            binding.bottomBar.setPadding(stable.left + gutter, gutter, stable.right + gutter, stable.bottom + gutter / 2);

            ViewGroup.MarginLayoutParams indicator = (ViewGroup.MarginLayoutParams) binding.pageIndicator.getLayoutParams();
            indicator.bottomMargin = current.bottom + gutter;
            binding.pageIndicator.setLayoutParams(indicator);

            // fullscreen pages use the whole display, otherwise keep them clear of the system bars
            if (settings.isFullscreen()) {
                binding.list.setPadding(cutout.left, 0, cutout.right, 0);
            } else {
                binding.list.setPadding(stable.left, stable.top, stable.right, stable.bottom);
            }
            return insets;
        });
    }

    private void setupList() {
        binding.list.setHasFixedSize(true);
        binding.list.setItemAnimator(null);
        binding.list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                updateCurrentPage();
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    updateCurrentPage();
                    saveProgress();
                }
            }
        });
        // re-layout pages for the new size on rotation, split screen or inset changes
        binding.list.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (adapter == null) return;
            int page = currentPage;
            if (adapter.setViewport(viewportWidth(), viewportHeight()) && page >= 0) {
                v.post(() -> jumpToPage(page));
            }
        });
    }

    private void setupControls() {
        binding.nextChapter.setOnClickListener(v -> loadNextChapter());
        binding.prevChapter.setOnClickListener(v -> loadPreviousChapter());
        binding.errorRetry.setOnClickListener(v -> {
            loadChapter(currentChapter, R.string.reader_loading_chapter);
            if (list == null) loadAllChapters();
        });

        binding.pageSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (!fromUser || adapter == null) return;
            jumpToPage((int) value);
        });

        binding.actionMode.setOnClickListener(this::showModeMenu);
        binding.actionScale.setOnClickListener(this::showScaleMenu);
        binding.actionOrientation.setOnClickListener(v -> {
            settings.setOrientation((settings.getOrientation() + 1) % 3);
            applyDisplaySettings();
        });
        binding.actionSettings.setOnClickListener(v -> new ReaderSettingsSheet(this, settings, this).show());
    }

    private void setupGestures() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                handleTap(e.getRawX(), e.getRawY());
                return true;
            }
        });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        // observe every touch so taps work on top of zoomable pages without stealing their gestures
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            touchOnControl = isOnControl(ev.getRawX(), ev.getRawY());
        }
        if (gestureDetector != null) gestureDetector.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    // endregion

    // region settings

    @Override
    public void onLayoutChanged() {
        applyLayoutSettings();
    }

    @Override
    public void onDisplayChanged() {
        applyDisplaySettings();
    }

    @SuppressLint("NotifyDataSetChanged")
    private void applyLayoutSettings() {
        int page = currentPage;
        int mode = settings.getReadingMode();

        if (mode != appliedMode) {
            appliedMode = mode;
            boolean horizontal = mode == ReaderSettings.MODE_LTR || mode == ReaderSettings.MODE_RTL;
            layoutManager = new LinearLayoutManager(this,
                    horizontal ? RecyclerView.HORIZONTAL : RecyclerView.VERTICAL,
                    mode == ReaderSettings.MODE_RTL);
            binding.list.setLayoutManager(layoutManager);
            snapHelper.attachToRecyclerView(settings.isPaged() ? binding.list : null);
            // slider and chapter buttons follow the reading direction
            binding.seekRow.setLayoutDirection(mode == ReaderSettings.MODE_RTL
                    ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        }

        int background;
        int foreground;
        switch (settings.getBackground()) {
            case ReaderSettings.BG_GRAY:
                background = R.color.reader_bg_gray;
                foreground = R.color.reader_on_overlay_variant;
                break;
            case ReaderSettings.BG_WHITE:
                background = R.color.reader_bg_white;
                foreground = R.color.reader_on_light;
                break;
            default:
                background = R.color.reader_bg_black;
                foreground = R.color.reader_on_overlay_variant;
        }
        int foregroundColor = ContextCompat.getColor(this, foreground);
        binding.main.setBackgroundColor(ContextCompat.getColor(this, background));
        binding.loadingText.setTextColor(foregroundColor);
        binding.errorTitle.setTextColor(foregroundColor);
        binding.errorText.setTextColor(foregroundColor);

        if (adapter != null) {
            adapter.setForegroundColor(foregroundColor);
            // splitting may have been toggled, rebuild the items before restoring the page
            adapter.rebuildItems();
            if (page >= 0) jumpToPage(page);
        }
        updateActionLabels();
    }

    private void applyDisplaySettings() {
        if (settings.isKeepScreenOn()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.screenBrightness = settings.isCustomBrightness()
                ? Math.max(0.01f, settings.getBrightness())
                : WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        getWindow().setAttributes(params);

        switch (settings.getOrientation()) {
            case ReaderSettings.ORIENTATION_PORTRAIT:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
                break;
            case ReaderSettings.ORIENTATION_LANDSCAPE:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                break;
            default:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }

        updateSystemBars();
        ViewCompat.requestApplyInsets(binding.main);
        updatePageIndicator();
        updateActionLabels();
    }

    private void updateActionLabels() {
        int[] modes = {R.string.reader_mode_webtoon, R.string.reader_mode_ltr,
                R.string.reader_mode_rtl, R.string.reader_mode_vertical};
        binding.actionMode.setText(modes[settings.getReadingMode()]);

        if (settings.isPaged()) {
            binding.actionScale.setText(settings.getPageScale() == ReaderSettings.PAGE_FIT_WIDTH
                    ? R.string.reader_scale_fit_width : R.string.reader_scale_fit_screen);
        } else {
            int[] scales = {R.string.reader_scale_smart, R.string.reader_scale_fit_width, R.string.reader_scale_fit_screen};
            binding.actionScale.setText(scales[settings.getWebtoonScale()]);
        }

        int[] orientations = {R.string.reader_orientation_free, R.string.reader_orientation_portrait,
                R.string.reader_orientation_landscape};
        binding.actionOrientation.setText(orientations[settings.getOrientation()]);
    }

    private void showModeMenu(View anchor) {
        int[] modes = {R.string.reader_mode_webtoon, R.string.reader_mode_ltr,
                R.string.reader_mode_rtl, R.string.reader_mode_vertical};
        showChoiceMenu(anchor, modes, settings.getReadingMode(), index -> {
            settings.setReadingMode(index);
            applyLayoutSettings();
        });
    }

    private void showScaleMenu(View anchor) {
        if (settings.isPaged()) {
            int[] scales = {R.string.reader_scale_fit_screen, R.string.reader_scale_fit_width};
            showChoiceMenu(anchor, scales, settings.getPageScale(), index -> {
                settings.setPageScale(index);
                applyLayoutSettings();
            });
        } else {
            int[] scales = {R.string.reader_scale_smart, R.string.reader_scale_fit_width, R.string.reader_scale_fit_screen};
            showChoiceMenu(anchor, scales, settings.getWebtoonScale(), index -> {
                settings.setWebtoonScale(index);
                applyLayoutSettings();
            });
        }
    }

    private void showChoiceMenu(View anchor, int[] labels, int selected, ChoiceListener listener) {
        PopupMenu popup = new PopupMenu(this, anchor);
        Menu menu = popup.getMenu();
        for (int i = 0; i < labels.length; i++) {
            menu.add(1, i, i, labels[i]).setChecked(i == selected);
        }
        menu.setGroupCheckable(1, true, true);
        popup.setOnMenuItemClickListener(item -> {
            listener.onChoice(item.getItemId());
            return true;
        });
        popup.show();
    }

    // endregion

    // region menu & navigation

    private void setMenuVisible(boolean visible, boolean animate) {
        menuVisible = visible;
        View top = binding.topBar;
        View bottom = binding.bottomBar;
        long duration = animate ? MENU_ANIMATION_MS : 0;

        if (visible) {
            if (top.getVisibility() != View.VISIBLE) {
                top.setTranslationY(-top.getHeight());
                bottom.setTranslationY(bottom.getHeight());
            }
            top.setVisibility(View.VISIBLE);
            bottom.setVisibility(View.VISIBLE);
            top.animate().translationY(0).alpha(1f).setDuration(duration).start();
            bottom.animate().translationY(0).alpha(1f).setDuration(duration).start();
        } else {
            top.animate().translationY(-top.getHeight()).alpha(0f).setDuration(duration)
                    .withEndAction(() -> top.setVisibility(View.INVISIBLE)).start();
            bottom.animate().translationY(bottom.getHeight()).alpha(0f).setDuration(duration)
                    .withEndAction(() -> bottom.setVisibility(View.INVISIBLE)).start();
        }
        updateSystemBars();
        updatePageIndicator();
    }

    private void updateSystemBars() {
        if (settings.isFullscreen() && !menuVisible) {
            insetsController.hide(WindowInsetsCompat.Type.systemBars());
        } else {
            insetsController.show(WindowInsetsCompat.Type.systemBars());
        }
    }

    private boolean isOnControl(float x, float y) {
        if (menuVisible && (isInside(binding.topBar, x, y) || isInside(binding.bottomBar, x, y))) return true;
        if (suggestion != null && suggestion.isShown() && isInside(suggestion.getView(), x, y)) return true;
        return isOnButton(binding.list, x, y) || isOnButton(binding.errorView, x, y);
    }

    private void handleTap(float x, float y) {
        if (touchOnControl) return;

        if (menuVisible) {
            setMenuVisible(false, true);
            return;
        }
        if (!settings.isTapZones() || adapter == null || loadingChapter) {
            setMenuVisible(true, true);
            return;
        }

        int[] location = new int[2];
        binding.main.getLocationOnScreen(location);
        float relX = (x - location[0]) / binding.main.getWidth();
        float relY = (y - location[1]) / binding.main.getHeight();
        int mode = settings.getReadingMode();

        if (mode == ReaderSettings.MODE_LTR || mode == ReaderSettings.MODE_RTL) {
            boolean rtl = mode == ReaderSettings.MODE_RTL;
            if (relX < 1 / 3f) {
                if (rtl) nextPage(); else previousPage();
            } else if (relX > 2 / 3f) {
                if (rtl) previousPage(); else nextPage();
            } else {
                setMenuVisible(true, true);
            }
        } else {
            if (relY < 1 / 3f) previousPage();
            else if (relY > 2 / 3f) nextPage();
            else setMenuVisible(true, true);
        }
    }

    private void nextPage() {
        scrollPages(1);
    }

    private void previousPage() {
        scrollPages(-1);
    }

    private void scrollPages(int direction) {
        if (adapter == null || layoutManager == null) return;
        if (settings.isPaged()) {
            int position = findCurrentPosition();
            if (position == RecyclerView.NO_POSITION) return;
            int target = Math.max(0, Math.min(adapter.getItemCount() - 1, position + direction));
            binding.list.smoothScrollToPosition(target);
        } else {
            int distance = Math.round(viewportHeight() * 0.8f);
            binding.list.smoothScrollBy(0, direction * distance);
        }
    }

    private void jumpToPage(int page) {
        if (adapter == null || layoutManager == null || adapter.getPageCount() == 0) return;
        int target = Math.max(0, Math.min(adapter.getPageCount() - 1, page));
        binding.list.stopScroll();
        layoutManager.scrollToPositionWithOffset(adapter.positionOfPage(target), 0);
        setCurrentPage(target, false);
    }

    private int findCurrentPosition() {
        if (layoutManager == null) return RecyclerView.NO_POSITION;
        if (settings.isPaged()) {
            View snap = snapHelper.findSnapView(layoutManager);
            return snap == null ? RecyclerView.NO_POSITION : layoutManager.getPosition(snap);
        }
        if (!binding.list.canScrollVertically(1)) return layoutManager.findLastVisibleItemPosition();
        if (!binding.list.canScrollVertically(-1)) return layoutManager.findFirstVisibleItemPosition();
        View center = binding.list.findChildViewUnder(binding.list.getWidth() / 2f, binding.list.getHeight() / 2f);
        return center != null ? binding.list.getChildAdapterPosition(center) : layoutManager.findFirstVisibleItemPosition();
    }

    private void updateCurrentPage() {
        if (adapter == null || adapter.getPageCount() == 0) return;
        int position = findCurrentPosition();
        if (position == RecyclerView.NO_POSITION) return;
        setCurrentPage(adapter.pageAt(position), adapter.isTransition(position));
    }

    private void setCurrentPage(int page, boolean transition) {
        boolean changed = page != currentPage || transition != onTransitionPage;
        currentPage = page;
        onTransitionPage = transition;
        if (!changed) return;

        int total = adapter.getPageCount();
        binding.pageCurrent.setText(String.valueOf(page + 1));
        binding.pageTotal.setText(String.valueOf(total));
        if (total > 1 && !binding.pageSlider.isPressed()) {
            binding.pageSlider.setValue(page);
        }
        updatePageIndicator();
        if (pageLoader != null) pageLoader.setFocus(page);
    }

    private void updatePageIndicator() {
        boolean show = settings.isShowPageNumber() && !menuVisible && adapter != null
                && adapter.getPageCount() > 0 && !onTransitionPage && currentPage >= 0;
        binding.pageIndicator.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            binding.pageIndicator.setText(getString(R.string.reader_page_of, currentPage + 1, adapter.getPageCount()));
        }
    }

    private void setupSlider(int total) {
        binding.pageSlider.setValue(0);
        binding.pageSlider.setValueFrom(0);
        binding.pageSlider.setValueTo(Math.max(1, total - 1));
        binding.pageSlider.setEnabled(total > 1);
        binding.pageTotal.setText(String.valueOf(total));
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (settings != null && settings.isVolumeKeys() && adapter != null) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                nextPage();
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                previousPage();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (settings != null && settings.isVolumeKeys() && adapter != null
                && (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)) {
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    // endregion

    // region chapters

    private void setUpError(String error) {
        if (loadingChapter) {
            loadingChapter = false;
            binding.loadingView.setVisibility(View.GONE);
            binding.errorView.setVisibility(View.VISIBLE);
            binding.errorText.setText(error);
            setMenuVisible(true, true);
            return;
        }
        if (list == null) {
            // chapter list request failed, stop waiting for the next chapter
            chaptersFailed = true;
            updateTransition();
        }
        if (error != null) Snackbar.make(binding.getRoot(), error, Snackbar.LENGTH_LONG).show();
    }

    private void loadAllChapters() {
        chaptersFailed = false;
        if (manga.sourceId == 1) {
            String url = "https://mangafire.to" + manga.url.replace("/manga", "/read");
            AtomicBoolean handled = new AtomicBoolean(false);
            VrfFetcher.fetchVrf(this, url, "/ajax/read/" + manga.id, vrf -> {
                if (!handled.compareAndSet(false, true)) return;
                Manga request = copy(manga, Manga.CREATOR);
                request.url = vrf.replace("https://mangafire.to", "");
                mainHandler.post(() -> {
                    if (isFinishing()) return;
                    viewModel.getChapters(request).observe(this, this::setAllChapters);
                });
            });
        } else {
            viewModel.getChapters(manga).observe(this, this::setAllChapters);
        }
    }

    private void setAllChapters(List<Chapter> chapters) {
        list = new ChapterList(chapters);
        updateChapterButtons();
        updateTransition();
    }

    private void loadChapter(Chapter chapter, @StringRes int message) {
        int token = ++loadToken;
        saveProgress();

        currentChapter = chapter;
        loadingChapter = true;
        adapter = null;
        releaseLoader();
        currentPage = -1;
        onTransitionPage = false;
        modeSuggestionChecked = false;
        binding.list.setAdapter(null);
        binding.errorView.setVisibility(View.GONE);
        binding.loadingView.setVisibility(View.VISIBLE);
        binding.loadingText.setText(message);
        binding.appbar.setSubtitle(chapterTitle(chapter));
        binding.pageCurrent.setText("-");
        setupSlider(0);
        updateChapterButtons();
        updatePageIndicator();

        // work on a copy, the source may rewrite the url while fetching
        Chapter request = copy(chapter, Chapter.CREATOR);
        if (request.sourceId == 1) {
            AtomicBoolean handled = new AtomicBoolean(false);
            VrfFetcher.fetchVrf(getApplicationContext(), "https://mangafire.to" + request.url, "/ajax/read/chapter", vrf -> {
                if (!handled.compareAndSet(false, true)) return;
                // https://mangafire.to/ajax/read/kw9j9/chapter/en?vrf=ZBYeRCjYBk0tkZnKW4kTuWBYw641e-csvu6vl7UY4zcaviixmK7VJ-tjpFEsOUq42nE5ZBdEYGJfpA
                request.url = vrf.replace("https://mangafire.to", "");
                mainHandler.post(() -> {
                    if (token == loadToken && !isFinishing()) observeChapter(request, token);
                });
            });
        } else {
            observeChapter(request, token);
        }
    }

    private void observeChapter(Chapter request, int token) {
        viewModel.getChapter(request).observe(this, result -> {
            if (token == loadToken) setUI(result);
        });
    }

    private void setUI(Chapter chapter) {
        loadingChapter = false;
        binding.loadingView.setVisibility(View.GONE);

        if (chapter == null || chapter.pages == null || chapter.pages.isEmpty()) {
            binding.errorView.setVisibility(View.VISIBLE);
            binding.errorText.setText(R.string.reader_no_chapter);
            setMenuVisible(true, true);
            return;
        }

        saveChapterToHistory(currentChapter);

        int foregroundColor = settings.getBackground() == ReaderSettings.BG_WHITE
                ? ContextCompat.getColor(this, R.color.reader_on_light)
                : ContextCompat.getColor(this, R.color.reader_on_overlay_variant);
        releaseLoader();
        pageLoader = new PageLoader(this, chapter.pages, new PageLoader.Listener() {
            @Override
            public void onPageStateChanged(int index) {
                if (adapter == null) return;
                adapter.onPageStateChanged(index);
                maybeSuggestReadingMode();
                // a split page adds items, keep the counter in sync once the list settles
                binding.list.post(() -> updateCurrentPage());
            }

            @Override
            public void onPageProgress(int index, int percent) {
                if (adapter != null) adapter.onPageProgress(index);
            }
        });
        adapter = new PageAdapter(pageLoader, settings, foregroundColor, this::loadNextChapter);
        adapter.setViewport(viewportWidth(), viewportHeight());
        binding.list.setAdapter(adapter);
        updateTransition();
        setupSlider(chapter.pages.size());

        int saved = settings.getProgress(manga.id, currentChapter.id);
        currentPage = -1;
        jumpToPage(saved < chapter.pages.size() ? saved : 0);
    }

    /**
     * Once enough pages are measured, suggests webtoon mode for long strips read in a paged
     * mode, or a paged mode for regular manga pages read as a webtoon. Shown rarely, see
     * {@link ReaderSettings#canSuggestMode(String)}.
     */
    private void maybeSuggestReadingMode() {
        if (modeSuggestionChecked || pageLoader == null || !settings.isModeSuggestions()) return;

        int measured = 0, strips = 0, regular = 0;
        for (int i = 0; i < pageLoader.getPageCount(); i++) {
            PageLoader.Page page = pageLoader.getPage(i);
            if (page.state != PageLoader.STATE_READY) continue;
            measured++;
            float ratio = page.ratio();
            if (ratio >= SUGGESTION_STRIP_RATIO) strips++;
            else if (ratio >= SUGGESTION_PAGE_MIN && ratio <= SUGGESTION_PAGE_MAX) regular++;
        }
        if (measured < Math.min(SUGGESTION_SAMPLE, pageLoader.getPageCount())) return;
        modeSuggestionChecked = true;

        int suggested;
        int message;
        if (settings.isPaged() && strips >= measured * 0.75f) {
            suggested = ReaderSettings.MODE_WEBTOON;
            message = R.string.reader_suggest_webtoon;
        } else if (!settings.isPaged() && regular >= measured * 0.8f) {
            // webtoon chunks can look like pages, so this direction needs a clear majority
            suggested = settings.getLastPagedMode();
            message = R.string.reader_suggest_paged;
        } else {
            return;
        }
        if (!settings.canSuggestMode(manga.id)) return;
        settings.onModeSuggested(manga.id);

        suggestion = Snackbar.make(binding.main, message, Snackbar.LENGTH_LONG)
                .setDuration(7000)
                .setAction(R.string.reader_suggest_switch, v -> {
                    settings.onModeSuggestionAccepted();
                    settings.setReadingMode(suggested);
                    applyLayoutSettings();
                })
                .addCallback(new Snackbar.Callback() {
                    @Override
                    public void onDismissed(Snackbar bar, int event) {
                        if (event == DISMISS_EVENT_TIMEOUT || event == DISMISS_EVENT_SWIPE) {
                            settings.onModeSuggestionIgnored();
                        }
                        if (suggestion == bar) suggestion = null;
                    }
                });
        if (menuVisible) suggestion.setAnchorView(binding.bottomBar);
        suggestion.show();
    }

    private void updateTransition() {
        if (adapter == null) return;
        Chapter next = getNextChapter();
        adapter.setTransition(chapterTitle(currentChapter),
                next != null ? chapterTitle(next) : null,
                list == null && !chaptersFailed);
    }

    private void updateChapterButtons() {
        binding.nextChapter.setEnabled(getNextChapter() != null);
        binding.prevChapter.setEnabled(getPreviousChapter() != null);
    }

    private int currentIndex() {
        if (list == null || list.chapters == null) return -1;
        for (int i = 0; i < list.chapters.size(); i++) {
            if (list.chapters.get(i).id == currentChapter.id) return i;
        }
        return -1;
    }

    private Chapter getNextChapter() {
        int index = currentIndex();
        if (index > -1 && index + 1 < list.chapters.size()) {
            return list.chapters.get(index + 1);
        }
        return null;
    }

    private Chapter getPreviousChapter() {
        int index = currentIndex();
        return index > 0 ? list.chapters.get(index - 1) : null;
    }

    private void loadNextChapter() {
        Chapter next = getNextChapter();
        if (next != null) loadChapter(next, R.string.reader_loading_next);
    }

    private void loadPreviousChapter() {
        Chapter previous = getPreviousChapter();
        if (previous != null) loadChapter(previous, R.string.reader_loading_previous);
    }

    private String chapterTitle(Chapter chapter) {
        String index = NumberUtils.normalize(chapter.index);
        return chapter.name == null || chapter.name.trim().isEmpty()
                ? getString(R.string.reader_chapter_title, index)
                : getString(R.string.reader_chapter_title_named, index, chapter.name.trim());
    }

    private void saveProgress() {
        if (adapter != null && currentChapter != null && currentPage >= 0) {
            settings.setProgress(manga.id, currentChapter.id, currentPage);
        }
    }

    private void saveChapterToHistory(Chapter item) {
        History history = new History(manga, item);
        AppDatabase.databaseExecutor.execute(() -> AppDatabase.getDatabase().historyDao().insert(history));
    }

    // endregion

    // region helpers

    private int viewportWidth() {
        int width = binding.list.getWidth() - binding.list.getPaddingLeft() - binding.list.getPaddingRight();
        return width > 0 ? width : getResources().getDisplayMetrics().widthPixels;
    }

    private int viewportHeight() {
        int height = binding.list.getHeight() - binding.list.getPaddingTop() - binding.list.getPaddingBottom();
        return height > 0 ? height : getResources().getDisplayMetrics().heightPixels;
    }

    private static boolean isInside(View view, float x, float y) {
        if (view.getVisibility() != View.VISIBLE) return false;
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        return x >= location[0] && x < location[0] + view.getWidth()
                && y >= location[1] && y < location[1] + view.getHeight();
    }

    /**
     * @return true if a visible button inside {@code root} is under the given screen point
     */
    private static boolean isOnButton(View root, float x, float y) {
        if (!isInside(root, x, y)) return false;
        if (root instanceof Button) return root.isEnabled();
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (isOnButton(group.getChildAt(i), x, y)) return true;
            }
        }
        return false;
    }

    private static <T extends Parcelable> T copy(T source, Parcelable.Creator<T> creator) {
        Parcel parcel = Parcel.obtain();
        try {
            source.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            return creator.createFromParcel(parcel);
        } finally {
            parcel.recycle();
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private interface ChoiceListener {
        void onChoice(int index);
    }

    // endregion
}
