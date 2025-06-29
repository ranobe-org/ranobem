package in.atulpatare.ranobem.model;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.util.List;

import in.atulpatare.core.models.Chapter;

public class ChapterList implements Parcelable {
    public static final Creator<ChapterList> CREATOR = new Creator<ChapterList>() {
        @Override
        public ChapterList createFromParcel(Parcel in) {
            return new ChapterList(in);
        }

        @Override
        public ChapterList[] newArray(int size) {
            return new ChapterList[size];
        }
    };
    public List<Chapter> chapters;

    public ChapterList(List<Chapter> chapters) {
        this.chapters = chapters;
    }

    protected ChapterList(Parcel in) {
        chapters = in.createTypedArrayList(Chapter.CREATOR);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeTypedList(chapters);
    }


}
