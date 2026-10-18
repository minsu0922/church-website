package com.church.website.service;

import com.church.website.entity.Sermon;
import com.church.website.repository.SermonRepository;
import com.church.website.service.SermonService.VideoSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("설교 영상: YouTube 링크와 파일 업로드")
class SermonVideoUploadTest {

    @TempDir
    static Path uploadDir;

    @DynamicPropertySource
    static void uploadProps(DynamicPropertyRegistry registry) {
        registry.add("file.upload.sermon.dir", () -> uploadDir.toString());
    }

    @Autowired private SermonService sermonService;
    @Autowired private SermonRepository sermonRepository;
    @Autowired private WebApplicationContext context;

    private MockMvc mockMvc;

    private static final byte[] VIDEO_BYTES = {0, 0, 0, 0x18, 'f', 't', 'y', 'p'};
    private static final String YOUTUBE_URL = "https://www.youtube.com/watch?v=abcdefghijk";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void tearDown() {
        sermonRepository.deleteAll();
    }

    private Sermon form(Long id, String videoUrl) {
        return Sermon.builder().id(id).title("설교").preacher("홍길동 목사")
                .sermonDate(LocalDate.of(2026, 10, 18)).biblePassage("요한복음 3:16")
                .videoUrl(videoUrl).build();
    }

    private MockMultipartFile mp4() {
        return new MockMultipartFile("videoFile", "sermon.mp4", "video/mp4", VIDEO_BYTES);
    }

    private Path fileOf(Sermon s) {
        return uploadDir.resolve(s.getVideoUrl().substring(Sermon.UPLOAD_URL_PREFIX.length()));
    }

    @Test
    void 파일을_업로드하면_서버에_저장되고_업로드_영상으로_판별된다() throws Exception {
        Sermon saved = sermonService.save(form(null, null), VideoSource.UPLOAD, mp4());

        assertThat(saved.isUploadedVideo()).isTrue();
        assertThat(saved.getYoutubeVideoId()).isNull();
        assertThat(Files.readAllBytes(fileOf(saved))).isEqualTo(VIDEO_BYTES);
    }

    @Test
    void YouTube_링크로_등록하면_파일을_저장하지_않는다() throws Exception {
        Sermon saved = sermonService.save(form(null, YOUTUBE_URL), VideoSource.YOUTUBE, null);

        assertThat(saved.isUploadedVideo()).isFalse();
        assertThat(saved.getYoutubeVideoId()).isEqualTo("abcdefghijk");
    }

    @Test
    void 업로드_방식인데_파일이_없으면_거부한다() {
        assertThatThrownBy(() -> sermonService.save(form(null, null), VideoSource.UPLOAD, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 허용하지_않는_형식은_거부한다() {
        MockMultipartFile mov = new MockMultipartFile("videoFile", "a.mov", "video/quicktime", VIDEO_BYTES);
        assertThatThrownBy(() -> sermonService.save(form(null, null), VideoSource.UPLOAD, mov))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 수정할_때_새_파일이_없으면_기존_영상을_유지한다() throws Exception {
        Sermon saved = sermonService.save(form(null, null), VideoSource.UPLOAD, mp4());

        Sermon updated = sermonService.save(form(saved.getId(), null), VideoSource.UPLOAD, null);

        assertThat(updated.getVideoUrl()).isEqualTo(saved.getVideoUrl());
        assertThat(fileOf(updated)).exists();
    }

    @Test
    void 업로드_영상을_YouTube로_바꾸면_기존_파일을_지운다() throws Exception {
        Sermon saved = sermonService.save(form(null, null), VideoSource.UPLOAD, mp4());
        Path oldFile = fileOf(saved);

        Sermon updated = sermonService.save(form(saved.getId(), YOUTUBE_URL), VideoSource.YOUTUBE, null);

        assertThat(updated.getYoutubeVideoId()).isEqualTo("abcdefghijk");
        assertThat(oldFile).doesNotExist();
    }

    @Test
    void 업로드_영상을_YouTube로_바꾸는데_URL이_없으면_거부한다() throws Exception {
        Sermon saved = sermonService.save(form(null, null), VideoSource.UPLOAD, mp4());

        assertThatThrownBy(() -> sermonService.save(form(saved.getId(), ""), VideoSource.YOUTUBE, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(fileOf(saved)).exists();
    }

    @Test
    void 설교를_삭제하면_업로드_파일도_지운다() throws Exception {
        Sermon saved = sermonService.save(form(null, null), VideoSource.UPLOAD, mp4());
        Path file = fileOf(saved);

        sermonService.delete(saved.getId());

        assertThat(file).doesNotExist();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 관리자_폼으로_업로드한_영상을_URL로_받을_수_있다() throws Exception {
        mockMvc.perform(multipart("/admin/sermon/save").file(mp4()).with(csrf())
                        .param("title", "설교").param("preacher", "홍길동 목사")
                        .param("sermonDateStr", "2026-10-18").param("biblePassage", "요한복음 3:16")
                        .param("videoSource", "UPLOAD"))
                .andExpect(redirectedUrl("/admin/sermon"));

        Sermon saved = sermonRepository.findAll().getFirst();
        byte[] body = mockMvc.perform(get(saved.getVideoUrl()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(body).isEqualTo(VIDEO_BYTES);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 등록_수정_폼과_상세_페이지가_렌더링된다() throws Exception {
        Sermon saved = sermonService.save(form(null, null), VideoSource.UPLOAD, mp4());

        mockMvc.perform(get("/admin/sermon/new")).andExpect(status().isOk());
        String editHtml = mockMvc.perform(get("/admin/sermon/edit/" + saved.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String detailHtml = mockMvc.perform(get("/sermon/" + saved.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(editHtml).containsPattern("value=\"UPLOAD\"\\s+onchange=\"switchSource\\(\\)\" checked=\"checked\"");
        assertThat(detailHtml).contains("<video src=\"" + saved.getVideoUrl() + "\"");
    }
}
