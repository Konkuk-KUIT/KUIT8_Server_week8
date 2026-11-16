package http.util;

import webserver.RequestHandler.RequestLine;

import java.io.IOException;

public class CustomParser {
    public record PathParserResult(String path, String queryString) {
    }

    public static RequestLine requestLineParser(String requestLine) throws IOException {
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
