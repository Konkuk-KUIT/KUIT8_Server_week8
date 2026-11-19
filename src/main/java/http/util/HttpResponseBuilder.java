package http.util;

import webserver.RequestHandler;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpResponseBuilder {
    public record HttpResponse(StautsLine stautsLine, Map<String, String> headers, byte[] body) {
        public void addHeader(String key, String value){
            headers.put(key, value);
        }
    }

    public record StautsLine(String version, String code, String message) {
    }

    private static final Logger log = Logger.getLogger(RequestHandler.class.getName());

    public static void buildHttpResponse(DataOutputStream dos, HttpResponse httpResponse) {
        try {
            dos.writeBytes(
                    httpResponse.stautsLine().version()
                            + " "
                            + httpResponse.stautsLine().code()
                            + " "
                            + httpResponse.stautsLine().message()
                            + " \r\n"
            );

            httpResponse.headers().forEach((key, value) -> {
                try {
                    dos.writeBytes(key + ": " + value + "\r\n");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.log(Level.SEVERE, e.getMessage());
        }
    }

    public static HttpResponse response200(String parsedPath) throws IOException {
        StautsLine stautsLine = new StautsLine("HTTP/1.1", "200", "OK");
        Map<String, String> headers = new HashMap<>();
        byte[] body = Files.readAllBytes(Path.of(parsedPath));

        HttpResponse httpResponse = new HttpResponse(stautsLine, headers, body);

        String contentType;

        if (parsedPath.endsWith(".css")) {
            contentType = "text/css; charset=utf-8";
        } else {
            contentType = "text/html; charset=utf-8";
        }

        httpResponse.addHeader("Content-Type", contentType);
        httpResponse.addHeader("Content-Length", String.valueOf(body.length));

        return httpResponse;
    }

    public static HttpResponse response302(String parsedPath) throws IOException {
        StautsLine stautsLine = new StautsLine("HTTP/1.1", "302", "FOUND");
        Map<String, String> headers = new HashMap<>();
        byte[] body = new byte[0];

        HttpResponse httpResponse = new HttpResponse(stautsLine, headers, body);

        httpResponse.addHeader("Content-Type", "text/html; charset=utf-8");
        httpResponse.addHeader("Content-Length", String.valueOf(body.length));
        httpResponse.addHeader("Location", parsedPath);

        return httpResponse;
    }
}
