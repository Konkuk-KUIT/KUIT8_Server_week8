package http.util;

import webserver.RequestHandler;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpResponseBuilder {
    public record HttpResponse(StautsLine stautsLine, Map<String, String> headers, byte[] body) {
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
        byte[] body = Files.readAllBytes(Path.of(parsedPath));

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "text/html;charset=utf-8");
        headers.put("Content-Length", String.valueOf(body.length));

        return new HttpResponse(stautsLine, headers, body);
    }

    public static HttpResponse response302(String parsedPath) throws IOException {
        StautsLine stautsLine = new StautsLine("HTTP/1.1", "302", "FOUND");
        byte[] body = new byte[0];

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "text/html;charset=utf-8");
        headers.put("Content-Length", String.valueOf(body.length));
        headers.put("Location", parsedPath);

        return new HttpResponse(stautsLine, headers, body);
    }
}
