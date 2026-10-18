package com.church.website.service;

import com.church.website.entity.Sermon;
import com.church.website.exception.EntityNotFoundException;
import com.church.website.repository.SermonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 설교 영상 비즈니스 로직 서비스.
 *
 * <p>설교 미디어는 YouTube URL 또는 서버 업로드 파일(영상·음성) 중 하나로 등록한다.
 * 두 경우 모두 Sermon.videoUrl 한 컬럼에 저장하고, 업로드 파일은 "/uploads/sermon/" 접두사로 구분한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SermonService {

    /** 관리자 폼에서 고른 영상 등록 방식. */
    public enum VideoSource { YOUTUBE, UPLOAD }

    // 브라우저가 변환 없이 재생하는 형식만 받는다. mov(HEVC)는 크롬에서 재생되지 않는다.
    // MIME은 브라우저마다 m4a를 audio/mp4·audio/x-m4a 등으로 달리 보내서 종류(audio/·video/)만 본다.
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".mp4", ".webm", ".mp3", ".m4a");
    private static final String FORMAT_HINT = "(허용: mp4, webm, mp3, m4a)";

    private final SermonRepository sermonRepository;

    @Value("${file.upload.sermon.dir}")
    private String uploadDir;

    /**
     * 성경 본문 검색 + 페이지네이션으로 설교 목록을 반환한다.
     * biblePassage가 비어있으면 전체를 설교 날짜 내림차순으로 반환한다.
     */
    @Transactional(readOnly = true)
    public Page<Sermon> getSermons(String biblePassage, Pageable pageable) {
        return sermonRepository.searchSermons(biblePassage, pageable);
    }

    /** 메인 페이지용 최신 설교 5건을 반환한다. */
    @Transactional(readOnly = true)
    public List<Sermon> getRecentSermons() {
        return sermonRepository.findTop5ByOrderBySermonDateDesc();
    }

    /**
     * ID로 단건 조회.
     *
     * @throws EntityNotFoundException 해당 id가 존재하지 않으면 발생
     */
    @Transactional(readOnly = true)
    public Sermon getById(Long id) {
        return sermonRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("설교를 찾을 수 없습니다."));
    }

    /** 전체 설교 목록. */
    @Transactional(readOnly = true)
    public List<Sermon> getAll() {
        return sermonRepository.findAll();
    }

    /**
     * 설교 등록·수정 공용.
     * 수정 때 같은 방식으로 새 값을 안 넣으면 기존 영상을 유지한다.
     * 방식을 바꾸는데 새 값이 없으면 IllegalArgumentException을 던진다.
     */
    @Transactional
    public Sermon save(Sermon form, VideoSource source, MultipartFile videoFile) throws IOException {
        boolean isNew = form.getId() == null;
        Sermon sermon = isNew ? new Sermon() : getById(form.getId());
        String oldVideoUrl = sermon.getVideoUrl();

        String newVideoUrl = resolveVideoUrl(sermon, form, source, videoFile);

        sermon.setTitle(form.getTitle());
        sermon.setPreacher(form.getPreacher());
        sermon.setSermonDate(form.getSermonDate());
        sermon.setBiblePassage(form.getBiblePassage());
        sermon.setDescription(form.getDescription());
        sermon.setThumbnailUrl(
                (form.getThumbnailUrl() != null && !form.getThumbnailUrl().isBlank())
                        ? form.getThumbnailUrl() : null);
        sermon.setVideoUrl(newVideoUrl);

        Sermon saved = sermonRepository.save(sermon);
        if (oldVideoUrl != null && !oldVideoUrl.equals(newVideoUrl)) {
            deleteVideo(oldVideoUrl);
        }
        log.info("설교 {}: {}", isNew ? "등록" : "수정", saved.getTitle());
        return saved;
    }

    /** 이번 저장에서 쓸 videoUrl을 정한다. 새 파일이 있으면 이 단계에서 디스크에 저장한다. */
    private String resolveVideoUrl(Sermon current, Sermon form, VideoSource source,
                                   MultipartFile videoFile) throws IOException {
        if (source == VideoSource.UPLOAD) {
            if (videoFile != null && !videoFile.isEmpty()) return saveVideo(videoFile);
            if (current.isUploadedFile()) return current.getVideoUrl();
            throw new IllegalArgumentException("업로드할 영상 또는 음성 파일을 선택해주세요.");
        }
        String url = form.getVideoUrl();
        if (url != null && !url.isBlank()) return url.trim();
        if (current.getVideoUrl() != null && !current.isUploadedFile()) return current.getVideoUrl();
        throw new IllegalArgumentException("YouTube URL을 입력해주세요.");
    }

    /**
     * 설교 삭제. 업로드한 영상 파일도 함께 지운다.
     *
     * @throws EntityNotFoundException 해당 id가 존재하지 않으면 발생
     */
    @Transactional
    public void delete(Long id) {
        Sermon sermon = getById(id);
        log.info("설교 삭제: {}", sermon.getTitle());
        sermonRepository.delete(sermon);
        deleteVideo(sermon.getVideoUrl());
    }

    private String saveVideo(MultipartFile file) throws IOException {
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!contentType.startsWith("video/") && !contentType.startsWith("audio/")) {
            throw new IllegalArgumentException("영상 또는 음성 파일만 올릴 수 있습니다. " + FORMAT_HINT);
        }
        String originalName = file.getOriginalFilename();
        String ext = (originalName != null && originalName.contains("."))
                ? originalName.substring(originalName.lastIndexOf(".")).toLowerCase() : "";
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("허용되지 않는 파일 형식입니다. " + FORMAT_HINT);
        }

        Path dir = Paths.get(uploadDir);
        Files.createDirectories(dir);
        String savedName = UUID.randomUUID().toString().replace("-", "") + ext;
        Path dest = dir.resolve(savedName);
        file.transferTo(dest.toFile());

        log.info("설교 미디어 저장: {}", dest);
        return Sermon.UPLOAD_URL_PREFIX + savedName;
    }

    /** 업로드 영상 파일을 지운다. YouTube URL이거나 업로드 폴더 밖을 가리키면 무시한다. */
    void deleteVideo(String videoUrl) {
        if (videoUrl == null || !videoUrl.startsWith(Sermon.UPLOAD_URL_PREFIX)) return;
        try {
            String filename = videoUrl.substring(Sermon.UPLOAD_URL_PREFIX.length());
            Path uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path target = uploadRoot.resolve(filename).normalize();
            if (!target.startsWith(uploadRoot)) {
                log.warn("허용되지 않는 파일 경로 접근 차단: {}", videoUrl);
                return;
            }
            Files.deleteIfExists(target);
            log.info("설교 영상 삭제: {}", target);
        } catch (IOException e) {
            log.warn("설교 영상 삭제 실패: {}", videoUrl, e);
        }
    }
}
