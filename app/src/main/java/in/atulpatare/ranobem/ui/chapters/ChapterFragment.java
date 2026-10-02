package in.atulpatare.ranobem.ui.chapters;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import in.atulpatare.core.models.Chapter;
import in.atulpatare.core.models.Manga;
import in.atulpatare.core.util.ListUtils;
import in.atulpatare.ranobem.R;
import in.atulpatare.ranobem.config.Config;
import in.atulpatare.ranobem.database.AppDatabase;
import in.atulpatare.ranobem.databinding.FragmentChapterBinding;
import in.atulpatare.ranobem.model.History;
import in.atulpatare.ranobem.ui.reader.ReaderActivity;

public class ChapterFragment extends BottomSheetDialogFragment implements ChapterAdapter.OnChapterItemClickListener {
    private final List<Chapter> originalItems = new ArrayList<>();
    private FragmentChapterBinding binding;
    private ChaptersViewModel viewModel;
    private Manga manga;
    private ChapterAdapter adapter;
    private String keyword = "";
    private int lastReadId = Integer.MIN_VALUE;
    private boolean scrolledToLastRead;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            manga = getArguments().getParcelable(Config.KEY_MANGA);
        }
        viewModel = new ViewModelProvider(requireActivity()).get(ChaptersViewModel.class);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentChapterBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setUpUi();
        setUpObservers();
    }

    private void setUpObservers() {
        viewModel.getError().observe(getViewLifecycleOwner(), this::setUpError);
        viewModel.getChapters(manga).observe(getViewLifecycleOwner(), this::setChapter);

        // get history
        AppDatabase.getDatabase().historyDao().getByMangaId(manga.id).observe(getViewLifecycleOwner(), this::setUpHistory);
    }

    private void setUpUi() {
        binding.toolbar.setOnMenuItemClickListener(this::onMenuItemClick);
        binding.searchField.addTextChangedListener(new SearchBarTextWatcher());

        adapter = new ChapterAdapter(this);
        binding.chapterList.setLayoutManager(new LinearLayoutManager(requireActivity()));
        binding.chapterList.setAdapter(adapter);
    }

    private void setUpHistory(List<History> h) {
        adapter.setHistory(h);
        lastReadId = h.isEmpty() ? Integer.MIN_VALUE : h.get(0).chapterId;
        scrollToLastRead();
    }

    // once, when both the chapters and the history are in
    private void scrollToLastRead() {
        if (scrolledToLastRead || originalItems.isEmpty() || lastReadId == Integer.MIN_VALUE) return;
        scrolledToLastRead = true;
        for (int i = 0; i < originalItems.size(); i++) {
            if (originalItems.get(i).id == lastReadId) {
                ((LinearLayoutManager) binding.chapterList.getLayoutManager()).scrollToPositionWithOffset(i, 0);
                return;
            }
        }
    }

    private void setUpError(String error) {
        binding.progress.hide();
        if (originalItems.isEmpty()) {
            Snackbar.make(binding.getRoot(), error, Snackbar.LENGTH_LONG).show();
        }
    }

    private void searchResults(String keyword) {
        this.keyword = keyword.trim();
        showChapters();
    }

    // one adapter for both, so read state and tags stay while searching
    private void showChapters() {
        adapter.submit(keyword.isEmpty() ? originalItems : ListUtils.searchByName(keyword.toLowerCase(), originalItems));
    }

    private void setSearchView() {
        int mode = binding.searchView.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE;
        binding.searchView.setVisibility(mode);
    }

    private void setChapter(List<Chapter> chapters) {
        originalItems.clear();
        originalItems.addAll(chapters);
        showChapters();
        binding.toolbar.setTitle(getResources().getQuantityString(R.plurals.chapter_count, chapters.size(), chapters.size()));
        binding.toolbar.setSubtitle(lastUpdated(chapters));
        binding.progress.hide();
        scrollToLastRead();
        markSeen(chapters.size());
    }

    // "Updated 3 days ago", from the newest chapter's date when the source gives one
    @Nullable
    private String lastUpdated(List<Chapter> chapters) {
        long newest = 0;
        for (Chapter c : chapters) newest = Math.max(newest, c.updatedAt);
        if (newest <= 0) return null;
        CharSequence ago = DateUtils.getRelativeTimeSpanString(newest, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
        return getString(R.string.chapters_updated, ago);
    }

    // chapters seen here don't need a new chapter notification later
    private void markSeen(int count) {
        if (count <= 0) return;
        String id = manga.id;
        int sourceId = manga.sourceId;
        AppDatabase.databaseExecutor.execute(() -> AppDatabase.getDatabase().mangaDao().raiseKnownChapters(id, sourceId, count));
    }

    private void sort() {
        Collections.reverse(originalItems);
        showChapters();
    }


    @Override
    public void onChapterItemClick(Chapter item) {
        Bundle bundle = new Bundle();
        bundle.putParcelable(Config.KEY_CHAPTER, item);
        bundle.putParcelable(Config.KEY_MANGA, manga);
        bundle.putString(Config.KEY_PAGE, Config.PAGE_DETAILS);
        requireActivity().startActivity(new Intent(requireActivity(), ReaderActivity.class).putExtras(bundle));
    }

    @Override
    public boolean onMenuItemClick(MenuItem menuItem) {
        int id = menuItem.getItemId();
        if (id == R.id.sort) {
            sort();
        } else if (id == R.id.search) {
            setSearchView();
        }
        return true;
    }

    public class SearchBarTextWatcher implements TextWatcher {

        @Override
        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

        }

        @Override
        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            searchResults(charSequence.toString());
        }

        @Override
        public void afterTextChanged(Editable editable) {

        }
    }
}
