import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Small helper tools used by all the API files. */
public class Util {

    static final SecureRandom RANDOM = new SecureRandom();

    // ---------- reading and sending ----------

    static String readBody(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    static void send(HttpExchange ex, int code, String type, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(code, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }

    static void json(HttpExchange ex, int code, String jsonText) throws IOException {
        send(ex, code, "application/json; charset=utf-8", jsonText.getBytes(StandardCharsets.UTF_8));
    }

    static void error(HttpExchange ex, int code, String message) throws IOException {
        json(ex, code, "{\"ok\":false,\"error\":" + quote(message) + "}");
    }

    // ---------- tiny JSON tools (no libraries needed) ----------

    /** Turns text into a safe JSON string, with quotes. */
    static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 32) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    /** Reads a simple JSON object like {"name":"Raja","age":5} into a Map. */
    static Map<String, String> parse(String s) {
        Map<String, String> map = new HashMap<>();
        int n = s.length();
        int i = s.indexOf('{');
        if (i < 0) return map;
        i++;
        while (i < n) {
            while (i < n && (Character.isWhitespace(s.charAt(i)) || s.charAt(i) == ',')) i++;
            if (i >= n || s.charAt(i) != '"') break;
            int[] pos = {i};
            String key = readString(s, pos);
            i = pos[0];
            while (i < n && (Character.isWhitespace(s.charAt(i)) || s.charAt(i) == ':')) i++;
            String value;
            if (i < n && s.charAt(i) == '"') {
                pos[0] = i;
                value = readString(s, pos);
                i = pos[0];
            } else {
                int start = i;
                while (i < n && s.charAt(i) != ',' && s.charAt(i) != '}') i++;
                value = s.substring(start, i).trim();
            }
            map.put(key, value);
        }
        return map;
    }

    private static String readString(String s, int[] pos) {
        StringBuilder sb = new StringBuilder();
        int i = pos[0] + 1;                       // skip the opening quote
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '"') { i++; break; }          // closing quote
            if (c == '\\' && i + 1 < s.length()) {
                char d = s.charAt(i + 1);
                i += 2;
                switch (d) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case 'u':
                        sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                        break;
                    default:  sb.append(d);
                }
            } else {
                sb.append(c);
                i++;
            }
        }
        pos[0] = i;
        return sb.toString();
    }

    // ---------- passwords and tokens ----------

    static String newSalt() {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    /** Scrambles a password so the real password is never stored. */
    static String hashPassword(String password, String saltB64) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(),
                    Base64.getDecoder().decode(saltB64), 120_000, 256);
            byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static String newToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Reads the login cookie from the browser request. */
    static String cookie(HttpExchange ex, String name) {
        String header = ex.getRequestHeaders().getFirst("Cookie");
        if (header == null) return null;
        for (String part : header.split(";")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2 && kv[0].equals(name)) return kv[1];
        }
        return null;
    }
}