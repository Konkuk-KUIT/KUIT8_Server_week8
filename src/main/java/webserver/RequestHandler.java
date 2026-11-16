package webserver;

import http.util.HttpRequestUtils;
import http.util.CustomParser;
import http.util.CustomParser.PathParserResult;

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
    public MemoryUserController memoryUserController = MemoryUserController.getInstance();

    public static class RequestLine{
        String method;
        String path;
        String version;

        public RequestLine(String method, String path, String version) {
            this.method = method;
            this.path = path;
            this.version = version;
        }
    }

    @Override
    public void run() {
        log.log(Level.INFO, "New Client Connect! Connected IP : " + connection.getInetAddress() + ", Port : " + connection.getPort());
        try (InputStream in = connection.getInputStream(); OutputStream out = connection.getOutputStream()){
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            DataOutputStream dos = new DataOutputStream(out);

            String requestLine = br.readLine();
            RequestLine request = CustomParser.requestLineParser(requestLine);
            log.log(Level.INFO, "Request Arrived. Method: " + request.method + ", Path: " + request.path + ", Version: " + request.version);


            PathParserResult pathParserResult = CustomParser.pathParser(request.path);
            String path = routingPath(pathParserResult.path());
            Map<String, String> parameters = HttpRequestUtils.parseQueryParameter(pathParserResult.queryString());

            if(!parameters.isEmpty()) {
                if(request.method.equals("GET")) GetRouting(path, parameters);
            }

            byte[] body = Files.readAllBytes(Path.of(path));
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

    private void GetRouting(String path, Map<String, String> parameters){
        String username = parameters.get("userId");
        String password = parameters.get("password");
        String name = parameters.get("name");
        String email = parameters.get("email");

        memoryUserController.GetSignUp(username, password, name, email);
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