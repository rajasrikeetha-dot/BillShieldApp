import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import javax.net.ssl.SSLSocketFactory;

/** Sends emails through Gmail (SMTP). Your Gmail address and App Password are read from the file mail.txt. */
public class Mailer {

    static final Path CONFIG = Path.of("mail.txt");
    static final String HOST = System.getProperty("mail.host", "smtp.gmail.com");
    static final int PORT = Integer.getInteger("mail.port", 465);
    static final boolean SSL = !"false".equals(System.getProperty("mail.ssl"));

    /** Sends one HTML email. Returns null if it worked, or a short error text if it did not. */
    static String send(String to, String subject, String html) {
        String a = trySend(to, subject, html, 465, false);
        if (a == null) return null;
        String b = trySend(to, subject, html, 587, true);
        if (b == null) return null;
        return "port 465: " + a + " | port 587: " + b;
    }

    static String trySend(String to, String subject, String html, int port, boolean startTls) {
        try {
            if (!Files.exists(CONFIG)) return "mail.txt was not found in the BillShieldApp folder.";
            List<String> lines = Files.readAllLines(CONFIG, StandardCharsets.UTF_8);
            if (lines.size() < 2) return "mail.txt must have 2 lines: your Gmail address, then your App Password.";
            String user = lines.get(0).trim();
            String pass = lines.get(1).replace(" ", "").trim();
            if (user.isEmpty() || pass.isEmpty()) return "mail.txt has an empty line.";
            if (to.contains("\r") || to.contains("\n")) return "Bad email address.";

            Socket raw = new Socket();
            raw.connect(new InetSocketAddress(HOST, port), 15000);
            raw.setSoTimeout(45000);
            Socket s = (SSL && !startTls) ? ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(raw, HOST, port, true) : raw;

            try (Socket sock = s) {
                BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8));
                OutputStream out = sock.getOutputStream();

                reply(in, "220");
                cmd(out, "EHLO localhost");               reply(in, "250");
                if (startTls) {
                    cmd(out, "STARTTLS");                 reply(in, "220");
                    Socket tls = ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(sock, HOST, port, true);
                    in = new BufferedReader(new InputStreamReader(tls.getInputStream(), StandardCharsets.UTF_8));
                    out = tls.getOutputStream();
                    cmd(out, "EHLO localhost");           reply(in, "250");
                }
                cmd(out, "AUTH LOGIN");                   reply(in, "334");
                cmd(out, b64(user));                      reply(in, "334");
                cmd(out, b64(pass));                      reply(in, "235");
                cmd(out, "MAIL FROM:<" + user + ">");     reply(in, "250");
                cmd(out, "RCPT TO:<" + to + ">");         reply(in, "250");
                cmd(out, "DATA");                         reply(in, "354");

                String date = ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME);
                String body = Base64.getMimeEncoder(76, "\r\n".getBytes()).encodeToString(html.getBytes(StandardCharsets.UTF_8));
                String message =
                        "From: Bill Shield <" + user + ">\r\n" +
                        "To: <" + to + ">\r\n" +
                        "Subject: =?UTF-8?B?" + b64(subject) + "?=\r\n" +
                        "Date: " + date + "\r\n" +
                        "MIME-Version: 1.0\r\n" +
                        "Content-Type: text/html; charset=UTF-8\r\n" +
                        "Content-Transfer-Encoding: base64\r\n" +
                        "\r\n" + body + "\r\n.";
                cmd(out, message);                        reply(in, "250");
                cmd(out, "QUIT");
            }
            return null;
        } catch (Exception e) {
            return e.getMessage() == null ? e.toString() : e.getMessage();
        }
    }

    static String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    static void cmd(OutputStream out, String text) throws IOException {
        out.write((text + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    /** Reads the server's answer and checks it starts with the code we expect. */
    static void reply(BufferedReader in, String expected) throws IOException {
        String line;
        do {
            line = in.readLine();
            if (line == null) throw new IOException("The mail server closed the connection.");
        } while (line.length() >= 4 && line.charAt(3) == '-');
        if (!line.startsWith(expected)) throw new IOException("Gmail said: " + line);
    }

    // ---------- the email for "forgot password" ----------

    static String resetSubject() { return "Your Bill Shield password reset code"; }

    static String resetHtml(String name, String code) {
        return "<div style=\"font-family:Arial,Helvetica,sans-serif;background:#eef3ff;padding:24px\">"
             + "<div style=\"max-width:460px;margin:auto;background:#fff;border-radius:18px;overflow:hidden;box-shadow:0 8px 24px rgba(37,99,235,.18)\">"
             + "<div style=\"background:linear-gradient(120deg,#2563eb,#7c3aed);color:#fff;padding:22px 26px;font-size:22px;font-weight:bold\">Bill Shield</div>"
             + "<div style=\"padding:26px;color:#0b1b4d\">"
             + "<p style=\"font-size:16px;margin:0 0 12px\">Hi " + escape(name) + ",</p>"
             + "<p style=\"font-size:15px;margin:0 0 18px;color:#334155\">Use this code to choose a new password:</p>"
             + "<div style=\"font-size:34px;letter-spacing:8px;font-weight:bold;text-align:center;background:#f0f4ff;border-radius:12px;padding:16px;color:#2563eb\">" + code + "</div>"
             + "<p style=\"font-size:13px;color:#64748b;margin:18px 0 0\">This code works for 10 minutes. If you did not ask for it, you can ignore this email.</p>"
             + "</div></div></div>";
    }

    static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}