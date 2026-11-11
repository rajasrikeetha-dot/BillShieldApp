import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class Server {

    static final int PORT = 8080;
    static final Path WEB = Path.of("web").toAbsolutePath().normalize();

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);

        // 1) A small test API
        server.createContext("/api/ping", ex ->
            send(ex, 200, "application/json",
                 "{\"ok\":true,\"message\":\"Bill Shield server is running\"}".getBytes(StandardCharsets.UTF_8)));

        // 2) Sign up / login / logout
        AuthApi.register(server);
        Reminders.register(server);

        // 3) Everything else = website files from the "web" folder
        server.createContext("/", Server::serveFile);

        server.start();
        System.out.println("Bill Shield server started.");
        System.out.println("Open this in your browser:  http://localhost:" + PORT);
        System.out.println("To stop the server press Ctrl + C");
    }

    static void serveFile(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html";

        Path file = WEB.resolve(path.substring(1)).normalize();
        if (!file.startsWith(WEB) || !Files.isRegularFile(file)) {
            send(ex, 404, "text/plain; charset=utf-8", "Page not found".getBytes(StandardCharsets.UTF_8));
            return;
        }
        send(ex, 200, typeOf(file.toString()), Files.readAllBytes(file));
    }

    static String typeOf(String name) {
        name = name.toLowerCase();
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".css"))  return "text/css; charset=utf-8";
        if (name.endsWith(".js"))   return "application/javascript; charset=utf-8";
        if (name.endsWith(".json")) return "application/json; charset=utf-8";
        if (name.endsWith(".svg"))  return "image/svg+xml";
        if (name.endsWith(".png"))  return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
    }

    static void send(HttpExchange ex, int code, String type, byte[] body) throws IOException {
        Util.send(ex, code, type, body);
    }
}