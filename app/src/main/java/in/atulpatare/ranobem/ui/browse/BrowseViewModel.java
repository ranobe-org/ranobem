package in.atulpatare.ranobem.ui.browse;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;

import in.atulpatare.core.models.Manga;
import in.atulpatare.core.network.repository.Repository;

public class BrowseViewModel extends ViewModel {
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<List<Manga>> items = new MutableLiveData<>();

    public LiveData<String> getError() {
        return error;
    }

    public LiveData<List<Manga>> getItems() {
        return items;
    }

    // errors are shown once, a recreated view must not see an old one again
    public void consumeError() {
        error.setValue(null);
    }

    public boolean hasItems() {
        return items.getValue() != null;
    }

    // appends the page to what is already loaded
    public void load(int sourceId, int page) {
        new Repository(sourceId).mangas(page, null, new Repository.Callback<>() {
            @Override
            public void onComplete(List<Manga> result) {
                List<Manga> old = items.getValue();
                List<Manga> merged = old == null ? new ArrayList<>() : new ArrayList<>(old);
                merged.addAll(result);
                items.postValue(merged);
            }

            @Override
            public void onError(Exception e) {
                error.postValue(e.getLocalizedMessage());
            }
        });
    }
}
