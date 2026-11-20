package http.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HttpRequestParserTest {
    @Test
    @DisplayName("GET 요청의 메서드, 원본 경로, 버전, 헤더를 분석한다")
    void parsesGetRequest() throws Exception {
        var request = parse("GET /user/signup?userId=student HTTP/1.1\r\n"
                + "Host: localhost:8080\r\n"
                + "Cookie: theme=dark; logined=true\r\n\r\n");

        assertAll(
                () -> assertEquals(HttpMethod.GET, request.requestLine().method()),
                () -> assertEquals("/user/signup?userId=student", request.requestLine().path()),
                () -> assertEquals("HTTP/1.1", request.requestLine().version()),
                () -> assertEquals("localhost:8080", request.headers().get("Host")),
                () -> assertEquals("theme=dark; logined=true", request.headers().get("Cookie")),
                () -> assertEquals("", request.body())
        );
    }

    @Test
    @DisplayName("POST 폼 본문을 읽고 한글, 공백, 리터럴 +를 복원할 수 있다")
    void parsesPostFormWithEncodedKorean() throws Exception {
        String body = "userId=student&password=a%2Bb&name=%ED%99%8D%EA%B8%B8%EB%8F%99+Student";
        var request = parse(postRequest("Content-Length", body));

        assertAll(
                () -> assertEquals(HttpMethod.POST, request.requestLine().method()),
                () -> assertEquals("/user/signup", request.requestLine().path()),
                () -> assertEquals("application/x-www-form-urlencoded", request.headers().get("Content-Type")),
                () -> assertEquals(body, request.body()),
                () -> assertEquals(Map.of("userId", "student", "password", "a+b", "name", "홍길동 Student"),
                        HttpRequestUtils.parseQueryParameter(request.body()))
        );
    }

    @Test
    @DisplayName("본문은 Content-Length만큼만 읽고 뒤의 데이터는 남겨 둔다")
    void readsExactlyContentLength() throws Exception {
        String body = "userId=student";
        try (var reader = new BufferedReader(new StringReader(postRequest("Content-Length", body) + "EXTRA"))) {
            var request = HttpRequestParser.ParseRequest(reader);

            assertEquals(body, request.body());
            assertEquals("EXTRA", reader.readLine());
        }
    }

    @Test
    @DisplayName("Content-Length가 0이면 빈 본문을 반환한다")
    void acceptsZeroLengthBody() throws Exception {
        assertEquals("", parse(postRequest("Content-Length", "")).body());
    }

    @Test
    @DisplayName("Content-Length보다 본문이 짧으면 EOFException이 발생한다")
    void rejectsTruncatedBody() {
        assertThrows(EOFException.class, () -> parse(
                "POST /user/signup HTTP/1.1\r\nContent-Length: 20\r\n\r\nuserId=a"));
    }

    @ParameterizedTest(name = "{0} -> path={1}, query={2}")
    @CsvSource(value = {
            "/|/|''",
            "/index.html|/index.html|''",
            "/user/signup?userId=student&password=pass|/user/signup|userId=student&password=pass",
            "/user/signup?|/user/signup|''"
    }, delimiter = '|', emptyValue = "")
    @DisplayName("경로와 쿼리를 분리하고 쿼리가 없으면 빈 문자열을 반환한다")
    void splitsPathAndQuery(String target, String expectedPath, String expectedQuery) throws Exception {
        var result = HttpRequestParser.pathParser(target);

        assertEquals(expectedPath, result.path());
        assertEquals(expectedQuery, result.queryString());
    }

    private static HttpRequestParser.HttpRequest parse(String rawRequest) throws Exception {
        try (var reader = new BufferedReader(new StringReader(rawRequest))) {
            return HttpRequestParser.ParseRequest(reader);
        }
    }

    private static String postRequest(String lengthHeaderName, String body) {
        return "POST /user/signup HTTP/1.1\r\n"
                + "Content-Type: application/x-www-form-urlencoded\r\n"
                + lengthHeaderName + ": " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n"
                + body;
    }
}
