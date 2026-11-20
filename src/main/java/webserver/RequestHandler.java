package webserver;

import db.MemoryUserRepository;
import http.util.HttpRequestUtils;
import http.util.IOUtils;
import model.User;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RequestHandler implements Runnable {
    private static final Logger log = Logger.getLogger(RequestHandler.class.getName());
    private final Socket connection;

    public RequestHandler(Socket connection) {
        this.connection = connection;
    }

    @Override
    public void run() {
        log.log(Level.INFO, "New Client Connect! Connected IP : "
                + connection.getInetAddress() + ", Port : " + connection.getPort());

        try (Socket socket = connection;
             BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

            String requestLine = br.readLine();
            if (requestLine == null) {
                return;
            }

            String[] parts = requestLine.split(" ", 3);
            if (parts.length != 3) {
                return;
            }

            String method = parts[0];
            String path = parts[1];

            if (method.equals("POST") && path.equals("/user/signup")) {
                signup(br, dos);
                return;
            }

            if (method.equals("POST") && path.equals("/user/login")) {
                login(br, dos);
                return;
            }

            if (!method.equals("GET")) {
                return;
            }

            if ((path.equals("/user/userList") || path.equals("/user/list.html")) && !isLoggedIn(br)) {
                dos.writeBytes("HTTP/1.1 302 Found\r\n");
                dos.writeBytes("Location: /user/login.html\r\n");
                dos.writeBytes("Content-Length: 0\r\n");
                dos.writeBytes("Connection: close\r\n");
                dos.writeBytes("\r\n");
                dos.flush();
                return;
            }

            Path file = switch (path) {
                case "/", "/index.html" -> Path.of("webapp/index.html");
                case "/user/form.html" -> Path.of("webapp/user/form.html");
                case "/user/login.html" -> Path.of("webapp/user/login.html");
                case "/user/login_failed.html" -> Path.of("webapp/user/login_failed.html");
                case "/user/userList", "/user/list.html" -> Path.of("webapp/user/list.html");
                case "/css/styles.css" -> Path.of("webapp/css/styles.css");
                default -> null;
            };

            if (file == null) {
                return;
            }

            byte[] body = Files.readAllBytes(file);
            String contentType = path.equals("/css/styles.css") ? "text/css" : "text/html";
            response200Header(dos, body.length, contentType);
            responseBody(dos, body);
        } catch (IOException e) {
            log.log(Level.SEVERE, e.getMessage());
        }
    }

    private void signup(BufferedReader br, DataOutputStream dos) throws IOException {
        Map<String, String> params = readForm(br);
        if (params == null || !params.keySet().containsAll(List.of("userId", "password", "name", "email"))) {
            return;
        }

        User user = new User(
                params.get("userId"),
                params.get("password"),
                params.get("name"),
                params.get("email")
        );
        MemoryUserRepository.getInstance().addUser(user);

        dos.writeBytes("HTTP/1.1 302 Found\r\n");
        dos.writeBytes("Location: /index.html\r\n");
        dos.writeBytes("Content-Length: 0\r\n");
        dos.writeBytes("Connection: close\r\n");
        dos.writeBytes("\r\n");
        dos.flush();
    }

    private Map<String, String> readForm(BufferedReader br) throws IOException {
        int contentLength = -1;
        String header;

        try {
            while ((header = br.readLine()) != null && !header.isEmpty()) {
                String[] fields = header.split(":", 2);
                if (fields.length == 2 && fields[0].equalsIgnoreCase("Content-Length")) {
                    contentLength = Integer.parseInt(fields[1].trim());
                }
            }

            if (header == null || contentLength <= 0) {
                return null;
            }

            String formBody = IOUtils.readData(br, contentLength);
            return HttpRequestUtils.parseQueryParameter(formBody);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void login(BufferedReader br, DataOutputStream dos) throws IOException {
        Map<String, String> params = readForm(br);
        if (params == null) {
            return;
        }

        String userId = params.get("userId");
        String password = params.get("password");
        User user = userId == null ? null : MemoryUserRepository.getInstance().findUserById(userId);
        boolean success = user != null && user.getPassword().equals(password);

        dos.writeBytes("HTTP/1.1 302 Found\r\n");
        if (success) {
            dos.writeBytes("Location: /index.html\r\n");
            dos.writeBytes("Set-Cookie: logined=true; Path=/\r\n");
        } else {
            dos.writeBytes("Location: /user/login_failed.html\r\n");
        }
        dos.writeBytes("Content-Length: 0\r\n");
        dos.writeBytes("Connection: close\r\n");
        dos.writeBytes("\r\n");
        dos.flush();
    }

    private boolean isLoggedIn(BufferedReader br) throws IOException {
        String header;
        while ((header = br.readLine()) != null && !header.isEmpty()) {
            String[] fields = header.split(":", 2);
            if (fields.length == 2 && fields[0].equalsIgnoreCase("Cookie")) {
                for (String cookie : fields[1].split(";")) {
                    if (cookie.trim().equals("logined=true")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void response200Header(
            DataOutputStream dos, int lengthOfBodyContent, String contentType
    ) throws IOException {
        dos.writeBytes("HTTP/1.1 200 OK\r\n");
        dos.writeBytes("Content-Type: " + contentType + "; charset=UTF-8\r\n");
        dos.writeBytes("Content-Length: " + lengthOfBodyContent + "\r\n");
        dos.writeBytes("Connection: close\r\n");
        dos.writeBytes("\r\n");
    }

    private void responseBody(DataOutputStream dos, byte[] body) throws IOException {
        dos.write(body);
        dos.flush();
    }
}