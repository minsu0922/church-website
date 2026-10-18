package com.church.website.controller;

import com.church.website.service.PastorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("담임목사 사진 등록 후 공개 페이지 표시")
class PastorPhotoFlowTest {

    @Autowired private WebApplicationContext context;
    @Autowired private PastorService pastorService;

    private MockMvc mockMvc;
    private String savedUrl;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void cleanUp() {
        pastorService.deletePhoto(savedUrl);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 등록한_사진을_URL로_내려받을_수_있다() throws Exception {
        byte[] jpg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3};
        MockMultipartFile photo = new MockMultipartFile("photoFile", "p.jpg", "image/jpeg", jpg);

        mockMvc.perform(multipart("/admin/pastor/save").file(photo)
                        .param("name", "홍길동").with(csrf()))
                .andExpect(status().is3xxRedirection());

        savedUrl = pastorService.getActive().orElseThrow().getPhotoUrl();
        assertThat(savedUrl).startsWith("/uploads/people/");

        byte[] body = mockMvc.perform(get(savedUrl))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(body).isEqualTo(jpg);
    }
}
