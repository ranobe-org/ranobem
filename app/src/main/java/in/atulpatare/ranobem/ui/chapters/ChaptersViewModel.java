package in.atulpatare.ranobem.ui.chapters;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;


import java.util.List;

import in.atulpatare.core.models.Chapter;
import in.atulpatare.core.models.Manga;
import in.atulpatare.core.network.repository.Repository;

public class ChaptersViewModel extends ViewModel {
    private MutableLiveData<String> error = new MutableLiveData<>();

    public MutableLiveData<String> getError() {
        return error = new MutableLiveData<>();
    }

    public MutableLiveData<List<Chapter>> getChapters(Manga manga) {
        MutableLiveData<List<Chapter>> chapters = new MutableLiveData<>();
        new Repository(manga.sourceId).chapters(manga, new Repository.Callback<List<Chapter>>() {
            @Override
            public void onComplete(List<Chapter> result) {
                chapters.postValue(result);
            }

            @Override
            public void onError(Exception e) {
                error.postValue(e.getLocalizedMessage());
            }
        });
        return chapters;
    }

    public MutableLiveData<Chapter> getChapter(Chapter chapter) {
        MutableLiveData<Chapter> chap = new MutableLiveData<>();
        new Repository(chapter.sourceId).chapter(chapter, new Repository.Callback<>() {
            @Override
            public void onComplete(Chapter result) {
                chap.postValue(result);
            }

            @Override
            public void onError(Exception e) {
                error.postValue(e.getLocalizedMessage());
            }
        });
        return chap;
    }
}
