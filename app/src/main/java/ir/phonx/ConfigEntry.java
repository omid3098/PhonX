package ir.phonx;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

public class ConfigEntry {

    public String id;
    public String rawUri;
    public String displayName;
    public String protocol;
    public String host;
    public int port;

    public ConfigEntry() {}

    public static ConfigEntry fromUri(String rawUri) throws Exception {
        ConfigParser.ProxyConfig parsed = ConfigParser.parse(rawUri);
        ConfigEntry entry = new ConfigEntry();
        entry.id = UUID.randomUUID().toString();
        entry.rawUri = rawUri;
        entry.protocol = parsed.protocol;
        entry.host = parsed.host;
        entry.port = parsed.port;
        if (parsed.name != null && !parsed.name.isEmpty()) {
            entry.displayName = parsed.name;
        } else {
            entry.displayName = parsed.protocol + "://" + parsed.host + ":" + parsed.port;
        }
        return entry;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("rawUri", rawUri);
        obj.put("displayName", displayName);
        obj.put("protocol", protocol);
        obj.put("host", host);
        obj.put("port", port);
        return obj;
    }

    public static ConfigEntry fromJson(JSONObject obj) throws JSONException {
        ConfigEntry entry = new ConfigEntry();
        entry.id = obj.getString("id");
        entry.rawUri = obj.getString("rawUri");
        entry.displayName = obj.getString("displayName");
        entry.protocol = obj.getString("protocol");
        entry.host = obj.getString("host");
        entry.port = obj.getInt("port");
        return entry;
    }
}
