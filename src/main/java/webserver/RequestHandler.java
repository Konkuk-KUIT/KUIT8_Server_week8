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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RequestHandler implements Runnable {
    private final Socket connection;

    private static final Logger log =
            Logger.getLogger(RequestHandler.class.getName());

    public RequestHandler(Socket connection) {
        this.connection = connection;
    }

    @Override
    public void run() {
        // 요청 처리가 끝나거나 예외가 발생하면 연결과 스트림을 닫습니다.
        try (Socket socket = connection;
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(
                             socket.getInputStream(),
                             StandardCharsets.UTF_8
                     )
             );
             DataOutputStream output =
                     new DataOutputStream(socket.getOutputStream())) {

            // 1. 요청 시작 줄을 읽습니다.
            String requestLine = reader.readLine();

            if (requestLine == null || requestLine.isEmpty()) {
                return;
            }

            String[] startLine = requestLine.split(" ", 3);

            if (startLine.length != 3) {
                throw new IOException("잘못된 요청 시작 줄입니다.");
            }

            String method = startLine[0];

            // 2. 요청 주소를 경로와 쿼리 문자열로 나눕니다.
            String[] requestTarget = startLine[1].split("\\?", 2);
            String path = requestTarget[0];
            String queryString = "";

            if (requestTarget.length == 2) {
                queryString = requestTarget[1];
            }

            // 3. 빈 줄이 나올 때까지 헤더를 읽습니다.
            // 헤더 이름은 대소문자를 구분하지 않습니다.
            Map<String, String> headers =
                    new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

            String line;

            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    break;
                }

                String[] header = line.split(":", 2);

                if (header.length != 2) {
                    throw new IOException("잘못된 요청 헤더입니다.");
                }

                headers.put(header[0].trim(), header[1].trim());
            }

            if (line == null) {
                return;
            }

            // 4. POST 요청이면 본문을 읽습니다.
            // 헤더를 읽었던 reader를 그대로 사용합니다.
            String requestBody = "";

            if (method.equals("POST")) {
                int contentLength = Integer.parseInt(
                        headers.getOrDefault("Content-Length", "0")
                );

                requestBody = IOUtils.readData(reader, contentLength);
            }

            // 5. GET 또는 POST 회원가입을 처리합니다.
            if (path.equals("/user/signup")
                    && (method.equals("GET") || method.equals("POST"))) {

                String formData = method.equals("GET")
                        ? queryString
                        : requestBody;

                Map<String, String> parameters =
                        HttpRequestUtils.parseQueryParameter(formData);

                String userId = parameters.get("userId");

                if (userId == null) {
                    throw new IllegalArgumentException(
                            "회원가입 요청에 userId가 없습니다."
                    );
                }

                User user = new User(
                        userId,
                        parameters.get("password"),
                        parameters.get("name"),
                        parameters.get("email")
                );

                MemoryUserRepository.getInstance().addUser(user);

                redirect(output, "/index.html", false);
                return;
            }

            // 6. POST 로그인을 처리합니다.
            if (method.equals("POST") && path.equals("/user/login")) {
                Map<String, String> parameters =
                        HttpRequestUtils.parseQueryParameter(requestBody);

                String userId = parameters.get("userId");
                String password = parameters.get("password");

                User savedUser = null;

                if (userId != null) {
                    savedUser = MemoryUserRepository.getInstance()
                            .findUserById(userId);
                }

                boolean loginSuccess =
                        savedUser != null
                                && password != null
                                && password.equals(savedUser.getPassword());

                if (loginSuccess) {
                    redirect(output, "/index.html", true);
                } else {
                    redirect(output, "/user/login_failed.html", false);
                }

                return;
            }

            // 7. 사용자 목록에 접근할 때 로그인 여부를 검사합니다.
            // /user/list.html 직접 접근에도 적용합니다.
            if (method.equals("GET")
                    && (path.equals("/user/userList")
                    || path.equals("/user/list.html"))) {

                if (!isLoggedIn(headers.get("Cookie"))) {
                    redirect(output, "/user/login.html", false);
                    return;
                }

                sendFile(output, "/user/list.html");
                return;
            }

            // 8. GET 요청에 HTML 또는 CSS 파일을 응답합니다.
            if (method.equals("GET")) {
                if (path.equals("/")) {
                    path = "/index.html";
                }

                sendFile(output, path);
                return;
            }

            sendResponse(
                    output,
                    "404 Not Found",
                    "text/plain; charset=utf-8",
                    "Not Found".getBytes(StandardCharsets.UTF_8)
            );

        } catch (IOException | IllegalArgumentException e) {
            log.log(Level.WARNING, "요청 처리를 종료합니다.", e);
        }
    }

    // 여러 쿠키 중 logined의 값이 정확히 true인지 확인합니다.
    private boolean isLoggedIn(String cookieHeader) {
        if (cookieHeader == null) {
            return false;
        }

        for (String cookie : cookieHeader.split(";")) {
            String[] pair = cookie.trim().split("=", 2);

            if (pair.length == 2
                    && pair[0].equals("logined")
                    && pair[1].equals("true")) {
                return true;
            }
        }

        return false;
    }

    // webapp 폴더에서 요청한 HTML 또는 CSS 파일을 읽습니다.
    private void sendFile(DataOutputStream output, String path)
            throws IOException {

        if (!path.startsWith("/")) {
            throw new IOException("잘못된 요청 경로입니다.");
        }

        Path webRoot = Path.of("webapp").toAbsolutePath().normalize();
        Path file = webRoot.resolve(path.substring(1)).normalize();

        boolean supportedFile =
                path.endsWith(".html") || path.endsWith(".css");

        if (!file.startsWith(webRoot)
                || !supportedFile
                || !Files.isRegularFile(file)) {

            sendResponse(
                    output,
                    "404 Not Found",
                    "text/plain; charset=utf-8",
                    "Not Found".getBytes(StandardCharsets.UTF_8)
            );
            return;
        }

        String contentType = path.endsWith(".css")
                ? "text/css; charset=utf-8"
                : "text/html; charset=utf-8";

        byte[] body = Files.readAllBytes(file);

        sendResponse(output, "200 OK", contentType, body);
    }

    // 상태 줄 → 헤더 → 빈 줄 → 본문 순서로 응답합니다.
    private void sendResponse(
            DataOutputStream output,
            String status,
            String contentType,
            byte[] body
    ) throws IOException {

        output.writeBytes("HTTP/1.1 " + status + "\r\n");
        output.writeBytes("Content-Type: " + contentType + "\r\n");
        output.writeBytes("Content-Length: " + body.length + "\r\n");
        output.writeBytes("Connection: close\r\n");
        output.writeBytes("\r\n");
        output.write(body);
        output.flush();
    }

    // 302 응답으로 브라우저가 이동할 주소를 전달합니다.
    private void redirect(
            DataOutputStream output,
            String location,
            boolean loginSuccess
    ) throws IOException {

        output.writeBytes("HTTP/1.1 302 Found\r\n");
        output.writeBytes("Location: " + location + "\r\n");

        if (loginSuccess) {
            output.writeBytes("Set-Cookie: logined=true; Path=/\r\n");
        }

        output.writeBytes("Content-Length: 0\r\n");
        output.writeBytes("Connection: close\r\n");
        output.writeBytes("\r\n");
        output.flush();
    }
}