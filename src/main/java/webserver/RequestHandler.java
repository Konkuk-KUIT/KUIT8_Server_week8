package webserver;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import db.MemoryUserRepository;
import http.util.HttpRequestUtils;
import model.User;
import java.util.Map;

public class RequestHandler implements Runnable {
    Socket connection;
    private static final Logger log = Logger.getLogger(RequestHandler.class.getName());

    public RequestHandler(Socket connection) {
        this.connection = connection;
    }

    @Override
    public void run() {
        log.log(Level.INFO, "New Client Connect! Connected IP : "
                + connection.getInetAddress() + ", Port : " + connection.getPort());

        try (InputStream in = connection.getInputStream();
             OutputStream out = connection.getOutputStream()) {
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            DataOutputStream dos = new DataOutputStream(out);

            String requestLine = br.readLine();
            if (requestLine == null) {
                return;
            }

            String[] parts = requestLine.split(" ", 3);
            if (parts.length != 3 || !parts[0].equals("GET")) {
                return;
            }

            String path = parts[1];
            if (path.startsWith("/user/signup?")) {
                String query = path.substring(path.indexOf('?') + 1);

                Map<String, String> params;
                try {
                    params = HttpRequestUtils.parseQueryParameter(query);
                } catch (IllegalArgumentException e) {
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
                return;
            }
            Path file;

            switch (path) {
                case "/", "/index.html" -> file = Path.of("webapp/index.html");
                case "/user/form.html" -> file = Path.of("webapp/user/form.html");
                case "/user/login.html" -> file = Path.of("webapp/user/login.html");
                case "/css/styles.css" -> file = Path.of("webapp/css/styles.css");
                default -> {
                    return;
                }
            }

            byte[] body = Files.readAllBytes(file);
            String contentType = path.equals("/css/styles.css") ? "text/css" : "text/html";

            response200Header(dos, body.length, contentType);
            responseBody(dos, body);
        } catch (IOException e) {
            log.log(Level.SEVERE, e.getMessage());
        }
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