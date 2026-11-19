package webserver;

import java.io.*;
import java.net.Socket;
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
        try (InputStream in = connection.getInputStream(); OutputStream out = connection.getOutputStream()){
            BufferedReader br = new BufferedReader(new InputStreamReader(in)); //요청을 읽는 도구
            DataOutputStream dos = new DataOutputStream(out); //응답을 쓰는 도구
            
            //1. HTTP 메시지 읽기. ex) GET /index.html HTTP/1.1
            String startLine = br.readLine();
            if (startLine == null){
                return;
            }
            String[] startTokens = startLine.split(" ");
            String method = startTokens[0];
            string url = startTokens[1];
            )
            
            //2. 헤더 빈 줄이 나올 때까지 읽기
            /* 헤더 양식 예시
                Host: localhost:8080                          
                Content-Type: application/x-www-form-urlencoded
                Content-Length: 46
                Cookie: logined=true
                                                            ← 빈 줄 = 헤더 끝!
                userId=kim&password=1234&name=%EA%B9%80       ← ④ 본문 (Body)
             */
            int contentLength = 0;
            String cookie = "";
            while (true) {
                String line = br.readLine();
                if (line == null){
                    return;
                }
                if(line.isEmpty()) {
                    break;
                }
                String[] header = line.split(":", 2);
                String headerName = header[0].trim();
                String headerValue = header.length == 2 ? header[1].trim() : "";
                if (headerName.equalsIgnoreCase("Content-Length")) {
                    contentLength = Integer.parseInt(headerValue);
                }
                if (headerName.equalsIgnoreCase("Cookie")) {
                    cookie = headerValue;
                }
            }

            //3. URL을 경로와 쿼리 문자열로 나누기
            //HTTP 요청 메시지에서 데이터가 URL에 붙어 오기 때문에 이걸 떼는 작업 
            /*
             "/user/signup?userId=kim&password=1" 
             → path="/user/signup", queryString="userId=kim&password=1"
            */

            String path = url;
            String queryString = "";
            int questionMark = url.indexOf('?');
            if (questionMark != -1) {
                path = url.substring(0, questionMark);
                queryString = url.substring(questionMark + 1);
            }

            if (path.equals("/")) {           // 그냥 localhost:8080 으로 오면 메인 화면
                path = "/index.html";
            }

            MemoryUserRepository repository = MemoryUserRepository.getInstance();   //정보 저장

            //4. GET 회원가입 -> 회원가입 양식 (HTML 폼) 조회 
            if (method.equals("GET") && path.equals("/user/signup")) {
                Map<String, String> params = HttpRequestUtils.parseQueryParameter(queryString);
                User user = new User(params.get("userId"), params.get("password"),
                        params.get("name"), params.get("email"));
                repository.addUser(user);
                response302Header(dos, "/index.html");    
                return;
            }

            //5. POST 회원가입 -> 입력한 데이터로 회원 생성 저장
            if (method.equals("POST") && path.equals("/user/signup")) {
                String body = IOUtils.readData(br, contentLength); //헤더를 읽던 br을 넘겨야 함.  
                Map<String, String> params = HttpRequestUtils.parseQueryParameter(body);
                User user = new User(params.get("userId"), params.get("password"),
                        params.get("name"), params.get("email"));
                repository.addUser(user);
                response302Header(dos, "/index.html");
                return;
            }


            //6. 로그인
            if (method.equals("POST") && path.equals("/user/login")) {
                String body = IOUtils.readData(br, contentLength);
                Map<String, String> params = HttpRequestUtils.parseQueryParameter(body);
                User user = repository.findUserById(params.getOrDefault("userId", ""));

                // user가 null(없는 아이디)인지 먼저 확인해야 getPassword()에서 안 터짐.
                if (user != null && user.getPassword().equals(params.get("password"))) {
                    response302HeaderWithCookie(dos, "/index.html", "logined=true; Path=/");
                } else {
                    response302Header(dos, "/user/login_failed.html");
                }
                return;
            }

            //7. 사용자 목록
            if (path.equals("/user/userList") || path.equals("/user/list.html")) {
                if (!isLogined(cookie)) {
                    response302Header(dos, "/user/login.html");
                    return;
                }
                path = "/user/list.html"; //로그인 하면 파일 전달
            }


            //8. 위에서 처리되지 않은 요청 처리 -> 파일 돌려주기
            Path filePath = Paths.get(WEBAPP + path);
            if (!Files.isRegularFile(filePath)) {      // 파일이 없으면
                response404Header(dos);
                return;
            }
            byte[] body = Files.readAllBytes(filePath);                   // 파일을 바이트로 통째로
            response200Header(dos, body.length, getContentType(path));   // 길이 = 바이트 수!
            responseBody(dos, body);

        } catch (IOException e) {
            //제공 함수가 예외 던지면 로그만 남기고 요청 종료
            log.log(Level.SEVERE,e.getMessage());
        }
    }


    //메서드들

    // Cookie 헤더에 logined=true 가 정확히 있는지 확인
    private boolean isLogined(String cookie) {
        for (String pair : cookie.split(";")) {
            String[] keyValue = pair.trim().split("=", 2);
            if (keyValue.length == 2 && keyValue[0].equals("logined") && keyValue[1].equals("true")) {
                return true;
            }
        }
        return false;
    }

    // 파일 확장자를 보고 어떤 종류의 파일인지를 정함.
    private String getContentType(String path) {
        if (path.endsWith(".css")) {
            return "text/css";
        }
        if (path.endsWith(".js")) {
            return "application/javascript";
        }
        return "text/html;charset=utf-8";
    }

    // 200 OK: 요청 전달
    private void response200Header(DataOutputStream dos, int lengthOfBodyContent, String contentType) throws IOException {
        dos.writeBytes("HTTP/1.1 200 OK\r\n");
        dos.writeBytes("Content-Type: " + contentType + "\r\n");
        dos.writeBytes("Content-Length: " + lengthOfBodyContent + "\r\n");
        dos.writeBytes("Connection: close\r\n");
        dos.writeBytes("\r\n");                       // 빈 줄 = 헤더 끝
    }

    // 302 Found: 요청한 리소스가 일시적으로 다른 URL로 이동했으니 다른 location으로 가십쇼
    private void response302Header(DataOutputStream dos, String location) throws IOException {
        dos.writeBytes("HTTP/1.1 302 Found\r\n");
        dos.writeBytes("Location: " + location + "\r\n"); //이쪽으로 오십쇼~
        dos.writeBytes("Content-Length: 0\r\n");
        dos.writeBytes("Connection: close\r\n");
        dos.writeBytes("\r\n");
        dos.flush();
    }

    // 302 + 쿠키: 로그인 성공 시 쿠키 주고 메인으로 보내기
    private void response302HeaderWithCookie(DataOutputStream dos, String location, String cookie) throws IOException {
        dos.writeBytes("HTTP/1.1 302 Found\r\n");
        dos.writeBytes("Location: " + location + "\r\n");
        dos.writeBytes("Set-Cookie: " + cookie + "\r\n");
        dos.writeBytes("Content-Length: 0\r\n");
        dos.writeBytes("Connection: close\r\n");
        dos.writeBytes("\r\n");
        dos.flush();
    }

    // 404 Not Found: "그런 메뉴 없어요"
    private void response404Header(DataOutputStream dos) throws IOException {
        dos.writeBytes("HTTP/1.1 404 Not Found\r\n");
        dos.writeBytes("Content-Length: 0\r\n");
        dos.writeBytes("Connection: close\r\n");
        dos.writeBytes("\r\n");
        dos.flush();
    }

    // 9. 본문(파일 내용) 보내기
    private void responseBody(DataOutputStream dos, byte[] body) throws IOException {
        dos.write(body, 0, body.length);
        dos.flush();
    }

}