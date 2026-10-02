package in.atulpatare.ranobem.ui.details;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import in.atulpatare.core.models.Manga;
import in.atulpatare.core.network.repository.Repository;

public class DetailsViewModel extends ViewModel {
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Manga> details = new MutableLiveData<>();
    private final MutableLiveData<List<Manga>> authorWorks = new MutableLiveData<>();
    private boolean requested = false;
    private String authorKey = null;

    public LiveData<String> getError() {
        return error;
    }

    public LiveData<List<Manga>> getAuthorWorks() {
        return authorWorks;
    }

    // fetches once, later calls (e.g. after a rotation) get the same result
    public LiveData<Manga> getDetails(Manga m) {
        if (!requested) {
            requested = true;
            fetchDetails(m);
        }
        return details;
    }

    public void retry(Manga m) {
        fetchDetails(m);
    }

    private void fetchDetails(Manga m) {
        new Repository(m.sourceId).details(m, new Repository.Callback<>() {
            @Override
            public void onComplete(Manga result) {
                details.postValue(result);
            }

            @Override
            public void onError(Exception e) {
                e.printStackTrace();
                error.postValue(e.getLocalizedMessage());
            }
        });
    }

    // other series by the author, without the one being viewed
    public void loadAuthorWorks(Manga m, String key) {
        if (key.equals(authorKey)) return;
        authorKey = key;
        HashMap<String, String> queries = new HashMap<>();
        queries.put("author", key);
        new Repository(m.sourceId).search(queries, 1, new Repository.Callback<>() {
            @Override
            public void onComplete(List<Manga> result) {
                List<Manga> others = new ArrayList<>();
                for (Manga item : result) {
                    if (!item.id.equals(m.id)) others.add(item);
                }
                authorWorks.postValue(others);
            }

            @Override
            public void onError(Exception e) {
                // the section just stays hidden
                e.printStackTrace();
            }
        });
    }
}
