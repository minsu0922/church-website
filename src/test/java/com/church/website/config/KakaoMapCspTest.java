package com.church.website.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("카카오맵 CSP")
class KakaoMapCspTest {

    @Autowired private WebApplicationContext context;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    // sdk.js가 지도 본체(kakao.js)와 주소 검색(services.js)을 t1.kakaocdn.net에서 불러온다
    @Test
    void 카카오맵_스크립트_CDN을_허용한다() throws Exception {
        String csp = mockMvc.perform(get("/location"))
                .andReturn().getResponse().getHeader("Content-Security-Policy");

        assertThat(directive(csp, "script-src")).contains("https://t1.kakaocdn.net");
        assertThat(directive(csp, "connect-src")).contains("https://t1.kakaocdn.net");
    }

    private String directive(String csp, String name) {
        return Arrays.stream(csp.split(";"))
                .map(String::trim)
                .filter(d -> d.startsWith(name + " "))
                .findFirst()
                .orElseThrow();
    }
}
