package in.atulpatare.core.models;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.List;

public class Chapter implements Parcelable {

    public static final Creator<Chapter> CREATOR = new Creator<Chapter>() {
        @Override
        public Chapter createFromParcel(Parcel in) {
            return new Chapter(in);
        }

        @Override
        public Chapter[] newArray(int size) {
            return new Chapter[size];
        }
    };
    public String url;
    public String mangaId;
    public List<String> pages;
    public String name;
    public int id;
    public int sourceId;
    public float index;

    public Chapter() {
        this.url = "";
    }

    protected Chapter(Parcel in) {
        url = in.readString();
        mangaId = in.readString();
        pages = in.createStringArrayList();
        name = in.readString();
        id = in.readInt();
        sourceId = in.readInt();
        index = in.readFloat();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(url);
        dest.writeString(mangaId);
        dest.writeStringList(pages);
        dest.writeString(name);
        dest.writeInt(id);
        dest.writeInt(sourceId);
        dest.writeFloat(index);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String toString() {
        return "Chapter{" +
                "url='" + url + '\'' +
                ", mangaId='" + mangaId + '\'' +
                ", pages=" + pages +
                ", name='" + name + '\'' +
                ", id=" + id +
                ", sourceId=" + sourceId +
                ", index=" + index +
                '}';
    }

}
