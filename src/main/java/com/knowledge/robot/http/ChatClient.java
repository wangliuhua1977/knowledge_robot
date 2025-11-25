package com.knowledge.robot.http;

import okhttp3.*;
import okio.BufferedSource;

import javax.net.ssl.*;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * 简单 HTTP 客户端（OkHttp），支持：
 *  - 忽略 SSL 证书校验
 *  - 常规 POST JSON
 *  - 流式读取（逐行回调）
 */
public class ChatClient {

    private final OkHttpClient client;
    private final String url;
    private final String token;

    public ChatClient(String url, String token) {
        this.url = url;
        this.token = token;
        this.client = buildUnsafeClient();
    }

    /** 忽略 SSL / Hostname 校验 */
    private OkHttpClient buildUnsafeClient() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {}
                        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {}
                        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[]{}; }
                    }
            };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                    .sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0])
                    .hostnameVerifier((hostname, session) -> true);

            // 可按需设置超时
            builder.callTimeout(java.time.Duration.ofSeconds(180));
            builder.readTimeout(java.time.Duration.ofSeconds(180));
            builder.connectTimeout(java.time.Duration.ofSeconds(30));
            builder.writeTimeout(java.time.Duration.ofSeconds(60));

            return builder.build();
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException(e);
        }
    }

    /** 常规 POST JSON（非流） */
    public Response postJson(String json) throws IOException {
        RequestBody body = RequestBody.create(json, MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", token)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();
        return client.newCall(request).execute();
    }

    /**
     * 流式 POST：逐行把响应内容回调出去（常见为 "event:" / "data:" 逐行）
     * 注意：此方法内部会消费完响应并自动关闭。
     */
    public void postJsonStream(String json, java.util.function.Consumer<String> onLine) throws Exception {
        RequestBody body = RequestBody.create(json, MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", token)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        Call call = client.newCall(request);
        Response resp = call.execute();
        try (resp) {
            if (!resp.isSuccessful()) {
                onLine.accept("HTTP " + resp.code());
                if (resp.body() != null) {
                    onLine.accept(resp.body().string());
                }
                return;
            }
            if (resp.body() == null) return;
            BufferedSource source = resp.body().source();
            while (!source.exhausted()) {
                String line = source.readUtf8LineStrict();
                // 逐行回调
                onLine.accept(line);
            }
        }
    }
}
