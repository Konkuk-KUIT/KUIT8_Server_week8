package webserver;

import db.MemoryUserRepository;
import http.util.HttpRequestUtils;
import http.util.IOUtils;
import model.User;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RequestHandler implements Runnable {
    private final Socket connection;
    private static final Logger log = Logger.getLogger(RequestHandler.class.getName());

    public RequestHandler(Socket connection) {
        this.connection = connection;
    }

    @Override
    public void run() {
        log.info("New Client Connect! Connected IP : "
                + connection.getInetAddress()
                + ", Port : " + connection.getPort());

        try (Socket socket = connection;
             InputStream in = socket.getInputStream();
             OutputStream out = socket.getOutputStream()) {

            BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            DataOutputStream dos = new DataOutputStream(out);

            // 1. 요청 시작 줄 읽기
            String requestLine = br.readLine();
            if (requestLine == null || requestLine.isBlank()) {
                return;
            }

            String[] requestParts = requestLine.split(" ", 3);
            if (requestParts.length != 3) {
                responseErrorHeader(dos, "400 Bad Request");
                return;
            }

            String method = requestParts[0];
            String requestUri = requestParts[1];

            // 2. 요청 경로와 쿼리 문자열 분리
            int questionMark = requestUri.indexOf('?');

            String path = questionMark < 0
                    ? requestUri
                    : requestUri.substring(0, questionMark);

            String queryString = questionMark < 0
                    ? ""
                    : requestUri.substring(questionMark + 1);

            // 3. HTTP 헤더 읽기
            int contentLength = -1;
            String cookieHeader = null;  // 요구사항 6 쿠키 저장 변수
            String headerLine;

            while ((headerLine = br.readLine()) != null && !headerLine.isEmpty()) {

                int colonIndex = headerLine.indexOf(':');
                if (colonIndex < 0) continue;

                String headerName = headerLine.substring(0, colonIndex).trim();
                String headerValue = headerLine.substring(colonIndex + 1).trim();

                if (headerName.equalsIgnoreCase("Content-Length")) {
                    contentLength = Integer.parseInt(headerValue);

                } else if (headerName.equalsIgnoreCase("Cookie")) {
                    cookieHeader = headerValue;
                }
            }

            // 헤더가 끝나기 전에 연결이 종료된 경우
            if (headerLine == null) return;

            // 4. 회원가입 요청 처리
            if (path.equals("/user/signup")) {
                Map<String, String> params;

                if (method.equals("GET")) {
                    // GET: URL의 쿼리 문자열 사용
                    params = HttpRequestUtils.parseQueryParameter(queryString);

                } else if (method.equals("POST")) {
                    // POST: 요청 본문 사용
                    if (contentLength < 0) {
                        responseErrorHeader(dos, "400 Bad Request");
                        return;
                    }

                    String body = IOUtils.readData(br, contentLength);

                    params = HttpRequestUtils.parseQueryParameter(body);

                } else {
                    responseErrorHeader(dos, "405 Method Not Allowed");
                    return;
                }

                signUp(dos, params);
                return;
            }

            // 요구사항 5 - 로그인 요청 처리
            if (path.equals("/user/login")) {

                // 로그인은 POST 요청만 허용
                if (!method.equals("POST")) {
                    responseErrorHeader(dos, "405 Method Not Allowed");
                    return;
                }

                if (contentLength < 0) {
                    responseErrorHeader(dos, "400 Bad Request");
                    return;
                }

                // 기존 회원가입에서 사용했던 본문 읽기 방식 재사용
                String body = IOUtils.readData(br, contentLength);

                Map<String, String> params =
                        HttpRequestUtils.parseQueryParameter(body);

                login(dos, params);
                return;
            }

            // 요구사항 6 - 사용자 목록 접근 제어
            if (path.equals("/user/userList") || path.equals("/user/list.html")) {
                if (!method.equals("GET")) {
                    responseErrorHeader(dos, "405 Method Not Allowed");
                    return;
                }

                if (hasLoginCookie(cookieHeader)) {
                    // 로그인 성공 상태: 목록 HTML 반환
                    serveFile(dos, "/user/list.html");

                } else {
                    // 비로그인 상태: 로그인 페이지로 이동
                    response302Header(dos, "/user/login.html");
                }

                return;
            }

            // 5. HTML 요청 처리
            if (method.equals("GET")) {
                serveFile(dos, path);
            } else {
                responseErrorHeader(dos, "405 Method Not Allowed");
            }

        } catch (IOException | IllegalArgumentException e) {
            log.log(Level.WARNING, "요청 처리 실패", e);
        }
    }

    // 요구사항 6번 - 쿠키 검사 메서드
    static boolean hasLoginCookie(String cookieHeader) {

        if (cookieHeader == null) {
            return false;
        }

        // 여러 쿠키는 세미콜론(;)으로 구분됨
        String[] cookies = cookieHeader.split(";");

        for (String cookie : cookies) {
            String[] pair = cookie.trim().split("=", 2);

            if (pair.length == 2
                    && pair[0].trim().equals("logined")
                    && pair[1].trim().equals("true")) {
                return true;
            }
        }

        return false;
    }

    // 회원가입 처리
    private void signUp(DataOutputStream dos, Map<String, String> params) throws IOException {

        String userId = params.get("userId");
        String password = params.get("password");
        String name = params.get("name");
        String email = params.get("email");

        // 필수 파라미터 누락 확인
        if (userId == null || userId.isBlank()
                || password == null || name == null || email == null) {
            responseErrorHeader(dos, "400 Bad Request");
            return;
        }

        User user = new User(userId, password, name, email);
        MemoryUserRepository.getInstance().addUser(user);

        log.info("회원가입 성공: " + userId);

        // 회원가입 성공 시 메인 페이지로 이동
        response302Header(dos, "/index.html");
    }

    private void login(DataOutputStream dos, Map<String, String> params) throws IOException {

        String userId = params.get("userId");
        String password = params.get("password");

        // ID로 기존 회원 조회
        User user = null;

        if (userId != null && !userId.isBlank()) {
            user = MemoryUserRepository.getInstance()
                    .findUserById(userId);
        }

        // 회원이 존재하고 비밀번호가 일치하는지 검사
        if (user != null && user.getPassword().equals(password)) {

            // 로그인 성공: 쿠키 발급 후 메인으로 이동
            response302Header(dos, "/index.html", true);

        } else {

            // 로그인 실패: 실패 화면으로 이동
            response302Header(dos, "/user/login_failed.html", false);
        }
    }

    // HTML 파일 읽어서 전송
    // HTML, CSS, JavaScript 파일 전송
    private void serveFile(DataOutputStream dos, String requestPath) throws IOException {

        String filePath = requestPath.equals("/")
                ? "/index.html"
                : requestPath;

        Path webRoot = Paths.get("webapp").toAbsolutePath().normalize();

        Path file = webRoot.resolve(filePath.startsWith("/")
                        ? filePath.substring(1)
                        : filePath).normalize();

        // 요청 파일의 확장자로 Content-Type 결정
        String contentType;

        if (filePath.endsWith(".html")) {
            contentType = "text/html; charset=UTF-8";

        } else if (filePath.endsWith(".css")) {
            contentType = "text/css; charset=UTF-8";

        } else if (filePath.endsWith(".js")) {
            contentType = "text/javascript; charset=UTF-8";

        } else {
            responseErrorHeader(dos, "404 Not Found");
            return;
        }

        // webapp 밖으로 벗어나지 않고 실제 파일이 있는지 확인
        if (!file.startsWith(webRoot) || !Files.isRegularFile(file)) {
            responseErrorHeader(dos, "404 Not Found");
            return;
        }

        byte[] body = Files.readAllBytes(file);

        response200Header(dos, body.length, contentType);
        responseBody(dos, body);
    }

    // 200 OK 응답 헤더
    // step7 - 이제 파일 종류에 따라 Content-Type을 다르게 보내야 한다
    private void response200Header(DataOutputStream dos,
                                   int lengthOfBodyContent,
                                   String contentType) throws IOException {

        dos.writeBytes("HTTP/1.1 200 OK\r\n");
        dos.writeBytes("Content-Type: " + contentType + "\r\n");
        dos.writeBytes("Content-Length: "
                + lengthOfBodyContent + "\r\n");
        dos.writeBytes("Connection: close\r\n");
        dos.writeBytes("\r\n");
    }

    // 302 Redirect 응답 헤더 - 오버로딩
    // 일반 리다이렉트: 기존 회원가입에서도 사용
    private void response302Header(DataOutputStream dos,
                                   String location) throws IOException {

        response302Header(dos, location, false);
    }

    // 쿠키 발급 여부까지 지정할 수 있는 리다이렉트
    private void response302Header(DataOutputStream dos,
                                   String location,
                                   boolean setLoginCookie) throws IOException {

        dos.writeBytes("HTTP/1.1 302 Found\r\n");
        dos.writeBytes("Location: " + location + "\r\n");

        if (setLoginCookie) {
            dos.writeBytes("Set-Cookie: logined=true; Path=/\r\n");
        }

        dos.writeBytes("Content-Length: 0\r\n");
        dos.writeBytes("Connection: close\r\n");
        dos.writeBytes("\r\n");
        dos.flush();
    }

    // 오류 응답 헤더
    private void responseErrorHeader(DataOutputStream dos, String status) throws IOException {
        dos.writeBytes("HTTP/1.1 " + status + "\r\n");
        dos.writeBytes("Content-Length: 0\r\n");
        dos.writeBytes("Connection: close\r\n");
        dos.writeBytes("\r\n");
        dos.flush();
    }

    // HTTP 응답 본문 전송
    private void responseBody(DataOutputStream dos, byte[] body) throws IOException {
        dos.write(body);
        dos.flush();
    }
}
