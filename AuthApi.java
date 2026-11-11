import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Sign up, login, logout, "who am I" and forgot password for Bill Shield. */
public class AuthApi {

    static final Path USERS_FILE = Path.of("C:\\BillShieldApp", "data", "users.txt");
    static final Map<String, String> SESSIONS = new ConcurrentHashMap<>();   // token -> email
    static final Map<String, String[]> RESETS = new ConcurrentHashMap<>();   // email -> {code, expiryMillis, wrongTries}

    static void register(HttpServer server) throws IOException {
        Files.createDirectories(USERS_FILE.getParent());
        if (!Files.exists(USERS_FILE)) Files.createFile(USERS_FILE);

        server.createContext("/api/signup", AuthApi::signup);
        server.createContext("/api/login",  AuthApi::login);
        server.createContext("/api/logout", AuthApi::logout);
        server.createContext("/api/me",     AuthApi::me);
        server.createContext("/api/forgot", AuthApi::forgot);
        server.createContext("/api/reset",  AuthApi::reset);
    }

    // ---------- users file: email <TAB> name <TAB> salt <TAB> hash ----------

    static synchronized String[] findUser(String email) throws IOException {
        List<String> lines = Files.readAllLines(USERS_FILE, StandardCharsets.UTF_8);
        for (String line : lines) {
            String[] p = line.split("\t");
            if (p.length == 4 && p[0].equals(email)) return p;
        }
        return null;
    }

    static synchronized void addUser(String email, String name, String salt, String hash) throws IOException {
        String line = email + "\t" + name + "\t" + salt + "\t" + hash + "\n";
        Files.writeString(USERS_FILE, line, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    }

    static synchronized void changePassword(String email, String salt, String hash) throws IOException {
        List<String> lines = Files.readAllLines(USERS_FILE, StandardCharsets.UTF_8);
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            String[] p = line.split("\t");
            if (p.length == 4 && p[0].equals(email)) line = p[0] + "\t" + p[1] + "\t" + salt + "\t" + hash;
            out.add(line);
        }
        Files.write(USERS_FILE, out, StandardCharsets.UTF_8);
    }

    // ---------- the actions ----------

    static void signup(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { Util.error(ex, 405, "Use POST"); return; }
        Map<String, String> in = Util.parse(Util.readBody(ex));
        String name = clean(in.get("name"));
        String email = clean(in.get("email")).toLowerCase();
        String password = in.getOrDefault("password", "");

        if (name.isEmpty())                       { Util.error(ex, 400, "Please type your name."); return; }
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) { Util.error(ex, 400, "That email does not look right."); return; }
        if (password.length() < 6)                { Util.error(ex, 400, "Password must have at least 6 characters."); return; }
        if (findUser(email) != null)              { Util.error(ex, 409, "This email already has an account. Please login."); return; }

        String salt = Util.newSalt();
        addUser(email, name, salt, Util.hashPassword(password, salt));
        startSession(ex, email);
        Util.json(ex, 200, "{\"ok\":true,\"name\":" + Util.quote(name) + ",\"email\":" + Util.quote(email) + "}");
    }

    static void login(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { Util.error(ex, 405, "Use POST"); return; }
        Map<String, String> in = Util.parse(Util.readBody(ex));
        String email = clean(in.get("email")).toLowerCase();
        String password = in.getOrDefault("password", "");

        String[] user = findUser(email);
        if (user == null) { Util.error(ex, 401, "No account found for this email. Please click Sign Up first."); return; }
        String hash = Util.hashPassword(password, user[2]);
        if (!MessageDigest.isEqual(hash.getBytes(StandardCharsets.UTF_8), user[3].getBytes(StandardCharsets.UTF_8))) {
            Util.error(ex, 401, "Wrong password. Please try again.");
            return;
        }
        startSession(ex, email);
        Util.json(ex, 200, "{\"ok\":true,\"name\":" + Util.quote(user[1]) + ",\"email\":" + Util.quote(email) + "}");
    }

    static void logout(HttpExchange ex) throws IOException {
        String token = Util.cookie(ex, "bs_session");
        if (token != null) SESSIONS.remove(token);
        ex.getResponseHeaders().add("Set-Cookie", "bs_session=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax");
        Util.json(ex, 200, "{\"ok\":true}");
    }

    static void me(HttpExchange ex) throws IOException {
        String email = currentEmail(ex);
        if (email == null) { Util.error(ex, 401, "Not logged in."); return; }
        String[] user = findUser(email);
        if (user == null) { Util.error(ex, 401, "Not logged in."); return; }
        Util.json(ex, 200, "{\"ok\":true,\"name\":" + Util.quote(user[1]) + ",\"email\":" + Util.quote(email) + "}");
    }

     /** Step 1 of "forgot password": make a 6-digit code and email it to the user. */
    static void forgot(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { Util.error(ex, 405, "Use POST"); return; }
        Map<String, String> in = Util.parse(Util.readBody(ex));
        String email = clean(in.get("email")).toLowerCase();
        String[] user = findUser(email);
        if (user == null) { Util.error(ex, 404, "No account found for this email. Please click Sign Up first."); return; }

        String code = String.format("%06d", Util.RANDOM.nextInt(1_000_000));
        long expiry = System.currentTimeMillis() + 10 * 60 * 1000;
        RESETS.put(email, new String[]{code, String.valueOf(expiry), "0"});

        String name = user[1];
        // send the email in the background so the page does not have to wait
        new Thread(() -> {
            String problem = Mailer.send(email, Mailer.resetSubject(), Mailer.resetHtml(name, code));
            if (problem == null) {
                System.out.println("Reset code emailed to " + email);
            } else {
                System.out.println();
                System.out.println("Could not send the email: " + problem);
                System.out.println("=======================================================");
                System.out.println("  PASSWORD RESET CODE for " + email + " :  " + code);
                System.out.println("  (valid for 10 minutes)");
                System.out.println("=======================================================");
                System.out.println();
            }
        }).start();

        Util.json(ex, 200, "{\"ok\":true,\"emailed\":true}");
    }
    /** Step 2 of "forgot password": check the code and save the new password. */
    static void reset(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { Util.error(ex, 405, "Use POST"); return; }
        Map<String, String> in = Util.parse(Util.readBody(ex));
        String email = clean(in.get("email")).toLowerCase();
        String code = clean(in.get("code"));
        String password = in.getOrDefault("password", "");

        if (password.length() < 6) { Util.error(ex, 400, "Password must have at least 6 characters."); return; }
        String[] r = RESETS.get(email);
        if (r == null || System.currentTimeMillis() > Long.parseLong(r[1])) {
            RESETS.remove(email);
            Util.error(ex, 400, "This code has expired. Please ask for a new code.");
            return;
        }
        int tries = Integer.parseInt(r[2]) + 1;
        if (tries > 5) {
            RESETS.remove(email);
            Util.error(ex, 400, "Too many wrong tries. Please ask for a new code.");
            return;
        }
        r[2] = String.valueOf(tries);
        if (!MessageDigest.isEqual(code.getBytes(StandardCharsets.UTF_8), r[0].getBytes(StandardCharsets.UTF_8))) {
            Util.error(ex, 400, "Wrong code. Please check the Java terminal and try again.");
            return;
        }

        String salt = Util.newSalt();
        changePassword(email, salt, Util.hashPassword(password, salt));
        RESETS.remove(email);
        SESSIONS.values().removeIf(e -> e.equals(email));     // log out old sessions
        Util.json(ex, 200, "{\"ok\":true}");
    }

    // ---------- helpers other files can use ----------

    /** Returns the email of the logged-in user, or null if nobody is logged in. */
    static String currentEmail(HttpExchange ex) {
        String token = Util.cookie(ex, "bs_session");
        return token == null ? null : SESSIONS.get(token);
    }

    static void startSession(HttpExchange ex, String email) {
        String token = Util.newToken();
        SESSIONS.put(token, email);
        ex.getResponseHeaders().add("Set-Cookie",
                "bs_session=" + token + "; Path=/; Max-Age=2592000; HttpOnly; SameSite=Lax");
    }

    static String clean(String s) {
        return s == null ? "" : s.replace("\t", " ").replace("\n", " ").replace("\r", " ").trim();
    }
}