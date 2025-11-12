package in.atulpatare.ranobem.ui.chapters;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import in.atulpatare.core.models.Chapter;
import in.atulpatare.core.models.Manga;
import in.atulpatare.core.util.ListUtils;
import in.atulpatare.ranobem.R;
import in.atulpatare.ranobem.config.Config;
import in.atulpatare.ranobem.databinding.FragmentChapterBinding;
import in.atulpatare.ranobem.ui.reader.ReaderActivity;
import in.atulpatare.ranobem.utils.VrfFetcher;

public class ChapterFragment extends BottomSheetDialogFragment implements ChapterAdapter.OnChapterItemClickListener, VrfFetcher.onCompleteListener {
    private final List<Chapter> originalItems = new ArrayList<>();
    private FragmentChapterBinding binding;
    private ChaptersViewModel viewModel;
    private Manga manga;
    private ChapterAdapter adapter;


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
        if (manga.sourceId == 1) {
            String url = "https://mangafire.to" + manga.url.replace("/manga", "/read");
            VrfFetcher.fetchVrf(requireContext(), url, "/ajax/read/" + manga.id, this);
        } else {
            viewModel.getChapters(manga).observe(this, this::setChapter);
        }
    }

    @Override
    public void onVrf(String vrf) {
        Manga m = manga;
        m.url = vrf.replace("https://mangafire.to", "");
        new Handler(Looper.getMainLooper()).post(() -> {
            viewModel.getChapters(m).observe(this, this::setChapter);
        });
    }

    private void setUpUi() {
        binding.toolbar.setOnMenuItemClickListener(this::onMenuItemClick);
        binding.searchField.addTextChangedListener(new SearchBarTextWatcher());

        adapter = new ChapterAdapter(originalItems, this);
        binding.chapterList.setLayoutManager(new LinearLayoutManager(requireActivity()));
        binding.chapterList.addItemDecoration(new DividerItemDecoration(requireActivity(), DividerItemDecoration.VERTICAL));
        binding.chapterList.setAdapter(adapter);
    }

    private void setUpError(String error) {
        binding.progress.hide();
        if (originalItems.isEmpty()) {
            Snackbar.make(binding.getRoot(), error, Snackbar.LENGTH_LONG).show();
        }
    }

    private void searchResults(String keyword) {
        if (!keyword.isEmpty()) {
            List<Chapter> filtered = ListUtils.searchByName(keyword.toLowerCase(), originalItems);
            ChapterAdapter searchAdapter = new ChapterAdapter(filtered, this);
            binding.chapterList.setAdapter(searchAdapter);
        } else {
            binding.chapterList.setAdapter(adapter);
        }
    }

    private void setSearchView() {
        int mode = binding.searchView.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE;
        binding.searchView.setVisibility(mode);
    }

    @SuppressLint("NotifyDataSetChanged")
    private void setChapter(List<Chapter> chapters) {
        originalItems.clear();
        originalItems.addAll(chapters);
        adapter.notifyDataSetChanged();
        binding.toolbar.setTitle(String.format(Locale.getDefault(), "%d Chapters", chapters.size()));
        binding.progress.hide();
    }

    private void sort() {
        Collections.reverse(originalItems);
        adapter.notifyItemRangeChanged(0, originalItems.size());
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
