package webserver;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RequestHandler implements Runnable{
    Socket connection;
    private static final Logger log = Logger.getLogger(RequestHandler.class.getName());

    public RequestHandler(Socket connection) {
        this.connection = connection;
    }

    @Override
    public void run() {
        log.log(Level.INFO, "New Client Connect! Connected IP : " + connection.getInetAddress() + ", Port : " + connection.getPort());
        try (Socket socket = connection;
             InputStream in = socket.getInputStream(); OutputStream out = socket.getOutputStream()){
            BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            DataOutputStream dos = new DataOutputStream(out);

            // 1. 요청 첫 줄 예시: GET /index.html HTTP/1.1
            String requestLine = br.readLine();
            if (requestLine == null || requestLine.isBlank()) {
                return;
            }
            log.info("Request: " + requestLine);

            String[] requestParts = requestLine.split(" ", 3);
            if (requestParts.length != 3) {
                return;
            }
            String method = requestParts[0];
            // 쿼리 문자열(? 뒤의 값)을 제외한 요청 경로입니다.
            String path = requestParts[1].split("\\?", 2)[0];

            // 2. 빈 줄까지가 요청 헤더입니다. 이번 단계에서는 읽어서 넘깁니다.
            String headerLine;
            while ((headerLine = br.readLine()) != null && !headerLine.isEmpty()) {
                log.fine(headerLine);
            }
            if (headerLine == null) {
                return;
            }

            // 3. 첫 화면 주소 /는 /index.html로 처리합니다.
            if ("/".equals(path)) {
                path = "/index.html";
            }

            // 4. HTML 조회 요청이면 주소에 맞는 파일을 webapp에서 찾습니다.
            if ("GET".equals(method) && path.startsWith("/") && path.endsWith(".html")) {
                Path webRoot = Path.of("webapp").toAbsolutePath().normalize();
                // 맨 앞 /를 떼면 /user/form.html -> user/form.html입니다.
                Path filePath = webRoot.resolve(path.substring(1)).normalize();

                // 사용자 목록은 요구사항 6에서 로그인 검사와 함께 연결합니다.
                boolean isUserList = filePath.equals(webRoot.resolve("user/list.html"));

                // webapp 안에 실제로 있는 파일인지 확인한 뒤 내용을 보냅니다.
                if (filePath.startsWith(webRoot) && !isUserList && Files.isRegularFile(filePath)) {
                    byte[] body = Files.readAllBytes(filePath);
                    responseHeader(dos, "200 OK", body.length);
                    responseBody(dos, body);
                    return;
                }
            }

            // 아직 구현하지 않은 경로는 404(찾을 수 없음)로 응답합니다.
            byte[] body = "Not Found".getBytes(StandardCharsets.UTF_8);
            responseHeader(dos, "404 Not Found", body.length);
            responseBody(dos, body);

        } catch (IOException | IllegalArgumentException e) {
            log.log(Level.SEVERE,e.getMessage());
        }
    }

    private void responseHeader(DataOutputStream dos, String status, int lengthOfBodyContent) throws IOException {
        dos.writeBytes("HTTP/1.1 " + status + "\r\n");
        dos.writeBytes("Content-Type: text/html;charset=utf-8\r\n");
        // 문자 수가 아니라 실제 전송할 바이트 수를 알려줍니다.
        dos.writeBytes("Content-Length: " + lengthOfBodyContent + "\r\n");
        dos.writeBytes("Connection: close\r\n");
        dos.writeBytes("\r\n");
    }

    private void responseBody(DataOutputStream dos, byte[] body) throws IOException {
        dos.write(body, 0, body.length);
        dos.flush();
    }

}
