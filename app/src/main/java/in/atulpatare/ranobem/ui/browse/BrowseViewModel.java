package in.atulpatare.ranobem.ui.browse;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import in.atulpatare.core.models.Manga;
import in.atulpatare.core.network.repository.Repository;

public class BrowseViewModel extends ViewModel {
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
        new Repository(sourceId).mangas(page, queries, new Repository.Callback<>() {
            @Override
            public void onComplete(List<Manga> result) {
                List<Manga> old = items.getValue();
                if (old == null) {
                    old = new ArrayList<>();
                }
                old.addAll(result);
                items.postValue(old);
            }

            @Override
            public void onError(Exception e) {
                error.postValue(e.getLocalizedMessage());
            }
        });
        return items;
    }
}
