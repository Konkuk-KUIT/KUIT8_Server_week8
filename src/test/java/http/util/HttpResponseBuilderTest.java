package http.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class HttpResponseBuilderTest {
    @TempDir
    Path tempDir;

    @Test
    @DisplayName("HTML 파일로 200 응답과 UTF-8 Content-Type을 생성한다")
    void createsHtmlResponse() throws Exception {
        byte[] content = "<h1>Hello</h1>".getBytes(StandardCharsets.UTF_8);
        Path file = writeFile("index.html", content);

        var response = HttpResponseBuilder.response200(file.toString());

        assertAll(
                () -> assertEquals("HTTP/1.1", response.stautsLine().version()),
                () -> assertEquals("200", response.stautsLine().code()),
                () -> assertEquals("OK", response.stautsLine().message()),
                () -> assertEquals("text/html; charset=utf-8", response.headers().get("Content-Type")),
                () -> assertArrayEquals(content, response.body())
        );
    }

    @Test
    @DisplayName("CSS 파일은 text/css와 원본 내용으로 응답한다")
    void createsCssResponse() throws Exception {
        byte[] content = "body { color: red; }".getBytes(StandardCharsets.UTF_8);
        Path file = writeFile("styles.css", content);

        var response = HttpResponseBuilder.response200(file.toString());

        assertEquals("text/css; charset=utf-8", response.headers().get("Content-Type"));
        assertEquals(String.valueOf(content.length), response.headers().get("Content-Length"));
        assertArrayEquals(content, response.body());
    }

    @Test
    @DisplayName("한글 HTML의 Content-Length는 문자 수가 아닌 UTF-8 바이트 수이다")
    void calculatesContentLengthInBytes() throws Exception {
        String html = "<h1>안녕하세요</h1>";
        byte[] content = html.getBytes(StandardCharsets.UTF_8);
        Path file = writeFile("korean.html", content);

        var response = HttpResponseBuilder.response200(file.toString());

        assertNotEquals(html.length(), content.length);
        assertEquals(String.valueOf(content.length), response.headers().get("Content-Length"));
        assertArrayEquals(content, response.body());
    }

    @Test
    @DisplayName("빈 파일은 Content-Length 0과 빈 본문으로 응답한다")
    void createsEmptyFileResponse() throws Exception {
        Path file = writeFile("empty.html", new byte[0]);

        var response = HttpResponseBuilder.response200(file.toString());

        assertEquals("0", response.headers().get("Content-Length"));
        assertArrayEquals(new byte[0], response.body());
    }

    @Test
    @DisplayName("존재하지 않는 파일을 읽으면 IOException이 발생한다")
    void rejectsMissingFile() {
        assertThrows(IOException.class,
                () -> HttpResponseBuilder.response200(tempDir.resolve("missing.html").toString()));
    }

    @Test
    @DisplayName("302 응답은 대상 URL을 Location에 넣고 빈 본문을 사용한다")
    void createsRedirectWithoutReadingDestinationFile() throws Exception {
        String location = "/user/login.html?next=%2Fuser%2FuserList";

        var response = HttpResponseBuilder.response302(location);

        assertAll(
                () -> assertEquals("HTTP/1.1", response.stautsLine().version()),
                () -> assertEquals("302", response.stautsLine().code()),
                () -> assertEquals(location, response.headers().get("Location")),
                () -> assertEquals("0", response.headers().get("Content-Length")),
                () -> assertArrayEquals(new byte[0], response.body())
        );
    }

    @Test
    @DisplayName("리다이렉트 응답에 추가한 Set-Cookie 헤더가 스트림에 출력된다")
    void serializesRedirectAndCookieHeaders() throws Exception {
        var response = HttpResponseBuilder.response302("/index.html");
        response.addHeader("Set-Cookie", "logined=true; Path=/");

        String wire = serializeHeaders(response);

        // 헤더 순서는 Map 구현에 따라 달라질 수 있으므로 순서에 의존하지 않는다.
        var lines = Arrays.asList(wire.split("\r\n"));
        assertTrue(lines.getFirst().startsWith("HTTP/1.1 302 "));
        assertTrue(lines.contains("Location: /index.html"));
        assertTrue(lines.contains("Content-Length: 0"));
        assertTrue(lines.contains("Set-Cookie: logined=true; Path=/"));
        assertTrue(wire.endsWith("\r\n\r\n"));
    }

    @Test
    @DisplayName("200 응답 헤더는 CRLF와 빈 줄로 구분하며 본문은 별도로 전송한다")
    void serializesHtmlHeaders() throws Exception {
        byte[] content = "<p>응답</p>".getBytes(StandardCharsets.UTF_8);
        var response = HttpResponseBuilder.response200(writeFile("response.html", content).toString());

        String wire = serializeHeaders(response);
        var lines = Arrays.asList(wire.split("\r\n"));

        assertTrue(lines.getFirst().startsWith("HTTP/1.1 200 OK"));
        assertTrue(lines.contains("Content-Type: text/html; charset=utf-8"));
        assertTrue(lines.contains("Content-Length: " + content.length));
        assertTrue(wire.endsWith("\r\n\r\n"));
        assertFalse(wire.contains("\n\n"));
        assertArrayEquals(content, response.body());
    }

    private Path writeFile(String name, byte[] content) throws IOException {
        return Files.write(tempDir.resolve(name), content);
    }

    private static String serializeHeaders(HttpResponseBuilder.HttpResponse response) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) {
            // 현재 RequestHandler가 본문을 따로 쓰므로 이 메서드는 헤더 출력만 검증한다.
            HttpResponseBuilder.buildHttpResponse(output, response);
            output.flush();
        }
        return bytes.toString(StandardCharsets.ISO_8859_1);
    }
}
