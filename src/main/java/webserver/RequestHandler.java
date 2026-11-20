package webserver;

import java.io.*;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

import db.MemoryUserRepository;
import http.HttpMethod;
import http.HttpRequest;
import model.User;

import http.HttpResponse;

public class RequestHandler implements Runnable {
    Socket connection;
    private static final Logger log = Logger.getLogger(RequestHandler.class.getName());

    public RequestHandler(Socket connection) {
        this.connection = connection;
    }

    @Override
    public void run() {
        log.log(Level.INFO, "New Client Connect! Connected IP : " + connection.getInetAddress() + ", Port : "
                + connection.getPort());
        try (InputStream in = connection.getInputStream(); OutputStream out = connection.getOutputStream()) {
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            DataOutputStream dos = new DataOutputStream(out);
            HttpRequest request = HttpRequest.from(br);
            HttpResponse response = new HttpResponse(dos);
            HttpMethod method = request.getMethod();
            String path = request.getPath();
            boolean logined = "true".equals(request.getCookie("logined"));

            if (method == HttpMethod.GET) {
                // "/" 요청이면 index.html 반환
                if (path.equals("/")) {
                    path = "/index.html";
                }

                // 사용자 목록 요청이면 로그인 여부 확인
                if (path.equals("/user/userList")
                        || path.equals("/user/list.html")) {

                    if (!logined) {
                        response.redirect("/user/login.html");
                        return;
                    }

                    path = "/user/list.html";
                }

                // HTML 또는 CSS 파일 반환
                response.forward(path);
                return;
            }

            if (method == HttpMethod.POST) {
                // 회원가입
                if (path.equals("/user/signup")) {
                    User user = new User(
                            request.getParameter("userId"),
                            request.getParameter("password"),
                            request.getParameter("name"),
                            request.getParameter("email"));

                    MemoryUserRepository.getInstance().addUser(user);

                    response.redirect("/index.html");
                    return;
                }

                // 로그인
                if (path.equals("/user/login")) {
                    String userId = request.getParameter("userId");
                    String password = request.getParameter("password");

                    User user = MemoryUserRepository.getInstance()
                            .findUserById(userId);

                    if (user != null && user.getPassword().equals(password)) {
                        response.setCookie("logined=true; Path=/");
                        response.redirect("/index.html");
                    } else {
                        response.redirect("/user/login_failed.html");
                    }

                    return;
                }
            }

        } catch (IOException e) {
            log.log(Level.SEVERE, e.getMessage());
        }
    }
}
