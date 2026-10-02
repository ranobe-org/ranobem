package in.atulpatare.ranobem.config;

import in.atulpatare.ranobem.BuildConfig;

public class Config {
    public static final String KEY_MANGA = "manga";
    public static final String KEY_CHAPTER = "chapter";
    public static final String KEY_PAGE = "from_page";
    public static final String PAGE_HISTORY = "history";
    public static final String PAGE_DETAILS = "details";
    // opening search pre-filtered, e.g. from a genre or author on the details page
    public static final String KEY_SOURCE_ID = "source_id";
    public static final String KEY_AUTHOR = "author";
    public static final String KEY_GENRE = "genre";

    public static final String GOOGLE_FORM_LINK = "https://forms.gle/oGKihEBEzx9WTk6K6";
    public static final String KEEP_ANDROID_OPEN_LINK = "https://keepandroidopen.org/";
    public static final String DISCORD_LINK = "https://discord.gg/6CQ6u64dca";
    public static final String RANOBE_LINK = "https://play.google.com/store/apps/details?id=org.ranobe.downloader.pro";
    public static final String PRO_LINK = "https://play.google.com/store/apps/details?id=in.atulpatare.ranobem.pro";

    public static boolean isFree() {
        return !BuildConfig.IS_PRO;
    }
}
