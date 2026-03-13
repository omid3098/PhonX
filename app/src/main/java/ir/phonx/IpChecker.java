package ir.phonx;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;

public class IpChecker {

    private static final String TAG = "IpChecker";
    private static final String IP_SERVICE_URL = "https://ipwho.is/";
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 10_000;
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 2_000;

    public static class IpInfo {
        public final String ip;
        public final String country;

        public IpInfo(String ip, String country) {
            this.ip = ip;
            this.country = country;
        }
    }

    public interface Callback {
        void onIpResult(IpInfo info);
        void onIpError(String error);
    }

    /**
     * Fetches the public IP address and country on a background thread, routing
     * through the local SOCKS proxy so traffic goes through the VPN tunnel.
     * Retries up to MAX_RETRIES times. Callback is invoked on the main thread.
     */
    public void checkIp(int socksPort, Callback callback) {
        new Thread(() -> {
            // Brief pause to let Xray's SOCKS inbound fully initialize
            if (socksPort > 0) {
                try { Thread.sleep(1_000); } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            Exception lastError = null;
            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                try {
                    IpInfo info = fetchIp(socksPort);
                    new Handler(Looper.getMainLooper()).post(() -> callback.onIpResult(info));
                    return;
                } catch (Exception e) {
                    lastError = e;
                    Log.w(TAG, "IP check attempt " + attempt + "/" + MAX_RETRIES
                            + " failed: " + e.getMessage());
                    if (attempt < MAX_RETRIES) {
                        try { Thread.sleep(RETRY_DELAY_MS); } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
            final String errorMsg = lastError != null ? lastError.getMessage() : "IP check failed";
            new Handler(Looper.getMainLooper()).post(() -> callback.onIpError(errorMsg));
        }, "PhonX-IpCheck").start();
    }

    /** Package-private for testability — tests can subclass and override. */
    IpInfo fetchIp(int socksPort) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(IP_SERVICE_URL);
            if (socksPort > 0) {
                Proxy proxy = new Proxy(Proxy.Type.SOCKS,
                        new InetSocketAddress("127.0.0.1", socksPort));
                conn = (HttpURLConnection) url.openConnection(proxy);
            } else {
                conn = (HttpURLConnection) url.openConnection();
            }
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "PhonX-VPN");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);

            int code = conn.getResponseCode();
            if (code != 200) {
                throw new Exception("HTTP " + code);
            }

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();

            String body = sb.toString().trim();
            if (body.isEmpty()) {
                throw new Exception("Empty response");
            }

            JSONObject json = new JSONObject(body);
            String ip = json.optString("ip", "");
            String country = json.optString("country", "");

            if (ip.isEmpty()) {
                throw new Exception("No IP in response");
            }
            return new IpInfo(ip, country);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
