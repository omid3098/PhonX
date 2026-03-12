package ir.phonx;

import android.util.Base64;

import org.json.JSONObject;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class ConfigParser {

    public static class ProxyConfig {
        public String protocol;  // "vless" or "vmess"
        public String uuid;
        public String host;
        public int port;
        public String security; // "tls", "reality", "none"
        public String network;  // "ws", "tcp", "grpc"
        public String path;
        public String sni;
        public String name;

        @Override
        public String toString() {
            return protocol + "://" + host + ":" + port + " [" + network + "/" + security + "]";
        }
    }

    public static ProxyConfig parse(String raw) throws Exception {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty config");
        }
        raw = raw.trim();

        if (raw.startsWith("vless://")) {
            return parseVless(raw);
        } else if (raw.startsWith("vmess://")) {
            return parseVmess(raw);
        } else {
            throw new IllegalArgumentException("Unsupported protocol. Use vless:// or vmess://");
        }
    }

    private static ProxyConfig parseVless(String raw) throws Exception {
        // Format: vless://UUID@host:port?security=tls&type=ws&path=/p&host=sni.com#name
        URI uri = new URI(raw);

        ProxyConfig config = new ProxyConfig();
        config.protocol = "vless";
        config.uuid = uri.getUserInfo();
        config.host = uri.getHost();
        config.port = uri.getPort();

        String query = uri.getRawQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length < 2) continue;
                String key = URLDecoder.decode(kv[0], "UTF-8");
                String value = URLDecoder.decode(kv[1], "UTF-8");
                switch (key) {
                    case "security":
                        config.security = value;
                        break;
                    case "type":
                        config.network = value;
                        break;
                    case "path":
                        config.path = value;
                        break;
                    case "host":
                    case "sni":
                        config.sni = value;
                        break;
                }
            }
        }

        String fragment = uri.getFragment();
        if (fragment != null) {
            config.name = URLDecoder.decode(fragment, "UTF-8");
        }

        if (config.security == null) config.security = "none";
        if (config.network == null) config.network = "tcp";
        if (config.path == null) config.path = "/";
        if (config.sni == null) config.sni = config.host;

        validateConfig(config);
        return config;
    }

    private static ProxyConfig parseVmess(String raw) throws Exception {
        // Format: vmess://BASE64JSON
        String b64 = raw.substring("vmess://".length()).trim();
        // Handle URL-safe base64
        b64 = b64.replace('-', '+').replace('_', '/');
        // Pad if needed
        while (b64.length() % 4 != 0) b64 += "=";

        byte[] decoded = Base64.decode(b64, Base64.DEFAULT);
        String json = new String(decoded, StandardCharsets.UTF_8);

        JSONObject obj = new JSONObject(json);

        ProxyConfig config = new ProxyConfig();
        config.protocol = "vmess";
        config.uuid = obj.optString("id", "");
        config.host = obj.optString("add", "");
        config.port = obj.optInt("port", 443);
        config.network = obj.optString("net", "tcp");
        config.security = obj.optString("tls", "none");
        config.path = obj.optString("path", "/");
        config.sni = obj.optString("host", config.host);
        config.name = obj.optString("ps", "");

        if (config.security.equals("1") || config.security.equals("true")) {
            config.security = "tls";
        }

        validateConfig(config);
        return config;
    }

    private static void validateConfig(ProxyConfig config) throws Exception {
        if (config.uuid == null || config.uuid.isEmpty()) {
            throw new IllegalArgumentException("Missing UUID");
        }
        if (config.host == null || config.host.isEmpty()) {
            throw new IllegalArgumentException("Missing host");
        }
        if (config.port <= 0 || config.port > 65535) {
            throw new IllegalArgumentException("Invalid port: " + config.port);
        }
    }
}
