package com.church.website.controller;

import com.church.website.entity.StaffMember;
import com.church.website.repository.StaffMemberRepository;
import com.church.website.service.StaffMemberService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("교역자 등록과 소개 페이지 표시")
class StaffPhotoFlowTest {

    @Autowired private WebApplicationContext context;
    @Autowired private StaffMemberRepository staffMemberRepository;
    @Autowired private StaffMemberService staffMemberService;

    @Value("${file.upload.people.dir}")
    private String peopleDir;

    private MockMvc mockMvc;

    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3};

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void cleanUp() {
        staffMemberRepository.findAll().forEach(s -> staffMemberService.deletePhoto(s.getPhotoUrl()));
        staffMemberRepository.deleteAll();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 사진을_넣어_등록하면_소개_페이지에_사진이_나오고_URL로_받을_수_있다() throws Exception {
        MockMultipartFile photo = new MockMultipartFile("photoFile", "elder.jpg", "image/jpeg", JPG);

        mockMvc.perform(multipart("/admin/staff/save").file(photo).with(csrf())
                        .param("name", "박달주").param("role", "장로").param("ministry", "")
                        .param("bio", "첫 문단입니다.\n\n둘째 문단입니다.").param("isActive", "true"))
                .andExpect(redirectedUrl("/admin/staff"));

        StaffMember saved = staffMemberRepository.findAll().getFirst();
        assertThat(saved.getPhotoUrl()).startsWith("/uploads/people/");

        byte[] body = mockMvc.perform(get(saved.getPhotoUrl()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(body).isEqualTo(JPG);

        String html = mockMvc.perform(get("/about/staff"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("src=\"" + saved.getPhotoUrl() + "\"");
        assertThat(html).contains("white-space: pre-wrap");
        assertThat(html).doesNotContain("담당 사역:");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 수정할_때_새_사진을_올리면_이전_사진_파일을_지운다() throws Exception {
        mockMvc.perform(multipart("/admin/staff/save")
                        .file(new MockMultipartFile("photoFile", "a.jpg", "image/jpeg", JPG)).with(csrf())
                        .param("name", "박달주").param("isActive", "true"))
                .andExpect(redirectedUrl("/admin/staff"));
        StaffMember first = staffMemberRepository.findAll().getFirst();
        String oldUrl = first.getPhotoUrl();

        mockMvc.perform(multipart("/admin/staff/save")
                        .file(new MockMultipartFile("photoFile", "b.png", "image/png", JPG)).with(csrf())
                        .param("id", first.getId().toString()).param("name", "박달주").param("isActive", "true"))
                .andExpect(redirectedUrl("/admin/staff"));

        StaffMember updated = staffMemberRepository.findById(first.getId()).orElseThrow();
        assertThat(updated.getPhotoUrl()).isNotEqualTo(oldUrl).endsWith(".png");
        // 없는 정적 파일은 GlobalExceptionHandler가 200 오류 화면으로 응답하므로 디스크로 확인한다
        assertThat(Paths.get(peopleDir, oldUrl.substring("/uploads/people/".length()))).doesNotExist();
        assertThat(Paths.get(peopleDir, updated.getPhotoUrl().substring("/uploads/people/".length()))).exists();
    }
}
