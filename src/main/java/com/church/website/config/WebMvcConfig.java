package com.church.website.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 추가 설정 클래스.
 *
 * <p>WebMvcConfigurer 인터페이스를 구현하여 Spring MVC의 기본 설정을 확장한다.
 * 두 가지를 설정한다.
 *
 * <ul>
 *   <li>정적 리소스 핸들러: /uploads/ministry/** URL 요청을 서버 파일시스템의
 *       실제 업로드 디렉토리로 연결한다. 파일을 소스 디렉토리 밖에 저장하더라도
 *       브라우저에서 URL로 직접 접근할 수 있게 된다.</li>
 *   <li>인터셉터: 모든 HTTP 요청에 RequestLoggingInterceptor를 적용한다.
 *       정적 리소스(CSS, JS, 이미지 등)는 제외하여 로그 노이즈를 줄인다.</li>
 * </ul>
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${file.upload.dir}")
    private String ministryUploadDir;

    @Value("${file.upload.people.dir}")
    private String peopleUploadDir;

    @Value("${file.upload.sermon.dir}")
    private String sermonUploadDir;

    private final RequestLoggingInterceptor requestLoggingInterceptor;

    /**
     * /uploads/ministry/** URL 요청을 실제 파일 디렉토리와 연결한다.
     *
     * <p>예: URL /uploads/ministry/abc.jpg → 파일시스템 {ministryUploadDir}/abc.jpg
     * "file:" 접두사는 classpath가 아닌 로컬 파일시스템 경로임을 Spring에 알려준다.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/ministry/**")
                .addResourceLocations("file:" + ministryUploadDir + "/");
        registry.addResourceHandler("/uploads/people/**")
                .addResourceLocations("file:" + peopleUploadDir + "/");
        registry.addResourceHandler("/uploads/sermon/**")
                .addResourceLocations("file:" + sermonUploadDir + "/");
    }

    /**
     * 모든 페이지 요청에 RequestLoggingInterceptor를 적용한다.
     * CSS·JS·이미지·업로드 파일 경로는 제외하여 의미 있는 요청만 로깅한다.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requestLoggingInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/css/**", "/js/**", "/images/**", "/uploads/**");
    }
}
