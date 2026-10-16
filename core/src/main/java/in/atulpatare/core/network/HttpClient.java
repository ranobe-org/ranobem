package in.atulpatare.core.network;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Cache;
import okhttp3.CacheControl;
import okhttp3.FormBody;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class HttpClient {
    private static OkHttpClient client = null;
    private static Context context;

    public static void initialize(Context context) {
        HttpClient.context = context;
    }

    private static OkHttpClient client() {
        if (client == null) {
            File cacheDir = new File(HttpClient.context.getCacheDir(), "cache-files");
            Cache cache = new Cache(cacheDir, 10 * 1024 * 1024); //10 MiB
            client = new OkHttpClient
                    .Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .addNetworkInterceptor(new CacheInterceptor()).cache(cache).build();
        }
        return client;
    }

    public static String GET(String url, HashMap<String, String> headers) throws IOException {
        Request.Builder builder = new Request.Builder().url(url);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            builder.addHeader(entry.getKey(), entry.getValue());
        }
        ResponseBody response = HttpClient.client().newCall(builder.build()).execute().body();
        return response == null ? "" : response.string();
    }

    public static String POST(String url, HashMap<String, String> headers, HashMap<String, String> form) throws IOException {
        Request.Builder builder = new Request.Builder().url(url);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            builder.addHeader(entry.getKey(), entry.getValue());
        }
        FormBody.Builder formBody = new FormBody.Builder();
        for (Map.Entry<String, String> entry : form.entrySet()) {
            formBody.add(entry.getKey(), entry.getValue());
        }
        ResponseBody response = HttpClient.client().newCall(builder.post(formBody.build()).build()).execute().body();
        return response == null ? "" : response.string();
    }

    // for JSON APIs (trackers): the body of a 2xx reply, anything else throws with its status code
    public static String POST_JSON(String url, HashMap<String, String> headers, String json) throws IOException {
        Request.Builder builder = new Request.Builder().url(url);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            builder.addHeader(entry.getKey(), entry.getValue());
        }
        RequestBody body = RequestBody.create(json, MediaType.get("application/json; charset=utf-8"));
        try (Response response = HttpClient.client().newCall(builder.post(body).build()).execute()) {
            ResponseBody responseBody = response.body();
            String text = responseBody == null ? "" : responseBody.string();
            if (!response.isSuccessful()) throw new HttpException(response.code(), text);
            return text;
        }
    }

    public static class HttpException extends IOException {
        public final int code;
        public final String body;

        public HttpException(int code, String body) {
            super("HTTP " + code);
            this.code = code;
            this.body = body;
        }
    }

    public static class CacheInterceptor implements Interceptor {
        @Override
        public Response intercept(Chain chain) throws IOException {
            Response response = chain.proceed(chain.request());

            CacheControl cacheControl = new CacheControl.Builder()
                    .maxAge(15, TimeUnit.MINUTES) // 15 minutes cache
                    .build();

            return response.newBuilder()
                    .removeHeader("Pragma")
                    .removeHeader("Cache-Control")
                    .header("Cache-Control", cacheControl.toString())
                    .build();
        }
    }
}
