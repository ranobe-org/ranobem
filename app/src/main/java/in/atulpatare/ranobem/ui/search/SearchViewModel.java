package in.atulpatare.ranobem.ui.search;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import in.atulpatare.core.models.Manga;
import in.atulpatare.core.network.repository.Repository;

public class SearchViewModel extends ViewModel {
    private MutableLiveData<String> error = new MutableLiveData<>();
    private MutableLiveData<List<Manga>> items;
    private int currentSourceId = -1;

    public MutableLiveData<String> getError() {
        return error = new MutableLiveData<>();
    }

    public MutableLiveData<List<Manga>> getMangas(int sourceId, int page, HashMap<String, String> queries) {
        if (currentSourceId != sourceId) {
            items = new MutableLiveData<>();
            currentSourceId = sourceId;
        }
        // results belong to the search that requested them, a newer search must not receive them
        MutableLiveData<List<Manga>> target = items;
        new Repository(sourceId).mangas(page, queries, new Repository.Callback<>() {
            @Override
            public void onComplete(List<Manga> result) {
                if (target != items) return;
                List<Manga> old = target.getValue();
                List<Manga> merged = old == null ? new ArrayList<>() : new ArrayList<>(old);
                merged.addAll(result);
                target.postValue(merged);
            }

            @Override
            public void onError(Exception e) {
                error.postValue(e.getLocalizedMessage());
            }
        });
        return items;
    }

    public void clearItems() {
        items = new MutableLiveData<>();
    }
}
