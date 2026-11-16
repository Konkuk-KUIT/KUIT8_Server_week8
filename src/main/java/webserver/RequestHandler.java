package webserver;

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

    public class RequestLine{
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
            RequestLine request = requestLineParser(requestLine);
            String path = routingPath(request.path);

            byte[] body = Files.readAllBytes(Path.of(path));
            response200Header(dos, body.length);
            responseBody(dos, body);

        } catch (IOException e) {
            log.log(Level.SEVERE,e.getMessage());
        }
    }

    private RequestLine requestLineParser(String requestLine) throws IOException {
        if(requestLine.isBlank()) throw new IOException("requestLine이 비어있습니다.");

        String[] parts = requestLine.split(" ");
        return new RequestLine(parts[0], parts[1], parts[2]);
    }

    private final String defaultPath = "/";
    private final String indexPath = "/index.html";
    private final String webappPath = "webapp/index.html";

    private String routingPath(String path){
        if (path.equals(defaultPath) || path.equals(indexPath)) return webappPath;
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