package com.church.website.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 설교 영상 엔티티.
 *
 * <p>설교 영상 정보를 담는다. 미디어는 두 방식 중 하나다.
 * YouTube URL이면 iframe으로 임베드하고, 업로드 파일이면 video·audio 태그로 재생한다.
 *
 * <p>YouTube URL 처리:
 * videoUrl에 입력된 다양한 형태의 YouTube 링크(watch?v=, youtu.be/, embed/)를
 * {@link #getYoutubeVideoId()}가 정규식으로 파싱하여 11자리 비디오 ID를 추출한다.
 * 추출된 ID는 Thymeleaf 템플릿에서 iframe src를 구성하는 데 사용된다.
 */
@Entity
@Table(name = "sermon")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Sermon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 설교 제목 */
    @Column(nullable = false, length = 200)
    private String title;

    /** 설교자 이름 */
    @Column(nullable = false, length = 100)
    private String preacher;

    /** 설교 날짜 */
    @Column(nullable = false)
    private LocalDate sermonDate;

    /** 성경 본문 (예: "요한복음 3:16") */
    @Column(nullable = false, length = 200)
    private String biblePassage;

    /** 업로드한 영상·음성 파일의 URL 접두사. videoUrl이 이걸로 시작하면 서버 파일이다. */
    public static final String UPLOAD_URL_PREFIX = "/uploads/sermon/";

    /**
     * 설교 미디어 주소. YouTube URL 또는 업로드 파일 URL("/uploads/sermon/파일명")을 저장한다.
     * 컬럼을 따로 두지 않은 이유: 운영 DB가 ddl-auto=validate라 스키마 변경 없이 넣기 위해서다.
     */
    @Column
    private String videoUrl;

    /** 썸네일 이미지 URL (없으면 YouTube 기본 썸네일 사용) */
    @Column
    private String thumbnailUrl;

    /** 설교 요약·설명 (선택사항) */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** 최초 등록 일시 — @PrePersist로 자동 설정 */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** 엔티티가 처음 DB에 저장되기 직전 자동 호출 — 등록 시각을 현재 시각으로 채운다. */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /**
     * videoUrl에서 YouTube 비디오 ID(11자리)를 추출한다.
     *
     * <p>지원하는 URL 형태:
     * <ul>
     *   <li>https://www.youtube.com/watch?v=abc12345678</li>
     *   <li>https://youtu.be/abc12345678</li>
     *   <li>https://www.youtube.com/embed/abc12345678</li>
     * </ul>
     *
     * @return 11자리 비디오 ID. URL이 null이거나 패턴과 맞지 않으면 null 반환.
     */
    public String getYoutubeVideoId() {
        if (videoUrl == null || isUploadedFile()) return null;
        Pattern pattern = Pattern.compile(
            "(?:youtube\\.com/(?:watch\\?v=|embed/)|youtu\\.be/)([a-zA-Z0-9_-]{11})"
        );
        Matcher matcher = pattern.matcher(videoUrl);
        return matcher.find() ? matcher.group(1) : null;
    }

    /** 서버에 업로드한 파일(영상·음성)이면 true. 템플릿에서는 sermon.uploadedFile로 쓴다. */
    public boolean isUploadedFile() {
        return videoUrl != null && videoUrl.startsWith(UPLOAD_URL_PREFIX);
    }

    /** 업로드 파일이 음성(mp3·m4a)이면 true. 상세 페이지에서 audio 태그로 재생한다. */
    public boolean isAudioFile() {
        if (!isUploadedFile()) return false;
        String lower = videoUrl.toLowerCase();
        return lower.endsWith(".mp3") || lower.endsWith(".m4a");
    }
}
