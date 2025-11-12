package in.atulpatare.ranobem.ui.history;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.List;

import in.atulpatare.core.models.Chapter;
import in.atulpatare.core.models.Manga;
import in.atulpatare.ranobem.config.Config;
import in.atulpatare.ranobem.database.AppDatabase;
import in.atulpatare.ranobem.databinding.FragmentHistoryBinding;
import in.atulpatare.ranobem.model.History;
import in.atulpatare.ranobem.ui.history.adapter.HistoryAdapter;
import in.atulpatare.ranobem.ui.reader.ReaderActivity;

public class HistoryFragment extends Fragment implements HistoryAdapter.OnHistoryItemClickListener {

    private FragmentHistoryBinding binding;

    public HistoryFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        Manga manga = history.getManga();
        Chapter chapter = history.getChapter();
        Bundle bundle = new Bundle();
        bundle.putParcelable(Config.KEY_CHAPTER, chapter);
        bundle.putParcelable(Config.KEY_MANGA, manga);
        bundle.putString(Config.KEY_PAGE, Config.PAGE_HISTORY);
        requireActivity().startActivity(new Intent(requireActivity(), ReaderActivity.class).putExtras(bundle));
    }
}