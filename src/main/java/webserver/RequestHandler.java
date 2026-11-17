package webserver;

import http.util.HttpRequestUtils;
import http.util.RequestParser;
import http.util.RequestParser.PathParserResult;

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
    public MemoryUserController memoryUserController = MemoryUserController.getInstance();

    @Override
    public void run() {
        log.log(Level.INFO, "New Client Connect! Connected IP : " + connection.getInetAddress() + ", Port : " + connection.getPort());
        try (InputStream in = connection.getInputStream(); OutputStream out = connection.getOutputStream()){
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            DataOutputStream dos = new DataOutputStream(out);

            RequestParser.HttpRequest httpRequest = RequestParser.ParseRequest(br);
            PathParserResult pathParserResult = RequestParser.pathParser(httpRequest.requestLine().path());

            String parsedPath = routingPath(pathParserResult.path());
            String method = httpRequest.requestLine().method();
            Map<String, String> parameters = HttpRequestUtils.parseQueryParameter(
                    method.equals("GET") ? pathParserResult.queryString() : httpRequest.body()
                    );

            if(httpRequest.requestLine().path().equals("/user/signup") && method.equals("POST")) {
                String userId = parameters.get("userId");
                String password = parameters.get("password");
                String name = parameters.get("name");
                String email = parameters.get("email");

                memoryUserController.GetSignUp(userId, password, name, email);
            }

            byte[] body = Files.readAllBytes(Path.of(parsedPath));
            response200Header(dos, body.length);
            responseBody(dos, body);
        } catch (IOException e) {
            log.log(Level.SEVERE,e.getMessage());
        }
    }

    private String routingPath(String path){
        String defaultPath = "/";
        String webappPath = "webapp/";

        if (path.equals(defaultPath)) return webappPath + "index.html";
        else return webappPath + path;
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