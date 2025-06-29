package in.atulpatare.ranobem.config;

import in.atulpatare.ranobem.BuildConfig;

public class Config {
    public static final String KEY_MANGA = "manga";
    public static final String KEY_CHAPTER = "chapter";
    public static final String KEY_CHAPTER_LIST = "chapter_list";

    public static boolean isFree () {
        return !BuildConfig.IS_PRO;
    }
}
