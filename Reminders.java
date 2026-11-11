import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Sends reminder emails at 30 days, 1 week and 2 days before expiry. */
public class Reminders {

    static final Path DIR  = Path.of("C:\\BillShieldApp", "data");
    static final Path SENT = DIR.resolve("sent.txt");
    static final String[][] FIELDS = {
        {"expiry", "Warranty"}, {"freeService", "Free service"}, {"insurance", "Insurance"}, {"puc", "PUC"}
    };

    static void register(HttpServer server) {
        server.createContext("/api/sync", Reminders::sync);
        Thread t = new Thread(() -> {
            while (true) {
                try { checkAll(); } catch (Exception e) { System.out.println("Reminder check problem: " + e); }
                try { Thread.sleep(60L * 60 * 1000); } catch (InterruptedException e) { return; }
            }
        });
        t.setDaemon(true);
        t.start();
    }

    /** The website sends the logged-in user's items here every time they change. */
    static void sync(HttpExchange ex) throws IOException {
        String email = AuthApi.currentEmail(ex);
        if (email == null) { Util.error(ex, 401, "Not logged in."); return; }
        String body = Util.readBody(ex);
        Files.createDirectories(DIR);
        Files.writeString(fileFor(email), body, StandardCharsets.UTF_8);
        Util.json(ex, 200, "{\"ok\":true}");
        new Thread(() -> { try { check(email); } catch (Exception e) { System.out.println("Reminder problem: " + e); } }).start();
    }

    static Path fileFor(String email) {
        return DIR.resolve("items_" + email.toLowerCase().replaceAll("[^a-z0-9]", "_") + ".json");
    }

    static void checkAll() throws IOException {
        if (!Files.isDirectory(DIR)) return;
        List<String> lines = Files.exists(AuthApi.USERS_FILE) ? Files.readAllLines(AuthApi.USERS_FILE) : new ArrayList<>();
        for (String line : lines) {
            String[] p = line.split("\t");
            if (p.length > 0 && !p[0].isBlank()) check(p[0]);
        }
    }

    static synchronized void check(String email) throws IOException {
        Path f = fileFor(email);
        if (!Files.exists(f)) return;
        String json = Files.readString(f, StandardCharsets.UTF_8);
        List<String> sent = Files.exists(SENT) ? Files.readAllLines(SENT) : new ArrayList<>();
        List<String> due = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        LocalDate today = LocalDate.now();

        Matcher obj = Pattern.compile("\\{[^{}]*\\}").matcher(json);
        while (obj.find()) {
            String o = obj.group();
            String name = get(o, "name");
            if (name == null) continue;
            for (String[] fd : FIELDS) {
                String v = get(o, fd[0]);
                if (v == null || v.isBlank()) continue;
                long d;
                try { d = ChronoUnit.DAYS.between(today, LocalDate.parse(v)); } catch (Exception e) { continue; }
                if (d > 30) continue;
                int tier = d <= 2 ? 2 : d <= 7 ? 7 : 30;
                String key = email + "|" + name + "|" + fd[0] + "|" + v + "|" + tier;
                if (sent.contains(key)) continue;
                String when = d < 0 ? "expired " + (-d) + " day(s) ago" : d == 0 ? "ends TODAY" : "ends in " + d + " day(s)";
                due.add(name + " - " + fd[1] + " " + when + " (" + v + ")");
                keys.add(key);
            }
        }
        if (due.isEmpty()) return;

        StringBuilder html = new StringBuilder("<div style=\"font-family:Arial,sans-serif\"><h2>Bill Shield reminder</h2><ul>");
        for (String s : due) html.append("<li>").append(Mailer.escape(s)).append("</li>");
        html.append("</ul><p>Please renew them in time.</p></div>");

        String problem = Mailer.send(email, "Bill Shield: " + due.size() + " item(s) need attention", html.toString());
        if (problem == null) {
            System.out.println("Reminder emailed to " + email + ": " + due);
            Files.write(SENT, keys, StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } else {
            System.out.println("Could not email reminder to " + email + ": " + problem);
            System.out.println("Items due: " + due);
        }
    }

    static String get(String obj, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"").matcher(obj);
        return m.find() ? m.group(1) : null;
    }
}