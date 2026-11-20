package webserver;

import db.MemoryUserRepository;
import http.util.HttpRequestUtils;
import http.util.IOUtils;
import model.User;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
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
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            DataOutputStream dos = new DataOutputStream(out);

            String requestLine = br.readLine(); //헤더 첫 번째 줄
            if(requestLine == null || requestLine.isEmpty()){
                return;
            }
            String[] tokens = requestLine.split(" ");
            String method = tokens[0];
            String url = tokens[1];
            String version = tokens[2];

            //헤더 읽기
            Map<String, String> headers = new HashMap<>();
            String line = br.readLine();
            while(line != null && !line.isEmpty()){
                String[] kv = line.split(":", 2);
                headers.put(kv[0].trim(), kv[1].trim());
                line = br.readLine();
            }



            if(method.equals("POST")){
                //POST 요청이 들어왔을 때
                int contentLength;
                try {
                    contentLength = Integer.parseInt(headers.getOrDefault("Content-Length", "").trim());
                } catch (NumberFormatException e) {
                    throw new IOException("잘못된 Content-Length");
                }
                if (contentLength < 0) {
                    throw new IOException("음수 Content-Length");
                }

                //body 부분 추출 및 body부분 파라미터 map으로 변환
                String body = IOUtils.readData(br, contentLength);
                Map<String, String> parameter = HttpRequestUtils.parseQueryParameter(body);

                if(url.equals("/user/signup")){
                    //회원가입 요청 처리
                    User user = new User(parameter.get("userId"), parameter.get("password"), parameter.get("name"), parameter.get("email"));
                    MemoryUserRepository.getInstance().addUser(user);
                    response302Header(dos, "/index.html", "logined=true; Path=/");
                }else if (url.equals("/user/login")){
                    //로그인 요청 처리
                    String loginUserID = parameter.get("userId");
                    String loginUserPassword = parameter.get("password");
                    User findedUser = MemoryUserRepository.getInstance().findUserById(loginUserID);
                    if(findedUser != null &&findedUser.getPassword().equals(loginUserPassword)){
                        //찾은 유저에 대한 비밀번호가 일치할 때
                        response302Header(dos, "/index.html", "logined=true; Path=/");
                    }else{
                        //없는 아이디 혹은 틀린 비밀번호 일 때
                        response302Header(dos, "/user/login_failed.html", null);
                    }
                }

            } else if (method.equals("GET")) {
                if(url.equals("/")){//기본 분기로 하였을 때
                    byte[] resBody = Files.readAllBytes(Path.of("webapp/index.html"));
                    response200Header(dos, "text/html;charset=utf-8", resBody.length);
                    responseBody(dos, resBody);
                } else if (url.equals("/user/userList") || url.equals("/user/list.html")) {
                    Map<String, String> cookies = parseCookies(headers.get("Cookie"));
                    if(cookies.get("logined").equals("true")){
                        //쿠키에 로그인 흔적이 있을 때
                        Path path = Path.of("webapp/user/list.html");
                        byte[] resBody = Files.readAllBytes(path);
                        response200Header(dos, "text/html;charset=utf-8",resBody.length);
                        responseBody(dos, resBody);
                    }else{
                        response302Header(dos, "/user/login.html", null);
                    }
                } else if (url.equals("/css/styles.css")) {
                    byte[] resBody = Files.readAllBytes(Path.of("webapp"+url));
                    response200Header(dos, "text/css", resBody.length);
                    responseBody(dos, resBody);
                } else{//나머지 경로에 대한 요청
                    byte[] resBody = Files.readAllBytes(Path.of("webapp" + url));
                    response200Header(dos, "text/html;charset=utf-8",resBody.length);
                    responseBody(dos, resBody);
                }
            } else{
                //404
            }
        } catch (IOException e) {
            log.log(Level.SEVERE,e.getMessage());
        }
    }

    private void response200Header(DataOutputStream dos, String ContentType,int lengthOfBodyContent) {
        try {
            dos.writeBytes("HTTP/1.1 200 OK \r\n");
            dos.writeBytes("Content-Type:"+ContentType +"\r\n");
            dos.writeBytes("Content-Length: " + lengthOfBodyContent + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.log(Level.SEVERE, e.getMessage());
        }
    }

    private void responseBody(DataOutputStream dos, byte[] body) {
        try {
            dos.write(body, 0, body.length);
            dos.flush();
        } catch (IOException e) {
            log.log(Level.SEVERE, e.getMessage());
        }
    }


    private void response302Header(DataOutputStream dos, String location, String cookie) throws IOException {
        try{
            dos.writeBytes("HTTP/1.1 302 Found \r\n");
            dos.writeBytes("Location: " + location + "\r\n");
            if (cookie != null){
                dos.writeBytes("Set-Cookie: " + cookie + "\r\n");
            }
            dos.writeBytes("Content-Length: 0\r\n");
            dos.writeBytes("Connection: close\r\n");
            dos.writeBytes("\r\n");
            dos.flush();
        } catch (IOException e){
            log.log(Level.SEVERE, e.getMessage());
        }

    }
    private Map<String, String> parseCookies(String cookieHeader) {
        Map<String, String> cookies = new HashMap<>();
        if (cookieHeader == null || cookieHeader.isEmpty()) {
            return cookies;
        }
        for (String pair : cookieHeader.split(";")) {
            String[] kv = pair.trim().split("=", 2);
            if (kv.length == 2) {
                cookies.put(kv[0].trim(), kv[1].trim());
            }
        }
        return cookies;
    }



}