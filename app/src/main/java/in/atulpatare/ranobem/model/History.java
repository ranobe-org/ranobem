package in.atulpatare.ranobem.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import in.atulpatare.core.models.Chapter;
import in.atulpatare.core.models.Manga;

@Entity
public class History {
    @NonNull
    @PrimaryKey
    public String id;
    public String mangaId, mangaUrl, mangaName, cover, chapterUrl, chapterName;
    public int chapterId, sourceId;
    public float chapterIndex;
    public long createdAt;

    public History() {
        // public constructor
        id = "";
    }

    @Ignore
    public History(Manga manga, Chapter chapter) {
        this.id = manga.id.concat("-").concat(String.valueOf(chapter.id));
        this.mangaId = manga.id;
        this.mangaUrl = manga.url;
        this.mangaName = manga.name;
        this.cover = manga.cover;
        this.chapterUrl = chapter.url;
        this.sourceId = chapter.sourceId;
        this.chapterId = chapter.id;
        this.chapterIndex = chapter.index;
        this.chapterName = chapter.name;
        this.createdAt = System.currentTimeMillis();
    }

    public Manga getManga() {
        Manga m = new Manga();
        m.id = this.mangaId;
        m.url = this.mangaUrl;
        m.sourceId = this.sourceId;
        m.name = this.mangaName;
        m.cover = this.cover;
        return m;
    }

    public Chapter getChapter() {
        Chapter chapter = new Chapter();
        chapter.id = this.chapterId;
        chapter.index = this.chapterIndex;
        chapter.url = this.chapterUrl;
        chapter.sourceId = this.sourceId;
        chapter.mangaId = this.mangaId;
        chapter.name = this.chapterName;
        return chapter;
    }
}
