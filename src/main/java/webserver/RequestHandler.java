package webserver;

import db.MemoryUserRepository;
import http.util.HttpRequestUtils;
import http.util.IOUtils;
import model.User;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
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

            String input = br.readLine();
            if(input == null) {
                return;
            }

            String[] parts=input.split(" ");
            String method = parts[0];
            String path = parts[1];

            String[] targetParts = path.split("\\?", 2);
            String requestPath = targetParts[0]; // "/user/signup"
            String query = targetParts.length == 2 ? targetParts[1] : "";

            if ("GET".equals(method) && "/user/signup".equals(requestPath)) {
                // 여기서 query를 파싱하고 User를 생성해서 저장
                Map<String, String> queries = HttpRequestUtils.parseQueryParameter(query);
                User user = new User(
                        queries.get("userId"),
                        queries.get("password"),
                        queries.get("name"),
                        queries.get("email")
                );
                MemoryUserRepository.getInstance().addUser(user);
                // 응답을 보낸 뒤 return
                return;
            }

            //요구사항 1
            String[] pathDet=parts[1].split("/");

            if(pathDet.length==0){
                byte[] body = Files.readAllBytes(Path.of("webapp", "index.html"));
                response200Header(dos, body.length);
                responseBody(dos, body);
            }
            if(pathDet.length==2){
                byte[] body = Files.readAllBytes(Path.of("webapp", pathDet[1]));
                response200Header(dos, body.length);
                responseBody(dos, body);
            }
            else if(pathDet.length==3){
                byte[] body = Files.readAllBytes(Path.of("webapp", pathDet[1], pathDet[2]));
                response200Header(dos, body.length);
                responseBody(dos, body);
            }

        } catch (IOException e) {
            log.log(Level.SEVERE,e.getMessage());
        }
    }

    private void response200Header(DataOutputStream dos, int lengthOfBodyContent) {
        try {
            dos.writeBytes("HTTP/1.1 200 OK \r\n");
            dos.writeBytes("Content-Type: text/html;charset=utf-8\r\n");
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

}