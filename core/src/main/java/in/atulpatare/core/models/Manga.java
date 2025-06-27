package in.atulpatare.core.models;

import android.os.Parcel;
import android.os.Parcelable;


public class Manga implements Parcelable {
    public String id, name, url, cover, status, type, summary, author;
    public int rating, sourceId; // out of 10

    public  Manga() {}


    protected Manga(Parcel in) {
        id = in.readString();
        name = in.readString();
        url = in.readString();
        cover = in.readString();
        status = in.readString();
        type = in.readString();
        summary = in.readString();
        author = in.readString();
        rating = in.readInt();
        sourceId = in.readInt();
    }

    public static final Creator<Manga> CREATOR = new Creator<Manga>() {
        @Override
        public Manga createFromParcel(Parcel in) {
            return new Manga(in);
        }

        @Override
        public Manga[] newArray(int size) {
            return new Manga[size];
        }
    };

    @Override
    public String toString() {
        return "Manga{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", url='" + url + '\'' +
                ", cover='" + cover + '\'' +
                ", status='" + status + '\'' +
                ", sourceId='" + sourceId + '\'' +
                ", type='" + type + '\'' +
                ", summary='" + summary + '\'' +
                ", author='" + author + '\'' +
                ", rating=" + rating +
                '}';
    }


    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(name);
        dest.writeString(url);
        dest.writeString(cover);
        dest.writeString(status);
        dest.writeString(type);
        dest.writeString(summary);
        dest.writeString(author);
        dest.writeInt(rating);
        dest.writeInt(sourceId);
    }
}
