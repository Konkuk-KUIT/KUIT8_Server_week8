package webserver;

import http.util.HttpMethod;
import http.util.HttpRequestUtils;
import http.util.HttpRequestParser;
import http.util.HttpRequestParser.PathParserResult;
import http.util.HttpResponseBuilder.HttpResponse;
import http.util.HttpResponseBuilder;

import java.io.*;
import java.net.Socket;
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

            HttpRequestParser.HttpRequest httpRequest = HttpRequestParser.ParseRequest(br);
            PathParserResult pathParserResult = HttpRequestParser.pathParser(httpRequest.requestLine().path());

            String parsedPath = routingPath(pathParserResult.path());
            HttpMethod method = httpRequest.requestLine().method();
            Map<String, String> parameters = HttpRequestUtils.parseQueryParameter(
                    method == HttpMethod.GET ? pathParserResult.queryString() : httpRequest.body()
                    );

            HttpResponse httpResponse = null;

            if(httpRequest.requestLine().path().equals("/user/signup") && method == HttpMethod.GET) {
                String userId = parameters.get("userId");
                String password = parameters.get("password");
                String name = parameters.get("name");
                String email = parameters.get("email");

                memoryUserController.signup(userId, password, name, email);
                httpResponse = HttpResponseBuilder.response302("/index.html");
            }else if(httpRequest.requestLine().path().equals("/user/login") && method == HttpMethod.POST) {
                String userId = parameters.get("userId");
                String password = parameters.get("password");

                boolean loginResult = memoryUserController.login(userId, password);
                if(loginResult) {
                    httpResponse = HttpResponseBuilder.response302("/index.html");
                    httpResponse.addHeader("Set-Cookie", "logined=true");
                    httpResponse.addHeader("Path", "/");
                }else{
                    httpResponse = HttpResponseBuilder.response302("login_failed.html");
                }
            }else if(httpRequest.requestLine().path().equals("/user/userList")) {
                if(httpRequest.headers().containsKey("Cookie") && httpRequest.headers().get("Cookie").contains("logined=true")) {
                    httpResponse = HttpResponseBuilder.response302("list.html");
                }
                else {
                    httpResponse = HttpResponseBuilder.response302("login.html");
                }
            }else{
                httpResponse = HttpResponseBuilder.response200(parsedPath);
            }

            HttpResponseBuilder.buildHttpResponse(dos, httpResponse);
            responseBody(dos, httpResponse.body());
        } catch (IOException e) {
            log.log(Level.SEVERE,e.getMessage());
        }
    }

    private String routingPath(String path){
        String defaultPath = "/";
        String webappPath = "webapp";

        if (path.equals(defaultPath)) return webappPath + "/index.html";
        else return webappPath + path;
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