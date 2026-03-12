package ir.phonx;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import libv2ray.CoreCallbackHandler;
import libv2ray.CoreController;
import libv2ray.Libv2ray;

/**
 * Controls the Xray/v2ray core via AndroidLibXrayLite.
 *
 * Real API (from libv2ray.aar v26.3.9):
 *   Libv2ray.initCoreEnv(String assetsPath, String key)
 *   Libv2ray.newCoreController(CoreCallbackHandler) → CoreController
 *   CoreController.startLoop(String configJson, int tunFd) throws Exception
 *   CoreController.stopLoop() throws Exception
 *
 * Architecture: Xray takes the TUN file descriptor directly.
 * Traffic flow: TUN fd → Xray (tun inbound) → vless/vmess outbound → VPS → Internet
 * DPI bypass: achieved via TLS+WebSocket or Reality transport on the vless/vmess outbound.
 */
public class XrayController {

    private static final String TAG = "XrayController";

    private final Context context;
    private CoreController coreController;

    public XrayController(Context context) {
        this.context = context.getApplicationContext();
    }

    public void start(ConfigParser.ProxyConfig config, int tunFd) throws Exception {
        stop();

        String primaryAbi = Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "";
        if (!primaryAbi.startsWith("arm")) {
            throw new Exception("Xray native library requires ARM device (detected: " + primaryAbi + ")");
        }

        String assetsPath = context.getFilesDir().getAbsolutePath();
        Libv2ray.initCoreEnv(assetsPath, "");

        coreController = Libv2ray.newCoreController(new XrayCoreCallback());

        String configJson = buildConfig(config);
        Log.d(TAG, "Starting Xray, tunFd=" + tunFd);
        coreController.startLoop(configJson, tunFd);
        Log.i(TAG, "Xray started: " + Libv2ray.checkVersionX());
    }

    public void stop() {
        if (coreController == null) return;
        try {
            coreController.stopLoop();
        } catch (Throwable t) {
            Log.w(TAG, "stopLoop: " + t.getMessage());
        }
        coreController = null;
        Log.i(TAG, "Xray stopped");
    }

    public boolean isRunning() {
        return coreController != null && coreController.getIsRunning();
    }

    // ── Config builders ──────────────────────────────────────────────────────

    private String buildConfig(ConfigParser.ProxyConfig config) {
        String outbound = "vless".equals(config.protocol)
                ? buildVlessOutbound(config)
                : buildVmessOutbound(config);

        return "{\n"
            + "  \"log\": {\"loglevel\": \"warning\"},\n"
            + "  \"dns\": {\n"
            + "    \"servers\": [\"8.8.8.8\", \"8.8.4.4\", \"223.5.5.5\"]\n"
            + "  },\n"
            + "  \"inbounds\": [\n"
            + "    {\n"
            + "      \"protocol\": \"tun\",\n"
            + "      \"settings\": {\"mtu\": 1500, \"userLevel\": 0},\n"
            + "      \"tag\": \"tun-in\"\n"
            + "    }\n"
            + "  ],\n"
            + "  \"outbounds\": [\n"
            + outbound + ",\n"
            + "    {\"protocol\": \"freedom\", \"tag\": \"direct\"}\n"
            + "  ],\n"
            + "  \"routing\": {\n"
            + "    \"domainStrategy\": \"IPIfNonMatch\",\n"
            + "    \"rules\": [\n"
            + "      {\"type\": \"field\", \"outboundTag\": \"proxy\", \"network\": \"tcp,udp\"}\n"
            + "    ]\n"
            + "  }\n"
            + "}";
    }

    private String buildVlessOutbound(ConfigParser.ProxyConfig c) {
        return "    {\n"
            + "      \"tag\": \"proxy\",\n"
            + "      \"protocol\": \"vless\",\n"
            + "      \"settings\": {\n"
            + "        \"vnext\": [{\n"
            + "          \"address\": \"" + c.host + "\",\n"
            + "          \"port\": " + c.port + ",\n"
            + "          \"users\": [{\n"
            + "            \"id\": \"" + c.uuid + "\",\n"
            + "            \"encryption\": \"none\"\n"
            + "          }]\n"
            + "        }]\n"
            + "      },\n"
            + "      \"streamSettings\": " + buildStreamSettings(c) + "\n"
            + "    }";
    }

    private String buildVmessOutbound(ConfigParser.ProxyConfig c) {
        return "    {\n"
            + "      \"tag\": \"proxy\",\n"
            + "      \"protocol\": \"vmess\",\n"
            + "      \"settings\": {\n"
            + "        \"vnext\": [{\n"
            + "          \"address\": \"" + c.host + "\",\n"
            + "          \"port\": " + c.port + ",\n"
            + "          \"users\": [{\n"
            + "            \"id\": \"" + c.uuid + "\",\n"
            + "            \"alterId\": 0,\n"
            + "            \"security\": \"auto\"\n"
            + "          }]\n"
            + "        }]\n"
            + "      },\n"
            + "      \"streamSettings\": " + buildStreamSettings(c) + "\n"
            + "    }";
    }

    private String buildStreamSettings(ConfigParser.ProxyConfig c) {
        String network  = c.network  != null ? c.network  : "tcp";
        String security = c.security != null ? c.security : "none";
        String sni      = c.sni      != null ? c.sni      : c.host;
        String path     = c.path     != null ? c.path     : "/";

        StringBuilder sb = new StringBuilder();
        sb.append("{\n")
          .append("        \"network\": \"").append(network).append("\",\n")
          .append("        \"security\": \"").append(security).append("\"");

        if ("ws".equals(network)) {
            sb.append(",\n        \"wsSettings\": {\n")
              .append("          \"path\": \"").append(path).append("\",\n")
              .append("          \"headers\": {\"Host\": \"").append(sni).append("\"}\n")
              .append("        }");
        } else if ("grpc".equals(network)) {
            sb.append(",\n        \"grpcSettings\": {\n")
              .append("          \"serviceName\": \"").append(path.replaceAll("^/", "")).append("\"\n")
              .append("        }");
        }

        if ("tls".equals(security)) {
            sb.append(",\n        \"tlsSettings\": {\n")
              .append("          \"serverName\": \"").append(sni).append("\",\n")
              .append("          \"allowInsecure\": false\n")
              .append("        }");
        } else if ("reality".equals(security)) {
            sb.append(",\n        \"realitySettings\": {\n")
              .append("          \"serverName\": \"").append(sni).append("\"\n")
              .append("        }");
        }

        sb.append("\n      }");
        return sb.toString();
    }

    // ── CoreCallbackHandler implementation ───────────────────────────────────

    private static class XrayCoreCallback implements CoreCallbackHandler {
        @Override
        public long startup() {
            Log.d(TAG, "Xray core startup");
            return 0;
        }

        @Override
        public long shutdown() {
            Log.d(TAG, "Xray core shutdown");
            return 0;
        }

        @Override
        public long onEmitStatus(long code, String status) {
            Log.d(TAG, "Xray status [" + code + "]: " + status);
            return 0;
        }
    }
}
