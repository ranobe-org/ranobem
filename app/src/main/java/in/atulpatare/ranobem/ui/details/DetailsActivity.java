package in.atulpatare.ranobem.ui.details;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.transition.Fade;
import android.transition.TransitionManager;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.google.android.material.chip.Chip;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import in.atulpatare.core.models.Manga;
import in.atulpatare.core.models.Tag;
import in.atulpatare.core.sources.SourceManager;
import in.atulpatare.ranobem.R;
import in.atulpatare.ranobem.config.Config;
import in.atulpatare.ranobem.database.AppDatabase;
import in.atulpatare.ranobem.databinding.ActivityDetailsBinding;
import in.atulpatare.ranobem.download.DownloadJob;
import in.atulpatare.ranobem.download.DownloadQueue;
import in.atulpatare.ranobem.download.DownloadText;
import in.atulpatare.ranobem.ui.HomeActivity;
import in.atulpatare.ranobem.ui.browse.adapter.MangaAdapter;
import in.atulpatare.ranobem.ui.chapters.ChapterFragment;
import in.atulpatare.ranobem.ui.downloads.DownloadsActivity;
import in.atulpatare.ranobem.ui.downloads.EpubDownloadPrompt;

public class DetailsActivity extends AppCompatActivity implements MangaAdapter.OnMangaItemClickListener {
    private static final int SUMMARY_LINES = 4;

    private final List<Manga> authorWorks = new ArrayList<>();
    private final List<Manga> related = new ArrayList<>();
    private final List<Manga> recommendations = new ArrayList<>();
    private ActivityDetailsBinding binding;
    private DetailsViewModel viewModel;
    private Manga manga;
    private boolean inLibrary = false;
    private boolean summaryExpanded = false;
    private String loadedCover = null;
    private EpubDownloadPrompt downloadPrompt;
    private DownloadJob downloadJob;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityDetailsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        manga = getIntent().getParcelableExtra(Config.KEY_MANGA);
        assert manga != null;

        setUpInsets();
        setUpTopBar();
        setUpCarousel(binding.authorWorks, authorWorks);
        setUpCarousel(binding.related, related);
        setUpCarousel(binding.recommendations, recommendations);

        // the list already gave us the cover and name, show them while the rest loads
        showBasics(manga);

        viewModel = new ViewModelProvider(this).get(DetailsViewModel.class);
        viewModel.getDetails(manga).observe(this, this::setUpUi);
        viewModel.getError().observe(this, this::showError);
        viewModel.getAuthorWorks().observe(this, this::showAuthorWorks);

        binding.back.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        binding.read.setOnClickListener(v -> navigateToChapterList());
        binding.chaptersCard.setOnClickListener(v -> navigateToChapterList());
        binding.libraryToggle.setOnClickListener(v -> toggleLibrary());
        binding.openInBrowser.setOnClickListener(v -> openInBrowser(manga));
        binding.summary.setOnClickListener(v -> toggleSummary());
        binding.summaryToggle.setOnClickListener(v -> toggleSummary());

        downloadPrompt = new EpubDownloadPrompt(this, binding.getRoot());
        binding.downloadCard.setOnClickListener(v -> onDownloadClick());
        binding.downloadProBadge.setVisibility(Config.isFree() ? View.VISIBLE : View.GONE);
        if (!Config.isFree()) DownloadQueue.get(this).jobs().observe(this, jobs -> showDownload());

        checkIfMangaInLibrary(manga.id);
        checkIfMangaInHistory(manga.id);
    }

    private void setUpInsets() {
        int heroTop = binding.heroContent.getPaddingTop();
        int heroBottom = binding.heroContent.getPaddingBottom();
        int heroSide = binding.heroContent.getPaddingStart();
        int contentBottom = binding.content.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            binding.topBar.setPadding(bars.left, bars.top, bars.right, 0);
            binding.heroContent.setPadding(heroSide + bars.left, heroTop + bars.top, heroSide + bars.right, heroBottom);
            binding.content.setPadding(0, 0, 0, contentBottom + bars.bottom);
            return insets;
        });
    }

    // the bar sits transparent over the hero and fades to solid with the title as the hero scrolls away
    private void setUpTopBar() {
        int surface = MaterialColors.getColor(binding.topBar, com.google.android.material.R.attr.colorSurface);
        binding.scroll.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (v, x, y, oldX, oldY) -> {
            int range = Math.max(1, binding.hero.getHeight() - binding.topBar.getHeight());
            float fraction = Math.min(1f, Math.max(0f, (float) y / range));
            binding.topBar.setBackgroundColor(ColorUtils.setAlphaComponent(surface, (int) (fraction * 255)));
            binding.topBarTitle.setAlpha(fraction < 0.8f ? 0f : (fraction - 0.8f) / 0.2f);
        });
    }

    private void setUpCarousel(RecyclerView view, List<Manga> items) {
        view.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        view.setAdapter(new MangaCarouselAdapter(items, this));
    }

    private void showBasics(Manga m) {
        binding.title.setText(m.name);
        binding.topBarTitle.setText(m.name);
        if (m.cover == null || m.cover.equals(loadedCover)) return;
        loadedCover = m.cover;
        Glide.with(this).load(m.cover).transition(DrawableTransitionOptions.withCrossFade()).into(binding.cover);
        // a tiny copy stretched over the hero reads as a soft blur, newer devices blur it for real
        Glide.with(this).load(m.cover).override(48, 72).centerCrop().into(binding.backdrop);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            binding.backdrop.setRenderEffect(RenderEffect.createBlurEffect(40f, 40f, Shader.TileMode.CLAMP));
        }
    }

    private void setUpUi(Manga m) {
        manga = m;
        // sections fade in as their data arrives instead of popping in
        fadeIn();
        binding.progress.hide();
        showBasics(m);
        showBadges(m);
        showAuthors(m);
        showStats(m);
        showLatestChapter(m);
        showSummary(m.summary);
        showGenres(m);
        showCarousel(binding.relatedSection, binding.related, related, m.related);
        showCarousel(binding.recommendationsSection, binding.recommendations, recommendations, m.recommendations);

        Tag author = firstSearchableAuthor(m);
        if (author != null && supportsSearch(m)) {
            binding.authorSectionTitle.setText(getString(R.string.more_from, author.name));
            binding.authorSeeAll.setOnClickListener(v -> openSearch(Config.KEY_AUTHOR, author.key));
            viewModel.loadAuthorWorks(m, author.key);
        }
    }

    private void showBadges(Manga m) {
        boolean hasType = !isBlank(m.type) && !m.type.equalsIgnoreCase("unknown");
        binding.badgeType.setText(hasType ? m.type : null);
        binding.badgeType.setVisibility(hasType ? View.VISIBLE : View.GONE);
        binding.badgeOfficial.setVisibility(m.official ? View.VISIBLE : View.GONE);
        binding.badgeAnime.setVisibility(m.anime ? View.VISIBLE : View.GONE);
        binding.badgeAdult.setVisibility(m.adult ? View.VISIBLE : View.GONE);
    }

    // "by A, B" where every author the source can search by is a link to their other works
    private void showAuthors(Manga m) {
        List<Tag> authors = m.authors;
        if ((authors == null || authors.isEmpty()) && !isBlank(m.author)) {
            authors = new ArrayList<>();
            authors.add(new Tag(m.author, null));
        }
        if (authors == null || authors.isEmpty()) {
            binding.authors.setVisibility(View.GONE);
            return;
        }

        int linkColor = MaterialColors.getColor(binding.authors, androidx.appcompat.R.attr.colorPrimary);
        boolean searchable = supportsSearch(m);
        SpannableStringBuilder text = new SpannableStringBuilder(getString(R.string.by)).append(' ');
        for (int i = 0; i < authors.size(); i++) {
            Tag author = authors.get(i);
            if (i > 0) text.append(", ");
            int start = text.length();
            text.append(author.name);
            if (searchable && author.isSearchable()) {
                text.setSpan(new ClickableSpan() {
                    @Override
                    public void onClick(@NonNull View widget) {
                        openSearch(Config.KEY_AUTHOR, author.key);
                    }

                    @Override
                    public void updateDrawState(@NonNull TextPaint ds) {
                        ds.setColor(linkColor);
                        ds.setFakeBoldText(true);
                        ds.setUnderlineText(false);
                    }
                }, start, text.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        binding.authors.setText(text);
        binding.authors.setMovementMethod(LinkMovementMethod.getInstance());
        binding.authors.setHighlightColor(Color.TRANSPARENT);
        binding.authors.setVisibility(View.VISIBLE);
    }

    private void showStats(Manga m) {
        boolean any = setStat(binding.statStatus, binding.statStatusValue, m.status);
        any |= setStat(binding.statReleased, binding.statReleasedValue, m.released);
        any |= setStat(binding.statFollowers, binding.statFollowersValue, m.subscribers > 0 ? formatCount(m.subscribers) : null);
        any |= setStat(binding.statRating, binding.statRatingValue, m.rating > 0 ? String.valueOf(m.rating) : null);
        binding.statStatusValue.setTextColor(statusColor(m.status));
        binding.stats.setVisibility(any ? View.VISIBLE : View.GONE);
    }

    private boolean setStat(View tile, TextView value, String text) {
        boolean show = !isBlank(text);
        value.setText(text);
        tile.setVisibility(show ? View.VISIBLE : View.GONE);
        return show;
    }

    private int statusColor(String status) {
        String s = status == null ? "" : status.toLowerCase(Locale.ROOT);
        if (s.contains("ongoing") || s.contains("releasing")) return ContextCompat.getColor(this, R.color.status_ongoing);
        if (s.contains("complete") || s.contains("finished")) return ContextCompat.getColor(this, R.color.status_completed);
        if (s.contains("hiatus")) return ContextCompat.getColor(this, R.color.status_hiatus);
        if (s.contains("cancel") || s.contains("discontinued") || s.contains("dropped")) {
            return ContextCompat.getColor(this, R.color.status_cancelled);
        }
        return MaterialColors.getColor(binding.statStatusValue, com.google.android.material.R.attr.colorOnSurface);
    }

    // 950, 1.2K, 50K, 1.3M
    private String formatCount(long n) {
        if (n < 1000) return String.valueOf(n);
        double value = n < 1_000_000 ? n / 1000.0 : n / 1_000_000.0;
        String suffix = n < 1_000_000 ? "K" : "M";
        if (value >= 10 || value == Math.floor(value)) return ((long) value) + suffix;
        return String.format(Locale.getDefault(), "%.1f", value) + suffix;
    }

    private void showLatestChapter(Manga m) {
        if (isBlank(m.latestChapter)) {
            binding.latestChapter.setVisibility(View.GONE);
            return;
        }
        String text = getString(R.string.latest_chapter, m.latestChapter);
        if (m.latestChapterAt > 0) {
            CharSequence ago = DateUtils.getRelativeTimeSpanString(m.latestChapterAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
            text = text + " · " + ago;
        }
        binding.latestChapter.setText(text);
        binding.latestChapter.setVisibility(View.VISIBLE);
    }

    private void showSummary(String summary) {
        boolean show = !isBlank(summary);
        binding.synopsisTitle.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.summary.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.summaryToggle.setVisibility(View.GONE);
        if (!show) return;

        binding.summary.setText(summary);
        binding.summary.setMaxLines(summaryExpanded ? Integer.MAX_VALUE : SUMMARY_LINES);
        // only offer "more" when the text actually got cut
        binding.summary.post(() -> {
            Layout layout = binding.summary.getLayout();
            if (layout == null) return;
            boolean cut = layout.getEllipsisCount(layout.getLineCount() - 1) > 0;
            binding.summaryToggle.setVisibility(cut || summaryExpanded ? View.VISIBLE : View.GONE);
        });
    }

    private void toggleSummary() {
        if (binding.summaryToggle.getVisibility() != View.VISIBLE) return;
        summaryExpanded = !summaryExpanded;
        TransitionManager.beginDelayedTransition(binding.content);
        binding.summary.setMaxLines(summaryExpanded ? Integer.MAX_VALUE : SUMMARY_LINES);
        binding.summaryToggle.setText(summaryExpanded ? R.string.show_less : R.string.show_more);
    }

    // fade only: the default transition also animates bounds, which holds off layout while it
    // runs, and the details and the author's works arriving close together left the hero
    // stuck at its old size, hiding the badges and authors
    private void fadeIn() {
        TransitionManager.beginDelayedTransition(binding.content, new Fade());
    }

    private void showGenres(Manga m) {
        binding.genres.removeAllViews();
        boolean searchable = supportsSearch(m);
        for (Tag genre : m.genres) {
            Chip chip = new Chip(this);
            chip.setText(genre.name);
            if (searchable && genre.isSearchable()) {
                chip.setOnClickListener(v -> openSearch(Config.KEY_GENRE, genre.key));
            } else {
                chip.setClickable(false);
            }
            binding.genres.addView(chip);
        }
        binding.genres.setVisibility(m.genres.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void showAuthorWorks(List<Manga> items) {
        fadeIn();
        showCarousel(binding.authorSection, binding.authorWorks, authorWorks, items);
    }

    @SuppressWarnings("NotifyDataSetChanged")
    private void showCarousel(View section, RecyclerView view, List<Manga> shown, List<Manga> items) {
        shown.clear();
        if (items != null) shown.addAll(items);
        RecyclerView.Adapter<?> adapter = view.getAdapter();
        if (adapter != null) adapter.notifyDataSetChanged();
        section.setVisibility(shown.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void showError(String error) {
        binding.progress.hide();
        String message = isBlank(error) ? getString(R.string.details_error) : error;
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.retry, v -> {
                    binding.progress.show();
                    viewModel.retry(manga);
                })
                .show();
    }

    private Tag firstSearchableAuthor(Manga m) {
        for (Tag author : m.authors) {
            if (author.isSearchable()) return author;
        }
        return null;
    }

    private boolean supportsSearch(Manga m) {
        return Boolean.TRUE.equals(SourceManager.getSource(m.sourceId).meta().isSearchSupported);
    }

    // jumps into the search tab for this source, filtered by an author or a genre
    private void openSearch(String key, String value) {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.putExtra(HomeActivity.TARGET_FRAGMENT, HomeActivity.TARGET_SEARCH);
        intent.putExtra(Config.KEY_SOURCE_ID, manga.sourceId);
        intent.putExtra(key, value);
        startActivity(intent);
    }

    @Override
    public void onMangaItemClick(Manga item) {
        startActivity(new Intent(this, DetailsActivity.class).putExtra(Config.KEY_MANGA, item));
    }

    private void toggleLibrary() {
        Manga m = manga;
        boolean adding = !inLibrary;
        AppDatabase.databaseExecutor.execute(() -> {
            if (adding) {
                AppDatabase.getDatabase().mangaDao().insert(m);
            } else {
                AppDatabase.getDatabase().mangaDao().delete(m);
            }
        });
        Snackbar.make(binding.getRoot(), adding ? R.string.added_to_library : R.string.removed_from_library, Snackbar.LENGTH_SHORT).show();
    }

    private void checkIfMangaInLibrary(String id) {
        ColorStateList defaultTint = binding.libraryToggle.getIconTint();
        ColorStateList defaultText = binding.libraryToggle.getTextColors();
        ColorStateList savedTint = ColorStateList.valueOf(
                MaterialColors.getColor(binding.libraryToggle, androidx.appcompat.R.attr.colorPrimary));
        AppDatabase.getDatabase().mangaDao().getById(id).observe(this, m -> {
            inLibrary = m != null;
            binding.libraryToggle.setIconResource(inLibrary ? R.drawable.ic_bookmark : R.drawable.ic_bookmark_border);
            binding.libraryToggle.setText(inLibrary ? R.string.library_saved : R.string.library_save);
            binding.libraryToggle.setIconTint(inLibrary ? savedTint : defaultTint);
            binding.libraryToggle.setTextColor(inLibrary ? savedTint : defaultText);
            binding.libraryToggle.setContentDescription(getString(inLibrary ? R.string.remove_from_library : R.string.add_to_library));
        });
    }

    private void checkIfMangaInHistory(String id) {
        AppDatabase.getDatabase().historyDao().getByMangaId(id).observe(this, h -> {
            if (h != null && !h.isEmpty()) {
                binding.read.setText(getString(R.string.continue_reading));
            }
        });
    }

    private void openInBrowser(Manga m) {
        String url = m.sourceId == 1 && !m.url.startsWith("https") ? "https://mangafire.to".concat(m.url) : m.url;
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    // an existing download is managed on the downloads screen, otherwise start one
    private void onDownloadClick() {
        if (downloadJob != null) {
            startActivity(new Intent(this, DownloadsActivity.class));
        } else {
            downloadPrompt.show(manga);
        }
    }

    private void showDownload() {
        downloadJob = DownloadQueue.get(this).findForManga(manga);
        if (downloadJob == null) {
            binding.downloadStatus.setText(R.string.download_epub_desc);
            binding.downloadProgress.setVisibility(View.GONE);
            return;
        }
        binding.downloadStatus.setText(DownloadText.status(this, downloadJob));
        boolean showProgress = downloadJob.status != DownloadJob.Status.COMPLETED;
        boolean indeterminate = DownloadText.indeterminate(downloadJob);
        if (binding.downloadProgress.isIndeterminate() != indeterminate) {
            // switching modes only works while hidden
            binding.downloadProgress.setVisibility(View.INVISIBLE);
            binding.downloadProgress.setIndeterminate(indeterminate);
        }
        binding.downloadProgress.setVisibility(showProgress ? View.VISIBLE : View.GONE);
        if (showProgress && !indeterminate) binding.downloadProgress.setProgressCompat(downloadJob.percent(), true);
    }

    private void navigateToChapterList() {
        if (manga == null) return;
        Bundle bundle = new Bundle();
        bundle.putParcelable(Config.KEY_MANGA, manga);
        ChapterFragment chapters = new ChapterFragment();
        chapters.setArguments(bundle);
        chapters.show(getSupportFragmentManager(), "chapters-sheet");
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
