package in.atulpatare.core.util;

public class VrfExtractor {
    public static String extractVrf(String url) {
        String key = "vrf=";
        int start = url.indexOf(key);
        if (start == -1) return "";

        start += key.length(); // move past "vrf="
        int end = url.indexOf('&', start); // in case there are other params after vrf
        if (end == -1) {
            end = url.length();
        }

        return url.substring(start, end);
    }
}
