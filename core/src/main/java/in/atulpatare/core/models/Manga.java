package in.atulpatare.core.models;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
public class Manga implements Parcelable {
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
    @NonNull
    @PrimaryKey
    public String id;
    public String name, url, cover, status, type, summary, author;
    public int rating, sourceId; // out of 10

    // extra details, filled by Source.details() and never stored in the library
    @Ignore
    public List<Tag> authors = new ArrayList<>();
    @Ignore
    public List<Tag> genres = new ArrayList<>();
    @Ignore
    public String released, latestChapter;
    @Ignore
    public long subscribers, latestChapterAt; // latestChapterAt is epoch millis, 0 when unknown
    @Ignore
    public boolean official, anime, adult;
    @Ignore
    public List<Manga> related = new ArrayList<>();
    @Ignore
    public List<Manga> recommendations = new ArrayList<>();
    // how this manga relates to the one it is listed under, e.g. "Prequel"
    @Ignore
    public String relation;


    public Manga() {
        id = "";
    }

    protected Manga(Parcel in) {
        id = Objects.requireNonNull(in.readString());
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
