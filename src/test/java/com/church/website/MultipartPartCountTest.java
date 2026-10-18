package com.church.website;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tomcat의 폼 칸 개수 제한(max-part-count)은 MockMvc로는 재현되지 않아 실제 서버를 띄워 확인한다.
 * 기본값 10이면 칸이 11개인 설교 등록 폼이 파일 크기와 상관없이 413으로 거절된다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("멀티파트 폼 칸 개수 제한")
class MultipartPartCountTest {

    @LocalServerPort
    int port;

    @Test
    void 칸이_11개인_설교_폼도_413_없이_받는다() throws Exception {
        String boundary = "----boundary";
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        String[] fields = {"_csrf", "id", "title", "preacher", "sermonDateStr", "biblePassage",
                "description", "videoSource", "videoUrl", "thumbnailUrl"};
        for (String name : fields) {
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name
                    + "\"\r\n\r\nv\r\n").getBytes(StandardCharsets.UTF_8));
        }
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"videoFile\"; filename=\"a.m4a\""
                + "\r\nContent-Type: audio/x-m4a\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(new byte[1000]);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/admin/sermon/save"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        int status = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding()).statusCode();

        // 로그인·CSRF가 없어 거절(302/403)되는 건 정상. 413만 아니면 Tomcat이 폼을 끝까지 읽은 것이다.
        assertThat(status).isNotEqualTo(413);
    }
}
