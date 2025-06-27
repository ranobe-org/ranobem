package in.atulpatare.ranobem.utils;

import java.util.List;

public class HtmlBuilder {
    public static String getHtml(List<String> urls) {
        StringBuilder images = new StringBuilder();
        for (String u : urls) {
            images.append("<img width=\"100%\" height=\"auto\" src=\"").append(u).append("\">");
        }
        return "<html> " +
                "<head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1, maximum-scale=1 \"></head>" +
                "<body>" +
                images +
                "</body>" +
                "</html>";
    }
}
