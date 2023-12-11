package red.lilu.app.tool;

import android.util.Log;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import java9.util.concurrent.CompletableFuture;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;

public class Dns {

    private static final String T = "调试-DNS";
    private static final String DOH = "https://1.12.12.12/dns-query";
    private static final Dns ourInstance = new Dns();
    private final OkHttpClient httpClient;

    private Dns() {
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .build();
    }

    private static class DNSResponse {
        LinkedList<DNSResponseAnswer> Answer;
    }

    private static class DNSResponseAnswer {
        int type;
        String data;
    }

    public static Dns getInstance() {
        return ourInstance;
    }

    /**
     * 查询TXT类型, 结果已经去除首尾双引号
     */
    public void txt(ExecutorService executorService,
                    String domain,
                    java9.util.function.Consumer<String> onError,
                    java9.util.function.Consumer<HashSet<String>> onDone) {
        CompletableFuture.runAsync(() -> {
            try {
                HashSet<String> list = new HashSet<>();

                okhttp3.Request request = new okhttp3.Request.Builder()
                        .url(String.format("%s?type=16&name=%s", DOH, domain))
                        .header("accept", "application/dns-json")
                        .build();
                try (okhttp3.Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        onError.accept(String.format(Locale.CHINA, "查询DNS TXT接口出错:%d", response.code()));
                        return;
                    }

                    try (ResponseBody body = response.body()) {
                        if (body == null) {
                            onError.accept("查询DNS TXT接口出错:没有Body");
                            return;
                        }

                        String bodyText = body.string();
                        Log.d(T, bodyText);

                        DNSResponse dnsResponse = new Gson().fromJson(
                                bodyText,
                                DNSResponse.class
                        );
                        if (dnsResponse.Answer != null) {
                            for (DNSResponseAnswer dnsAnswer : dnsResponse.Answer) {
                                if (dnsAnswer.type != 16) {
                                    continue;
                                }

                                String data = dnsAnswer.data;
                                data = data.substring(1, data.length() - 1);
//                                Log.d(T, "查询DNS TXT:" + data);
                                list.add(data);
                            }
                        }
                    }
                }

                onDone.accept(list);
            } catch (Exception e) {
                onError.accept(e.getMessage());
            }
        }, executorService);
    }

}
