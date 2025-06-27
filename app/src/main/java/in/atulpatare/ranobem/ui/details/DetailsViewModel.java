package in.atulpatare.ranobem.ui.details;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import in.atulpatare.core.models.Manga;
import in.atulpatare.core.network.repository.Repository;

public class DetailsViewModel extends ViewModel {
    private MutableLiveData<String> error = new MutableLiveData<>();
    private MutableLiveData<Manga> item;
    private int currentSourceId = -1;
    private int page = 1;

    public MutableLiveData<String> getError() {
        return error = new MutableLiveData<>();
    }

    public MutableLiveData<Manga> getDetails(int sourceId, Manga m) {
        if (currentSourceId != sourceId) {
            item = new MutableLiveData<>();
            page = 1;
            currentSourceId = sourceId;
        } else {
            page += 1;
        }
        new Repository(sourceId).details(m, new Repository.Callback<Manga>() {
            @Override
            public void onComplete(Manga result) {
                item.postValue(result);
            }

            @Override
            public void onError(Exception e) {
                e.printStackTrace();
                error.postValue(e.getLocalizedMessage());
            }
        });
        return item;
    }
}
