package com.church.website;

import com.church.website.repository.*;
import com.church.website.service.UserService;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 관리자·공개 화면 전체를 실제 사용 순서(등록 → 조회·수정 → 순서·상태 변경 → 삭제)로 지나가며
 * 오류 화면이나 오류 flash 메시지가 나오는 곳을 모두 모아 보고한다.
 * GlobalExceptionHandler가 오류 화면을 200으로 내므로 상태 코드 대신 뷰 이름으로 판별한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("사이트 전체 기능 점검")
class FullSiteSmokeTest {

    @TempDir
    static Path uploadRoot;

    @DynamicPropertySource
    static void uploadProps(DynamicPropertyRegistry r) {
        r.add("file.upload.dir", () -> uploadRoot.resolve("ministry").toString());
        r.add("file.upload.people.dir", () -> uploadRoot.resolve("people").toString());
        r.add("file.upload.sermon.dir", () -> uploadRoot.resolve("sermon").toString());
    }

    @Autowired private WebApplicationContext context;
    @Autowired private UserService userService;
    @Autowired private NoticeRepository noticeRepo;
    @Autowired private MinistryPhotoRepository ministryRepo;
    @Autowired private NewFamilyRepository newFamilyRepo;
    @Autowired private SermonRepository sermonRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private BulletinRepository bulletinRepo;
    @Autowired private StaffMemberRepository staffRepo;
    @Autowired private WorshipScheduleRepository worshipRepo;
    @Autowired private DepartmentRepository deptRepo;

    private MockMvc mvc;
    private final List<String> problems = new ArrayList<>();

    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3};

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void 전체_화면과_기능을_지나가며_오류를_모은다() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        userService.createUser("admin", "Admin1234");

        // ── 1. 등록 ──
        save("공지 등록", post("/admin/notices/save").param("title", "공지").param("content", "<p>내용</p>")
                .param("popupEnabled", "true").param("popupStartDate", "2026-10-01T00:00")
                .param("popupEndDate", "2026-12-31T23:59"), "/admin/notices");
        save("사역사진 등록", multipart("/admin/ministry/save").file(img("photoFile"))
                .param("category", "WORSHIP").param("title", "찬양").param("displayOrder", "0")
                .param("isActive", "true"), "/admin/ministry");
        save("새가족 등록(방문자)", post("/new-family/submit").param("name", "홍길동")
                .param("phone", "010-1234-5678").param("visitRoute", "지인 소개").param("memo", "메모"),
                "/new-family/complete");
        save("설교 등록(YouTube)", multipart("/admin/sermon/save").param("title", "설교").param("preacher", "목사")
                .param("sermonDateStr", "2026-10-18").param("biblePassage", "요한복음 3:16")
                .param("videoSource", "YOUTUBE").param("videoUrl", "https://youtu.be/abcdefghijk"), "/admin/sermon");
        save("설교 등록(파일)", multipart("/admin/sermon/save")
                .file(new MockMultipartFile("videoFile", "a.mp3", "audio/mpeg", JPG))
                .param("title", "음성 설교").param("preacher", "목사").param("sermonDateStr", "2026-10-11")
                .param("biblePassage", "시편 23편").param("videoSource", "UPLOAD"), "/admin/sermon");
        save("계정 추가", post("/admin/account/create").param("username", "staff2")
                .param("password", "Staff1234"), "/admin/account");
        save("교회 정보 저장", post("/admin/settings/save").param("churchName", "샘물교회")
                .param("address", "서울시 어딘가 1").param("phone", "02-000-0000")
                .param("latitude", "37.5").param("longitude", "127.0"), "/admin/settings");
        save("주보 등록", post("/admin/bulletin/save").param("worshipDate", "2026-10-25")
                .param("sermonTitle", "제목").param("biblePassage", "요 3:16").param("hymn1", "1"),
                "/admin/bulletin");
        save("교회소개 저장", post("/admin/church-info/save").param("vision", "비전")
                .param("mission", "사명").param("history", "연혁"), "/admin/church-info");
        save("담임목사 저장", multipart("/admin/pastor/save").file(img("photoFile"))
                .param("name", "김목사").param("role", "담임목사").param("greeting", "인사말"), "/admin/pastor");
        for (String name : new String[]{"교역자1", "교역자2"}) {
            save("교역자 등록 " + name, multipart("/admin/staff/save").file(img("photoFile"))
                    .param("name", name).param("role", "전도사").param("isActive", "true"), "/admin/staff");
        }
        for (String name : new String[]{"주일 1부", "수요 예배"}) {
            save("예배 등록 " + name, post("/admin/worship/save").param("name", name).param("category", "SUNDAY")
                    .param("serviceTime", "오전 11시").param("isActive", "true"), "/admin/worship");
        }
        for (String name : new String[]{"유치부", "청년부"}) {
            save("부서 등록 " + name, post("/admin/department/save").param("name", name)
                    .param("worshipTime", "오전 10시").param("location", "교육관").param("isActive", "true"),
                    "/admin/department");
        }

        Long noticeId = noticeRepo.findAll().getFirst().getId();
        Long ministryId = ministryRepo.findAll().getFirst().getId();
        Long nfId = newFamilyRepo.findAll().getFirst().getId();
        Long sermonId = sermonRepo.findAll().getFirst().getId();
        Long sermon2Id = sermonRepo.findAll().getLast().getId();
        Long bulletinId = bulletinRepo.findAll().getFirst().getId();
        Long staffId = staffRepo.findAll().getLast().getId();
        Long worshipId = worshipRepo.findAll().getLast().getId();
        Long deptId = deptRepo.findAll().getLast().getId();
        Long otherUserId = userRepo.findByUsername("staff2").orElseThrow().getId();

        // ── 2. 화면 열기 ──
        for (String url : List.of("/", "/about/church", "/about/pastor", "/about/staff", "/worship/schedule",
                "/worship/department", "/notice/list", "/notice/detail/" + noticeId, "/ministry", "/location",
                "/sermon", "/sermon/" + sermonId, "/sermon/" + sermon2Id, "/sermon?biblePassage=요한",
                "/new-family", "/new-family/complete", "/login",
                "/admin", "/admin/notices", "/admin/notices?keyword=공지&popupStatus=on", "/admin/notices/new",
                "/admin/notices/edit/" + noticeId, "/admin/ministry", "/admin/ministry/new",
                "/admin/ministry/edit/" + ministryId, "/admin/new-family", "/admin/new-family?keyword=홍&status=unchecked",
                "/admin/new-family/" + nfId, "/admin/sermon", "/admin/sermon?biblePassage=시편", "/admin/sermon/new",
                "/admin/sermon/edit/" + sermonId, "/admin/sermon/edit/" + sermon2Id, "/admin/account",
                "/admin/settings", "/admin/bulletin", "/admin/bulletin/new", "/admin/bulletin/edit/" + bulletinId,
                "/admin/bulletin/" + bulletinId + "/print", "/admin/church-info", "/admin/pastor", "/admin/staff",
                "/admin/staff/new", "/admin/staff/edit/" + staffId, "/admin/worship", "/admin/worship/new",
                "/admin/worship/edit/" + worshipId, "/admin/department", "/admin/department/new",
                "/admin/department/edit/" + deptId)) {
            page(url);
        }

        // ── 3. 수정·상태 변경 ──
        save("공지 수정", post("/admin/notices/save").param("id", noticeId.toString()).param("title", "공지2")
                .param("content", "내용2"), "/admin/notices");
        save("사역사진 수정", multipart("/admin/ministry/save").param("id", ministryId.toString())
                .param("category", "YOUTH").param("title", "청년").param("displayOrder", "1"), "/admin/ministry");
        save("새가족 확인", post("/admin/new-family/check/" + nfId), "/admin/new-family");
        save("새가족 확인 취소", post("/admin/new-family/uncheck/" + nfId).param("from", "detail"),
                "/admin/new-family/" + nfId);
        save("새가족 메모", post("/admin/new-family/admin-memo/" + nfId).param("adminMemo", "전화함"),
                "/admin/new-family/" + nfId);
        save("설교 수정", multipart("/admin/sermon/save").param("id", sermonId.toString()).param("title", "설교2")
                .param("preacher", "목사").param("sermonDateStr", "2026-10-18").param("biblePassage", "요 1:1")
                .param("videoSource", "YOUTUBE"), "/admin/sermon");
        save("주보 수정", post("/admin/bulletin/save").param("id", bulletinId.toString())
                .param("worshipDate", "2026-10-25").param("sermonTitle", "제목2").param("hymn1", "1"),
                "/admin/bulletin");
        save("교역자 수정", multipart("/admin/staff/save").param("id", staffId.toString()).param("name", "교역자2")
                .param("bio", "소개\n둘째 줄").param("isActive", "true"), "/admin/staff");
        save("교역자 위로", post("/admin/staff/" + staffId + "/move-up"), "/admin/staff");
        save("교역자 아래로", post("/admin/staff/" + staffId + "/move-down"), "/admin/staff");
        save("예배 수정", post("/admin/worship/save").param("id", worshipId.toString()).param("name", "수요 예배")
                .param("category", "WEEKDAY").param("isActive", "true"), "/admin/worship");
        save("예배 위로", post("/admin/worship/" + worshipId + "/move-up"), "/admin/worship");
        save("예배 아래로", post("/admin/worship/" + worshipId + "/move-down"), "/admin/worship");
        save("부서 수정", post("/admin/department/save").param("id", deptId.toString()).param("name", "청년부")
                .param("isActive", "true"), "/admin/department");
        save("부서 위로", post("/admin/department/" + deptId + "/move-up"), "/admin/department");
        save("부서 아래로", post("/admin/department/" + deptId + "/move-down"), "/admin/department");
        save("계정 비활성화", post("/admin/account/toggle/" + otherUserId), "/admin/account");
        save("비밀번호 변경", post("/admin/account/change-password").param("currentPassword", "Admin1234")
                .param("newPassword", "Admin5678").param("confirmPassword", "Admin5678"), "/admin/account");

        // 수정 폼에 없는 값이 수정 후 지워지지 않았는지 확인
        var notice = noticeRepo.findById(noticeId).orElseThrow();
        if (notice.getAuthor() == null) problems.add("[데이터] 공지 수정 후 작성자가 지워짐");
        if (notice.getCreatedAt() == null) problems.add("[데이터] 공지 수정 후 등록일이 지워짐");
        if (ministryRepo.findById(ministryId).orElseThrow().getPhotoUrl() == null)
            problems.add("[데이터] 사역사진 수정 후 사진이 지워짐");
        if (sermonRepo.findById(sermonId).orElseThrow().getVideoUrl() == null)
            problems.add("[데이터] 설교 수정 후 영상이 지워짐");
        var bulletin = bulletinRepo.findById(bulletinId).orElseThrow();
        if (bulletin.getCreatedAt() == null) problems.add("[데이터] 주보 수정 후 등록일(createdAt)이 지워짐");
        if (staffRepo.findById(staffId).orElseThrow().getPhotoUrl() == null)
            problems.add("[데이터] 교역자 수정 후 사진이 지워짐");
        if (newFamilyRepo.findById(nfId).orElseThrow().getAdminMemo() == null)
            problems.add("[데이터] 새가족 메모가 저장되지 않음");

        // 같은 설정을 두 번 저장해도 활성 데이터가 하나만 남는지 확인
        save("교회 정보 재저장", post("/admin/settings/save").param("churchName", "샘물교회")
                .param("address", "서울시 어딘가 2"), "/admin/settings");
        save("교회소개 재저장", post("/admin/church-info/save").param("vision", "비전2"), "/admin/church-info");
        save("담임목사 재저장(사진 없이)", multipart("/admin/pastor/save").param("name", "김목사")
                .param("greeting", "인사말2"), "/admin/pastor");
        page("/location");
        page("/about/church");
        String pastorHtml = mvc.perform(get("/about/pastor")).andReturn().getResponse().getContentAsString();
        if (!pastorHtml.contains("/uploads/people/")) problems.add("[데이터] 담임목사 재저장 후 사진이 사라짐");

        // 수정 후에도 화면이 열리는지 다시 확인
        for (String url : List.of("/", "/about/staff", "/notice/detail/" + noticeId, "/sermon/" + sermonId,
                "/admin/bulletin/edit/" + bulletinId, "/admin/new-family/" + nfId)) {
            page(url);
        }

        // ── 4. 삭제 ──
        save("공지 삭제", post("/admin/notices/delete/" + noticeId), "/admin/notices");
        save("사역사진 삭제", post("/admin/ministry/delete/" + ministryId), "/admin/ministry");
        save("새가족 삭제", post("/admin/new-family/delete/" + nfId), "/admin/new-family");
        save("설교 삭제", post("/admin/sermon/delete/" + sermon2Id), "/admin/sermon");
        save("주보 삭제", post("/admin/bulletin/delete/" + bulletinId), "/admin/bulletin");
        save("교역자 삭제", post("/admin/staff/delete/" + staffId), "/admin/staff");
        save("예배 삭제", post("/admin/worship/delete/" + worshipId), "/admin/worship");
        save("부서 삭제", post("/admin/department/delete/" + deptId), "/admin/department");
        save("계정 삭제", post("/admin/account/delete/" + otherUserId), "/admin/account");

        // 없는 데이터는 404 화면이어야 한다
        for (String url : List.of("/notice/detail/99999", "/sermon/99999", "/admin/notices/edit/99999")) {
            MvcResult r = mvc.perform(get(url)).andReturn();
            String view = r.getModelAndView() == null ? null : r.getModelAndView().getViewName();
            if (!"error/404".equals(view)) problems.add("[없는 데이터] " + url + " → view=" + view);
        }

        // 점검 장치 자체가 오류를 잡는지 확인: 없는 정적 파일은 오류 화면이 나와야 한다
        int before = problems.size();
        page("/uploads/people/does-not-exist.jpg");
        if (problems.size() == before) problems.add("[점검 장치] 오류 화면을 못 잡음");
        else System.out.println("SMOKE-SELFCHECK " + problems.removeLast());

        problems.forEach(p -> System.out.println("SMOKE " + p));
        assertThat(problems).isEmpty();
    }

    private MockMultipartFile img(String field) {
        return new MockMultipartFile(field, "p.jpg", "image/jpeg", JPG);
    }

    /** 폼 전송 후 기대한 곳으로 이동하고 오류 메시지가 없는지 확인한다. */
    private void save(String label, AbstractMockHttpServletRequestBuilder<?> req, String expectedRedirectPrefix) {
        try {
            MvcResult r = mvc.perform(req.with(csrf())).andReturn();
            String location = r.getResponse().getRedirectedUrl();
            Object error = r.getFlashMap().get("error");
            if (error != null) problems.add("[" + label + "] 오류 메시지: " + error);
            if (location == null || !location.startsWith(expectedRedirectPrefix)) {
                problems.add("[" + label + "] 이동 위치=" + location + " status=" + r.getResponse().getStatus()
                        + (r.getModelAndView() != null ? " view=" + r.getModelAndView().getViewName() : ""));
            }
        } catch (Exception e) {
            problems.add("[" + label + "] 예외: " + e);
        }
    }

    /** 화면이 200으로 열리고 오류 화면이 아닌지 확인한다. */
    private void page(String url) {
        try {
            MvcResult r = mvc.perform(get(url)).andReturn();
            int status = r.getResponse().getStatus();
            String view = r.getModelAndView() == null ? null : r.getModelAndView().getViewName();
            if (status != 200 || (view != null && view.startsWith("error/"))) {
                problems.add("[화면] " + url + " → status=" + status + " view=" + view
                        + (r.getResolvedException() != null ? " 예외=" + r.getResolvedException() : ""));
            }
        } catch (Exception e) {
            problems.add("[화면] " + url + " → 예외: " + e);
        }
    }
}
