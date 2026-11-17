package http.util;
import webserver.RequestHandler;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RequestParser {
    private static final Logger log = Logger.getLogger(RequestHandler.class.getName());

    public record HttpRequest(RequestLine requestLine, Map<String, String> headers, String body) {
    }

    public record RequestLine(String method, String path, String version){
    }

    public record PathParserResult(String path, String queryString) {
    }

    public static HttpRequest ParseRequest(BufferedReader br) throws IOException {
        String requestLine = br.readLine();
        RequestLine request = requestLineParser(requestLine);
        log.log(Level.INFO, "Request Arrived. Method: " + request.method + ", Path: " + request.path + ", Version: " + request.version);

        Map<String, String> headers = new HashMap<>();
        String nextLine;
        while(!Objects.equals(nextLine = br.readLine(), "")){
            String[] temp = nextLine.split(": ");
            headers.put(temp[0], temp[1]);
        }

        int contentLength = headers.containsKey("Content-Length") ? Integer.parseInt(headers.get("Content-Length")) : 0;
        String body = IOUtils.readData(br, contentLength);

        return new HttpRequest(request, headers, body);
    }

    private static RequestLine requestLineParser(String requestLine) throws IOException {
        if(requestLine.isBlank()) throw new IOException("requestLine이 비어있습니다.");

        String[] parts = requestLine.split(" ");
        return new RequestLine(parts[0], parts[1], parts[2]);
    }

    public static PathParserResult pathParser(String path) throws IOException {
        String[] temp = path.split("\\?");
        if(temp.length == 2) return new PathParserResult(temp[0], temp[1]);
        return new PathParserResult(temp[0], "");
    }
}
