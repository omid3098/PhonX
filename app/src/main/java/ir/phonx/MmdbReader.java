package ir.phonx;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Minimal MaxMind DB (MMDB) format reader for country-level GeoIP lookups.
 * Zero external dependencies — reads the binary format directly.
 * Supports 24, 28, and 32-bit record sizes; IPv4 and IPv6 databases.
 */
class MmdbReader {

    private static final byte[] METADATA_MARKER = {
            (byte) 0xAB, (byte) 0xCD, (byte) 0xEF,
            'M', 'a', 'x', 'M', 'i', 'n', 'd', '.', 'c', 'o', 'm'
    };

    private final byte[] data;
    private final int nodeCount;
    private final int recordSize;
    private final int nodeByteSize;
    private final int searchTreeSize;
    private final int dataSectionOffset;
    private final int ipVersion;

    MmdbReader(InputStream in) throws IOException {
        data = readAllBytes(in);

        int metaStart = findMetadataStart();
        if (metaStart < 0) throw new IOException("Invalid MMDB: metadata marker not found");

        int[] pos = {metaStart};
        Object metaObj = decode(pos, metaStart);
        if (!(metaObj instanceof Map))
            throw new IOException("Invalid MMDB: metadata is not a map");

        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) metaObj;
        nodeCount = toInt(meta.get("node_count"));
        recordSize = toInt(meta.get("record_size"));
        ipVersion = toInt(meta.get("ip_version"));

        if (nodeCount == 0 || recordSize == 0)
            throw new IOException("Invalid MMDB: bad metadata (nodeCount=" + nodeCount
                    + ", recordSize=" + recordSize + ")");

        nodeByteSize = recordSize * 2 / 8;
        searchTreeSize = nodeCount * nodeByteSize;
        dataSectionOffset = searchTreeSize + 16;
    }

    /** Returns the English country name for the given IP, or "" if not found. */
    String lookupCountry(String ipAddress) {
        try {
            InetAddress addr = InetAddress.getByName(ipAddress);
            byte[] ipBytes = addr.getAddress();
            byte[] bits;
            int bitCount;

            if (ipVersion == 6 && ipBytes.length == 4) {
                // IPv4 in an IPv6 database: use IPv4-mapped address ::ffff:x.x.x.x
                bits = new byte[16];
                bits[10] = (byte) 0xFF;
                bits[11] = (byte) 0xFF;
                System.arraycopy(ipBytes, 0, bits, 12, 4);
                bitCount = 128;
            } else if (ipVersion == 4 && ipBytes.length == 4) {
                bits = ipBytes;
                bitCount = 32;
            } else {
                bits = ipBytes;
                bitCount = ipVersion == 6 ? 128 : 32;
            }

            // Walk the binary search tree
            int node = 0;
            for (int i = 0; i < bitCount && node < nodeCount; i++) {
                int bit = (bits[i / 8] >> (7 - (i % 8))) & 1;
                node = readRecord(node, bit);
            }

            if (node <= nodeCount) return "";   // Not found

            int offset = searchTreeSize + (node - nodeCount);
            if (offset >= data.length) return "";

            int[] pos = {offset};
            Object record = decode(pos, dataSectionOffset);
            return extractCountryName(record);
        } catch (Exception e) {
            return "";
        }
    }

    // ── Search tree ────────────────────────────────────────────────────────────

    private int readRecord(int nodeIndex, int bit) {
        int base = nodeIndex * nodeByteSize;
        if (recordSize == 28) {
            if (bit == 0) {
                return ((data[base + 3] & 0xF0) >>> 4) << 24
                        | (data[base] & 0xFF) << 16
                        | (data[base + 1] & 0xFF) << 8
                        | (data[base + 2] & 0xFF);
            } else {
                return (data[base + 3] & 0x0F) << 24
                        | (data[base + 4] & 0xFF) << 16
                        | (data[base + 5] & 0xFF) << 8
                        | (data[base + 6] & 0xFF);
            }
        } else if (recordSize == 32) {
            int off = base + bit * 4;
            return (data[off] & 0xFF) << 24
                    | (data[off + 1] & 0xFF) << 16
                    | (data[off + 2] & 0xFF) << 8
                    | (data[off + 3] & 0xFF);
        } else { // 24
            int off = base + bit * 3;
            return (data[off] & 0xFF) << 16
                    | (data[off + 1] & 0xFF) << 8
                    | (data[off + 2] & 0xFF);
        }
    }

    // ── Country extraction ─────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private String extractCountryName(Object record) {
        if (!(record instanceof Map)) return "";
        Map<String, Object> root = (Map<String, Object>) record;
        Object countryObj = root.get("country");
        if (!(countryObj instanceof Map)) return "";
        Map<String, Object> country = (Map<String, Object>) countryObj;

        // Try names → en first
        Object namesObj = country.get("names");
        if (namesObj instanceof Map) {
            Object en = ((Map<String, Object>) namesObj).get("en");
            if (en instanceof String) return (String) en;
        }
        // Fallback to ISO code
        Object iso = country.get("iso_code");
        if (iso instanceof String) return (String) iso;
        return "";
    }

    // ── MMDB data decoder ──────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Object decode(int[] pos, int pointerBase) {
        if (pos[0] >= data.length) return null;

        int ctrlByte = data[pos[0]] & 0xFF;
        pos[0]++;

        int type = ctrlByte >> 5;
        if (type == 0) {
            // Extended type
            type = (data[pos[0]] & 0xFF) + 7;
            pos[0]++;
        }

        // Pointer — decode target, then restore position
        if (type == 1) {
            return decodePointer(ctrlByte, pos, pointerBase);
        }

        // Payload size
        int size = ctrlByte & 0x1F;
        if (size == 29) {
            size = 29 + (data[pos[0]] & 0xFF);
            pos[0]++;
        } else if (size == 30) {
            size = 285 + ((data[pos[0]] & 0xFF) << 8) + (data[pos[0] + 1] & 0xFF);
            pos[0] += 2;
        } else if (size == 31) {
            size = 65821
                    + ((data[pos[0]] & 0xFF) << 16)
                    + ((data[pos[0] + 1] & 0xFF) << 8)
                    + (data[pos[0] + 2] & 0xFF);
            pos[0] += 3;
        }

        switch (type) {
            case 2: { // UTF-8 string
                String s = new String(data, pos[0], size, StandardCharsets.UTF_8);
                pos[0] += size;
                return s;
            }
            case 7: { // map
                Map<String, Object> map = new HashMap<>();
                for (int i = 0; i < size; i++) {
                    Object key = decode(pos, pointerBase);
                    Object value = decode(pos, pointerBase);
                    if (key instanceof String) map.put((String) key, value);
                }
                return map;
            }
            case 11: { // array
                Object[] arr = new Object[size];
                for (int i = 0; i < size; i++) arr[i] = decode(pos, pointerBase);
                return arr;
            }
            case 5:  // uint16
            case 6:  // uint32
            case 9:  // uint64
            case 10: { // uint128
                long val = 0;
                for (int i = 0; i < size; i++) val = (val << 8) | (data[pos[0]++] & 0xFF);
                return val;
            }
            case 8: { // int32
                int val = 0;
                for (int i = 0; i < size; i++) val = (val << 8) | (data[pos[0]++] & 0xFF);
                return val;
            }
            case 3:  pos[0] += 8; return 0.0;          // double
            case 15: pos[0] += 4; return 0.0f;         // float
            case 4:  pos[0] += size; return "";         // bytes (unused)
            case 14: return size != 0;                  // boolean
            default: pos[0] += size; return null;
        }
    }

    private Object decodePointer(int ctrlByte, int[] pos, int pointerBase) {
        int ptrSize = (ctrlByte >> 3) & 0x03;
        int value = ctrlByte & 0x07;
        int pointer;

        switch (ptrSize) {
            case 0:
                pointer = (value << 8) | (data[pos[0]] & 0xFF);
                pos[0]++;
                break;
            case 1:
                pointer = ((value << 16)
                        | ((data[pos[0]] & 0xFF) << 8)
                        | (data[pos[0] + 1] & 0xFF)) + 2048;
                pos[0] += 2;
                break;
            case 2:
                pointer = ((value << 24)
                        | ((data[pos[0]] & 0xFF) << 16)
                        | ((data[pos[0] + 1] & 0xFF) << 8)
                        | (data[pos[0] + 2] & 0xFF)) + 526336;
                pos[0] += 3;
                break;
            default:
                pointer = ((data[pos[0]] & 0xFF) << 24)
                        | ((data[pos[0] + 1] & 0xFF) << 16)
                        | ((data[pos[0] + 2] & 0xFF) << 8)
                        | (data[pos[0] + 3] & 0xFF);
                pos[0] += 4;
                break;
        }

        int savedPos = pos[0];
        pos[0] = pointerBase + pointer;
        Object result = decode(pos, pointerBase);
        pos[0] = savedPos;
        return result;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private int findMetadataStart() {
        outer:
        for (int i = data.length - METADATA_MARKER.length; i >= 0; i--) {
            for (int j = 0; j < METADATA_MARKER.length; j++) {
                if (data[i + j] != METADATA_MARKER[j]) continue outer;
            }
            return i + METADATA_MARKER.length;
        }
        return -1;
    }

    private static int toInt(Object o) {
        return o instanceof Number ? ((Number) o).intValue() : 0;
    }

    private static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        return out.toByteArray();
    }
}
