package in.atulpatare.ranobem.ui.history;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;

import java.util.List;

import in.atulpatare.core.models.Chapter;
import in.atulpatare.core.models.Manga;
import in.atulpatare.core.util.ListUtils;
import in.atulpatare.ranobem.config.Config;
import in.atulpatare.ranobem.database.AppDatabase;
import in.atulpatare.ranobem.databinding.FragmentHistoryBinding;
import in.atulpatare.ranobem.model.ChapterList;
import in.atulpatare.ranobem.model.History;
import in.atulpatare.ranobem.ui.chapters.ChaptersViewModel;
import in.atulpatare.ranobem.ui.history.adapter.HistoryAdapter;
import in.atulpatare.ranobem.ui.reader.ReaderActivity;
import in.atulpatare.ranobem.utils.VrfFetcher;

public class HistoryFragment extends Fragment implements HistoryAdapter.OnHistoryItemClickListener, VrfFetcher.onCompleteListener {

    private FragmentHistoryBinding binding;
    private Manga manga;
    private Chapter chapter;
    private ChaptersViewModel viewModel;

    public HistoryFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(ChaptersViewModel.class);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentHistoryBinding.inflate(inflater, container, false);
        binding.mangaList.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false));
        binding.mangaList.addItemDecoration(new DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL));
        binding.mangaList.setHasFixedSize(true);

        AppDatabase.getDatabase().historyDao().getAll().observe(getViewLifecycleOwner(), this::setHistories);
        return binding.getRoot();
    }

    private void setHistories(List<History> histories) {
        binding.progress.hide();
        binding.mangaList.setAdapter(new HistoryAdapter(histories, this));
    }

    @Override
    public void onHistoryItemClick(History history) {
        this.manga = history.getManga();
        this.chapter = history.getChapter();
        loadChapterList();
    }

    private void loadChapterList() {
        binding.progress.show();
        viewModel.getError().observe(getViewLifecycleOwner(), this::setUpError);
        if (manga.sourceId == 1) {
            String url = "https://mangafire.to" + manga.url.replace("/manga", "/read");
            VrfFetcher.fetchVrf(requireContext(), url, "/ajax/read/" + manga.id, this);
        } else {
            viewModel.getChapters(manga).observe(this, this::navigateToReader);
        }
    }

    private void setUpError(String error) {
        binding.progress.hide();
        Snackbar.make(binding.getRoot(), error, Snackbar.LENGTH_LONG).show();
    }

    private void navigateToReader(List<Chapter> chapters) {
        binding.progress.hide();
        Bundle bundle = new Bundle();
        bundle.putParcelable(Config.KEY_CHAPTER, chapter);
        bundle.putParcelable(Config.KEY_MANGA, manga);
        bundle.putParcelable(Config.KEY_CHAPTER_LIST, new ChapterList(ListUtils.sortByIndex(chapters)));
        requireActivity().startActivity(new Intent(requireActivity(), ReaderActivity.class).putExtras(bundle));
    }

    @Override
    public void onVrf(String vrf) {
        Manga m = manga;
        m.url = vrf.replace("https://mangafire.to", "");
        new Handler(Looper.getMainLooper()).post(() -> {
            viewModel.getChapters(m).observe(this, this::navigateToReader);
        });
    }
}