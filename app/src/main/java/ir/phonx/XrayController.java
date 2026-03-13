package ir.phonx;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import phonxcore.CoreCallbackHandler;
import phonxcore.Phonxcore;

/**
 * Controls the Xray/v2ray core via phonxcore (combined Go module).
 *
 * Real API (from phonxcore):
 *   Phonxcore.initXrayEnv(String assetsPath, String key)
 *   Phonxcore.newXrayController(CoreCallbackHandler) → phonxcore.XrayController
 *   controller.startLoop(String configJson, int tunFd) throws Exception
 *   controller.stopLoop() throws Exception
 *
 * Architecture: Xray takes the TUN file descriptor directly.
 * Traffic flow (no Psiphon): TUN fd → Xray (tun inbound) → vless/vmess outbound → VPS → Internet
 * Traffic flow (with Psiphon): TUN fd → Xray (tun inbound) → [dialerProxy: psiphon-out] → Psiphon SOCKS → VPS → Internet
 */
public class XrayController {

    private static final String TAG = "XrayController";
    public static final int LOCAL_SOCKS_PORT = 10809;

    private final Context context;
    // Use fully qualified name to avoid collision with this class's name
    private phonxcore.XrayController coreController;

    public XrayController(Context context) {
        this.context = context.getApplicationContext();
    }

    /** Start Xray without Psiphon chain (direct connection). */
    public void start(ConfigParser.ProxyConfig config, int tunFd) throws Exception {
        start(config, tunFd, 0);
    }

    /** Start Xray, optionally routing through Psiphon SOCKS proxy. */
    public void start(ConfigParser.ProxyConfig config, int tunFd, int psiphonSocksPort) throws Exception {
        stop();

        String primaryAbi = Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "";
        if (!primaryAbi.startsWith("arm")) {
            throw new Exception("Xray native library requires ARM device (detected: " + primaryAbi + ")");
        }

        String assetsPath = context.getFilesDir().getAbsolutePath();
        Phonxcore.initXrayEnv(assetsPath, "");

        coreController = Phonxcore.newXrayController(new XrayCoreCallback());

        String configJson = buildConfig(config, psiphonSocksPort);
        Log.d(TAG, "Starting Xray, tunFd=" + tunFd + ", psiphonSocksPort=" + psiphonSocksPort);
        coreController.startLoop(configJson, tunFd);
        Log.i(TAG, "Xray started: " + Phonxcore.checkVersionX());
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

    private String buildConfig(ConfigParser.ProxyConfig config, int psiphonSocksPort) {
        String outbound = "vless".equals(config.protocol)
                ? buildVlessOutbound(config, psiphonSocksPort)
                : buildVmessOutbound(config, psiphonSocksPort);

        StringBuilder outbounds = new StringBuilder();
        outbounds.append(outbound);

        // When Psiphon is active, add a SOCKS outbound for the Psiphon local proxy
        if (psiphonSocksPort > 0) {
            outbounds.append(",\n")
                .append("    {\n")
                .append("      \"tag\": \"psiphon-out\",\n")
                .append("      \"protocol\": \"socks\",\n")
                .append("      \"settings\": {\n")
                .append("        \"servers\": [{\"address\": \"127.0.0.1\", \"port\": ").append(psiphonSocksPort).append("}]\n")
                .append("      }\n")
                .append("    }");
        }

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
            + "    },\n"
            + "    {\n"
            + "      \"protocol\": \"socks\",\n"
            + "      \"port\": " + LOCAL_SOCKS_PORT + ",\n"
            + "      \"listen\": \"127.0.0.1\",\n"
            + "      \"settings\": {\"auth\": \"noauth\"},\n"
            + "      \"tag\": \"socks-in\"\n"
            + "    }\n"
            + "  ],\n"
            + "  \"outbounds\": [\n"
            + outbounds + ",\n"
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

    private String buildVlessOutbound(ConfigParser.ProxyConfig c, int psiphonSocksPort) {
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
            + "      \"streamSettings\": " + buildStreamSettings(c, psiphonSocksPort) + "\n"
            + "    }";
    }

    private String buildVmessOutbound(ConfigParser.ProxyConfig c, int psiphonSocksPort) {
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
            + "      \"streamSettings\": " + buildStreamSettings(c, psiphonSocksPort) + "\n"
            + "    }";
    }

    private String buildStreamSettings(ConfigParser.ProxyConfig c, int psiphonSocksPort) {
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

        // When Psiphon is active, route this outbound through the Psiphon SOCKS proxy
        if (psiphonSocksPort > 0) {
            sb.append(",\n        \"sockopt\": {\n")
              .append("          \"dialerProxy\": \"psiphon-out\"\n")
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
