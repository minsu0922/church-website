package com.church.website.controller;

import com.church.website.entity.Bulletin;
import com.church.website.entity.ChurchInfo;
import com.church.website.entity.Department;
import com.church.website.entity.Location;
import com.church.website.entity.MinistryPhoto;
import com.church.website.entity.NewFamily;
import com.church.website.entity.Notice;
import com.church.website.entity.Pastor;
import com.church.website.entity.Sermon;
import com.church.website.entity.StaffMember;
import com.church.website.entity.User;
import com.church.website.entity.WorshipSchedule;
import com.church.website.service.BulletinService;
import com.church.website.service.ChurchInfoService;
import com.church.website.service.DepartmentService;
import com.church.website.service.LocationService;
import com.church.website.service.MinistryPhotoService;
import com.church.website.service.NewFamilyService;
import com.church.website.service.NoticeService;
import com.church.website.service.PastorService;
import com.church.website.service.SermonService;
import com.church.website.service.StaffMemberService;
import com.church.website.service.UserService;
import com.church.website.service.WorshipScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 페이지 전체를 담당하는 컨트롤러.
 *
 * <p>/admin/** 경로 아래의 모든 관리 기능을 처리한다.
 * SecurityConfig에서 ROLE_ADMIN 권한을 가진 사용자만 접근할 수 있도록 제한되어 있다.
 *
 * <p>관리 기능 목록:
 * <ul>
 *   <li>대시보드  : /admin — 공지사항·새가족 현황 요약</li>
 *   <li>공지사항  : /admin/notices — 등록·수정·삭제·팝업 설정</li>
 *   <li>사역사진  : /admin/ministry — 사진 업로드·수정·삭제</li>
 *   <li>새가족    : /admin/new-family — 등록 목록 조회·확인 처리·메모</li>
 *   <li>설교      : /admin/sermon — YouTube URL 또는 영상 업로드로 설교 등록·수정·삭제</li>
 *   <li>계정      : /admin/account — 관리자 계정 추가·비밀번호 변경·활성화 토글</li>
 *   <li>설정      : /admin/settings — 교회 위치·연락처 정보 저장</li>
 *   <li>주보      : /admin/bulletin — 주보 등록·수정·인쇄</li>
 * </ul>
 *
 * <p>모든 POST 핸들러는 부모 클래스 {@link BaseController#run}을 통해
 * 예외 처리와 flash 메시지 처리를 일관되게 수행한다.
 */
@Slf4j
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController extends BaseController {

    // application.properties의 kakao.map.api.key 값을 주입받아 설정 페이지 뷰에 전달한다
    @Value("${kakao.map.api.key}")
    private String kakaoMapApiKey;

    private final NoticeService noticeService;
    private final NewFamilyService newFamilyService;
    private final MinistryPhotoService ministryPhotoService;
    private final UserService userService;
    private final LocationService locationService;
    private final SermonService sermonService;
    private final BulletinService bulletinService;
    private final ChurchInfoService churchInfoService;
    private final PastorService pastorService;
    private final StaffMemberService staffMemberService;
    private final WorshipScheduleService worshipScheduleService;
    private final DepartmentService departmentService;

    // ====== 공통: 사이드바 뱃지용 미확인 건수 ======

    /**
     * 모든 관리자 페이지 요청마다 자동으로 실행되어 Model에 공통 속성을 추가한다.
     * 사이드바의 새가족 미확인 뱃지 숫자를 매 요청마다 최신 값으로 유지하기 위해 사용한다.
     */
    @ModelAttribute
    public void addCommonAttributes(Model model) {
        model.addAttribute("uncheckedCount", newFamilyService.getUncheckedCount());
    }

    // ====== 대시보드 ======

    /** 관리자 메인 대시보드. 공지·사역사진·새가족 건수와 최근 목록을 요약해서 보여준다. */
    @GetMapping("")
    public String adminMain(Model model) {
        model.addAttribute("noticeCount",       noticeService.getTotalCount());
        model.addAttribute("ministryCount",     ministryPhotoService.getTotalCount());
        model.addAttribute("newFamilyTotal",    newFamilyService.getTotalCount());
        model.addAttribute("recentNotices",     noticeService.getTop5RecentNotices());
        model.addAttribute("recentNewFamilies", newFamilyService.getTop5UncheckedForDashboard());
        return "admin/index";
    }

    // ====== 공지사항 관리 ======

    /**
     * 공지사항 목록 (검색·팝업 상태 필터·페이지네이션).
     * keyword는 제목 부분 일치, popupStatus는 "on"/"off"/"" 세 값을 받는다.
     */
    @GetMapping("/notices")
    public String noticeList(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "") String popupStatus,
            @RequestParam(defaultValue = "0") int page,
            Model model) {
        Page<Notice> noticePage = noticeService.searchNoticesPaged(
                keyword, popupStatus,
                PageRequest.of(page, 20, Sort.by("createdAt").descending()));
        model.addAttribute("notices",     noticePage);
        model.addAttribute("keyword",     keyword);
        model.addAttribute("popupStatus", popupStatus);
        model.addAttribute("totalCount",  noticeService.getTotalCount());
        return "admin/notice/list";
    }

    /** 공지사항 등록 폼 — 빈 Notice 객체를 Model에 담아 Thymeleaf 바인딩에 사용한다. */
    @GetMapping("/notices/new")
    public String noticeForm(Model model) {
        model.addAttribute("notice", new Notice());
        return "admin/notice/form";
    }

    /** 공지사항 수정 폼 — 기존 데이터를 조회해 폼 초기값으로 채운다. */
    @GetMapping("/notices/edit/{id}")
    public String noticeEditForm(@PathVariable Long id, Model model) {
        model.addAttribute("notice", noticeService.getNoticeByIdNoCount(id));
        return "admin/notice/form";
    }

    /**
     * 공지사항 저장 (등록/수정 공용).
     * id가 null이면 신규 등록, 값이 있으면 수정으로 분기한다.
     * popupEnabled는 체크박스라 체크 해제 시 파라미터 자체가 오지 않으므로 required=false.
     */
    @PostMapping("/notices/save")
    public String noticeSave(
            @ModelAttribute Notice notice,
            @RequestParam(value = "popupEnabled", required = false) String popupEnabled,
            @RequestParam(value = "popupStartDate", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime popupStartDate,
            @RequestParam(value = "popupEndDate", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime popupEndDate,
            Authentication auth,
            RedirectAttributes redirectAttributes) {
        return run(() -> {
            notice.setPopup("true".equals(popupEnabled));
            notice.setPopupStartDate(popupStartDate);
            notice.setPopupEndDate(popupEndDate);
            if (notice.getId() == null) {
                notice.setAuthor(auth.getName());
                noticeService.createNotice(notice);
                redirectAttributes.addFlashAttribute("message", "공지사항이 등록되었습니다.");
            } else {
                noticeService.updateNotice(notice.getId(), notice);
                redirectAttributes.addFlashAttribute("message", "공지사항이 수정되었습니다.");
            }
            return "redirect:/admin/notices";
        }, "/admin/notices", "공지사항 저장 실패", redirectAttributes);
    }

    /** 공지사항 삭제. */
    @PostMapping("/notices/delete/{id}")
    public String noticeDelete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        return run(() -> {
            noticeService.deleteNotice(id);
            redirectAttributes.addFlashAttribute("message", "공지사항이 삭제되었습니다.");
            return "redirect:/admin/notices";
        }, "/admin/notices", "공지사항 삭제 실패", redirectAttributes);
    }

    // ====== 사역사진 관리 ======

    /** 사역사진 전체 목록 (표시 순서 기준). */
    @GetMapping("/ministry")
    public String ministryList(Model model) {
        model.addAttribute("photos", ministryPhotoService.getAllPhotos());
        return "admin/ministry/list";
    }

    /** 사역사진 등록 폼. */
    @GetMapping("/ministry/new")
    public String ministryForm(Model model) {
        model.addAttribute("photo", new MinistryPhoto());
        return "admin/ministry/form";
    }

    /** 사역사진 수정 폼. */
    @GetMapping("/ministry/edit/{id}")
    public String ministryEditForm(@PathVariable Long id, Model model) {
        model.addAttribute("photo", ministryPhotoService.getPhotoById(id));
        return "admin/ministry/form";
    }

    /**
     * 사역사진 저장 (등록/수정 공용).
     * 파일이 첨부된 경우에만 서버에 저장하고 URL을 교체한다.
     * 신규 등록 시 파일 없으면 오류 처리.
     */
    @PostMapping("/ministry/save")
    public String ministrySave(@ModelAttribute MinistryPhoto photo,
                               @RequestParam(value = "isActive", required = false) String isActiveStr,
                               @RequestParam(value = "photoFile", required = false) MultipartFile photoFile,
                               RedirectAttributes redirectAttributes) {
        return run(() -> {
            photo.setIsActive("true".equals(isActiveStr));
            String uploadedUrl = (photoFile != null && !photoFile.isEmpty())
                    ? ministryPhotoService.saveFile(photoFile) : null;
            if (photo.getId() == null) {
                if (uploadedUrl == null) {
                    redirectAttributes.addFlashAttribute("error", "사진 파일을 선택해주세요.");
                    return "redirect:/admin/ministry/new";
                }
                photo.setPhotoUrl(uploadedUrl);
                ministryPhotoService.createPhoto(photo);
                redirectAttributes.addFlashAttribute("message", "사역사진이 등록되었습니다.");
            } else {
                ministryPhotoService.updatePhoto(photo.getId(), photo, uploadedUrl);
                redirectAttributes.addFlashAttribute("message", "사역사진이 수정되었습니다.");
            }
            return "redirect:/admin/ministry";
        }, "/admin/ministry", "사역사진 저장 실패", redirectAttributes);
    }

    /**
     * 사역사진 삭제.
     * DB 레코드를 먼저 삭제한 뒤 실제 파일을 삭제한다.
     * (DB 삭제 실패 시 파일이 남아있는 편이 파일만 남고 레코드가 사라지는 것보다 낫다.)
     */
    @PostMapping("/ministry/delete/{id}")
    public String ministryDelete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        return run(() -> {
            MinistryPhoto photo = ministryPhotoService.getPhotoById(id);
            String photoUrl = photo.getPhotoUrl();
            ministryPhotoService.deletePhoto(id);         // DB 먼저
            ministryPhotoService.deleteFile(photoUrl);    // 성공 후 파일 삭제
            redirectAttributes.addFlashAttribute("message", "사역사진이 삭제되었습니다.");
            return "redirect:/admin/ministry";
        }, "/admin/ministry", "사역사진 삭제 실패", redirectAttributes);
    }

    // ====== 새가족 관리 ======

    /**
     * 새가족 목록 (이름 키워드·확인 상태 필터·페이지네이션).
     * status는 "checked"/"unchecked"/"" 세 값을 받는다.
     */
    @GetMapping("/new-family")
    public String newFamilyList(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "0") int page,
            Model model) {
        int size = 10;
        Page<NewFamily> result = newFamilyService.search(keyword, status, page, size);
        model.addAttribute("list",          result.getContent());
        model.addAttribute("currentPage",   result.getNumber());
        model.addAttribute("totalPages",    result.getTotalPages());
        model.addAttribute("totalElements", result.getTotalElements());
        model.addAttribute("keyword",       keyword);
        model.addAttribute("status",        status);
        model.addAttribute("checkedCount",  newFamilyService.getCheckedCount());
        return "admin/new-family/list";
    }

    /** 새가족 상세 조회. */
    @GetMapping("/new-family/{id}")
    public String newFamilyDetail(@PathVariable Long id, Model model) {
        model.addAttribute("nf", newFamilyService.getById(id));
        return "admin/new-family/detail";
    }

    /**
     * 새가족 확인 처리 (checked = true).
     * from 파라미터로 목록에서 왔는지 상세에서 왔는지 구분하여 돌아갈 URL을 결정한다.
     */
    @PostMapping("/new-family/check/{id}")
    public String newFamilyCheck(@PathVariable Long id,
                                 @RequestParam(defaultValue = "") String keyword,
                                 @RequestParam(defaultValue = "") String status,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "list") String from,
                                 RedirectAttributes redirectAttributes) {
        return run(() -> {
            newFamilyService.check(id);
            redirectAttributes.addFlashAttribute("message", "확인 처리되었습니다.");
            return "detail".equals(from)
                    ? "redirect:/admin/new-family/" + id
                    : newFamilyListUrl(keyword, status, page);
        }, "/admin/new-family", "새가족 확인 처리 실패", redirectAttributes);
    }

    /** 새가족 확인 취소 (checked = false). */
    @PostMapping("/new-family/uncheck/{id}")
    public String newFamilyUncheck(@PathVariable Long id,
                                   @RequestParam(defaultValue = "") String keyword,
                                   @RequestParam(defaultValue = "") String status,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "list") String from,
                                   RedirectAttributes redirectAttributes) {
        return run(() -> {
            newFamilyService.uncheck(id);
            redirectAttributes.addFlashAttribute("message", "확인이 취소되었습니다.");
            return "detail".equals(from)
                    ? "redirect:/admin/new-family/" + id
                    : newFamilyListUrl(keyword, status, page);
        }, "/admin/new-family", "새가족 확인 취소 실패", redirectAttributes);
    }

    /** 관리자 내부 메모 저장. */
    @PostMapping("/new-family/admin-memo/{id}")
    public String newFamilyAdminMemo(@PathVariable Long id,
                                     @RequestParam(defaultValue = "") String adminMemo,
                                     RedirectAttributes redirectAttributes) {
        return run(() -> {
            newFamilyService.saveAdminMemo(id, adminMemo);
            redirectAttributes.addFlashAttribute("message", "메모가 저장되었습니다.");
            return "redirect:/admin/new-family/" + id;
        }, "/admin/new-family/" + id, "새가족 메모 저장 실패", redirectAttributes);
    }

    /** 새가족 레코드 삭제. 삭제 후 현재 검색·페이지 상태를 유지한 채 목록으로 돌아간다. */
    @PostMapping("/new-family/delete/{id}")
    public String newFamilyDelete(@PathVariable Long id,
                                  @RequestParam(defaultValue = "") String keyword,
                                  @RequestParam(defaultValue = "") String status,
                                  @RequestParam(defaultValue = "0") int page,
                                  RedirectAttributes redirectAttributes) {
        return run(() -> {
            newFamilyService.delete(id);
            redirectAttributes.addFlashAttribute("message", "삭제되었습니다.");
            return newFamilyListUrl(keyword, status, page);
        }, "/admin/new-family", "새가족 삭제 실패", redirectAttributes);
    }

    /**
     * 새가족 목록 URL을 생성하는 유틸 메서드.
     * 한글 keyword가 URL에 그대로 들어가면 깨질 수 있으므로 URLEncoder로 인코딩한다.
     */
    private String newFamilyListUrl(String keyword, String status, int page) {
        String enc = URLEncoder.encode(keyword == null ? "" : keyword, StandardCharsets.UTF_8);
        return "redirect:/admin/new-family?keyword=" + enc + "&status=" + status + "&page=" + page;
    }

    // ====== 설교 관리 ======

    /**
     * 설교 목록 (성경 본문 검색·페이지네이션).
     * 설교 날짜 내림차순으로 정렬한다.
     */
    @GetMapping("/sermon")
    public String sermonList(
            @RequestParam(defaultValue = "") String biblePassage,
            @RequestParam(defaultValue = "0") int page,
            Model model) {
        int size = 10;
        PageRequest pageable = PageRequest.of(page, size, Sort.by("sermonDate").descending());
        Page<Sermon> result = sermonService.getSermons(biblePassage, pageable);
        model.addAttribute("sermons",       result.getContent());
        model.addAttribute("currentPage",   result.getNumber());
        model.addAttribute("totalPages",    result.getTotalPages());
        model.addAttribute("totalElements", result.getTotalElements());
        model.addAttribute("biblePassage",  biblePassage);
        return "admin/sermon/list";
    }

    /** 설교 등록 폼. */
    @GetMapping("/sermon/new")
    public String sermonForm(Model model) {
        model.addAttribute("sermon", new Sermon());
        return "admin/sermon/form";
    }

    /** 설교 수정 폼. */
    @GetMapping("/sermon/edit/{id}")
    public String sermonEditForm(@PathVariable Long id, Model model) {
        model.addAttribute("sermon", sermonService.getById(id));
        return "admin/sermon/form";
    }

    /**
     * 설교 저장 (등록/수정 공용).
     * 날짜는 HTML date 입력값(문자열)을 직접 파싱한다.
     * 영상은 videoSource에 따라 YouTube URL 또는 업로드 파일로 받는다.
     */
    @PostMapping("/sermon/save")
    public String sermonSave(
            @ModelAttribute Sermon sermon,
            @RequestParam(value = "sermonDateStr", required = false) String sermonDateStr,
            @RequestParam(value = "videoSource", defaultValue = "YOUTUBE") SermonService.VideoSource videoSource,
            @RequestParam(value = "videoFile", required = false) MultipartFile videoFile,
            RedirectAttributes redirectAttributes) {
        boolean isNew = sermon.getId() == null;
        String formPath = isNew ? "/admin/sermon/new" : "/admin/sermon/edit/" + sermon.getId();
        return run(() -> {
            if (sermonDateStr != null && !sermonDateStr.isBlank()) {
                sermon.setSermonDate(LocalDate.parse(sermonDateStr));
            }
            sermonService.save(sermon, videoSource, videoFile);
            redirectAttributes.addFlashAttribute("message", isNew ? "설교가 등록되었습니다." : "설교가 수정되었습니다.");
            return "redirect:/admin/sermon";
        }, formPath, "설교 저장 실패", redirectAttributes);
    }

    /** 설교 삭제. */
    @PostMapping("/sermon/delete/{id}")
    public String sermonDelete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        return run(() -> {
            sermonService.delete(id);
            redirectAttributes.addFlashAttribute("message", "설교가 삭제되었습니다.");
            return "redirect:/admin/sermon";
        }, "/admin/sermon", "설교 삭제 실패", redirectAttributes);
    }

    // ====== 계정 관리 ======

    /** 관리자 계정 목록. 현재 로그인한 계정은 비활성화·삭제 버튼이 비활성화된다. */
    @GetMapping("/account")
    public String accountList(Model model, Authentication auth) {
        model.addAttribute("users", userService.getAllUsers());
        model.addAttribute("currentUsername", auth.getName());
        return "admin/account/list";
    }

    /**
     * 비밀번호 변경.
     * 현재 비밀번호 일치 여부와 새 비밀번호 확인 일치 여부를 검증한다.
     * 성공 시 재로그인 안내 메시지를 표시한다 (세션은 즉시 만료되지 않음).
     */
    @PostMapping("/account/change-password")
    public String changePassword(
            @RequestParam String currentPassword,
            @RequestParam String newPassword,
            @RequestParam String confirmPassword,
            Authentication auth,
            RedirectAttributes redirectAttributes) {
        return run(() -> {
            if (!newPassword.equals(confirmPassword)) {
                redirectAttributes.addFlashAttribute("error", "새 비밀번호가 일치하지 않습니다.");
                return "redirect:/admin/account";
            }
            userService.changePassword(auth.getName(), currentPassword, newPassword);
            redirectAttributes.addFlashAttribute("message", "비밀번호가 변경되었습니다. 다시 로그인해주세요.");
            return "redirect:/admin/account";
        }, "/admin/account", "비밀번호 변경 실패", redirectAttributes);
    }

    /** 계정 추가. 사용자명 중복 및 비밀번호 규칙 검증은 UserService에서 수행한다. */
    @PostMapping("/account/create")
    public String createAccount(
            @RequestParam String username,
            @RequestParam String password,
            RedirectAttributes redirectAttributes) {
        return run(() -> {
            userService.createUser(username, password);
            redirectAttributes.addFlashAttribute("message", "계정이 추가되었습니다: " + username);
            return "redirect:/admin/account";
        }, "/admin/account", "계정 추가 실패", redirectAttributes);
    }

    /**
     * 계정 활성/비활성 토글.
     * 현재 로그인 중인 계정은 본인 스스로 비활성화할 수 없도록 방어 처리한다.
     */
    @PostMapping("/account/toggle/{id}")
    public String toggleAccount(@PathVariable Long id, Authentication auth,
                                RedirectAttributes redirectAttributes) {
        return run(() -> {
            User target = userService.getUserById(id);
            if (target.getUsername().equals(auth.getName())) {
                redirectAttributes.addFlashAttribute("error", "현재 로그인 계정은 비활성화할 수 없습니다.");
                return "redirect:/admin/account";
            }
            userService.toggleEnabled(id);
            redirectAttributes.addFlashAttribute("message", "계정 상태가 변경되었습니다.");
            return "redirect:/admin/account";
        }, "/admin/account", "계정 상태 변경 실패", redirectAttributes);
    }

    /**
     * 계정 삭제.
     * 현재 로그인 중인 계정은 삭제할 수 없도록 방어 처리한다.
     */
    @PostMapping("/account/delete/{id}")
    public String deleteAccount(@PathVariable Long id, Authentication auth,
                                RedirectAttributes redirectAttributes) {
        return run(() -> {
            User target = userService.getUserById(id);
            if (target.getUsername().equals(auth.getName())) {
                redirectAttributes.addFlashAttribute("error", "현재 로그인 계정은 삭제할 수 없습니다.");
                return "redirect:/admin/account";
            }
            userService.deleteUser(id);
            redirectAttributes.addFlashAttribute("message", "계정이 삭제되었습니다.");
            return "redirect:/admin/account";
        }, "/admin/account", "계정 삭제 실패", redirectAttributes);
    }

    // ====== 설정 ======

    /** 교회 정보 설정 페이지. 활성화된 Location이 없으면 빈 객체로 폼을 채운다. */
    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute("location", locationService.getActiveLocation().orElse(new Location()));
        model.addAttribute("kakaoMapApiKey", kakaoMapApiKey);
        return "admin/settings";
    }

    /** 교회 정보 저장. LocationService의 upsert 방식으로 단일 레코드를 유지한다. */
    @PostMapping("/settings/save")
    public String settingsSave(@ModelAttribute Location location, RedirectAttributes redirectAttributes) {
        return run(() -> {
            locationService.save(location);
            redirectAttributes.addFlashAttribute("message", "교회 정보가 저장되었습니다.");
            return "redirect:/admin/settings";
        }, "/admin/settings", "설정 저장 실패", redirectAttributes);
    }

    // ====== 주보 관리 ======

    /** 주보 목록 (예배 날짜 내림차순·페이지네이션). */
    @GetMapping("/bulletin")
    public String bulletinList(
            @RequestParam(defaultValue = "0") int page,
            Model model) {
        Page<Bulletin> result = bulletinService.getAll(PageRequest.of(page, 10));
        model.addAttribute("bulletins",     result.getContent());
        model.addAttribute("currentPage",   result.getNumber());
        model.addAttribute("totalPages",    result.getTotalPages());
        model.addAttribute("totalElements", result.getTotalElements());
        return "admin/bulletin/list";
    }

    /**
     * 주보 등록 폼.
     * 다음 주일 날짜를 자동으로 계산하고, 직전 주보에서 반복되는 필드(사회자, 찬송 번호 등)를
     * 미리 채워 입력 수고를 줄인다.
     */
    @GetMapping("/bulletin/new")
    public String bulletinForm(Model model) {
        Bulletin bulletin = new Bulletin();

        // 다음 주일 자동 계산 — 일요일(7)이면 다음 주 일요일, 그 외에는 이번 주 일요일
        LocalDate today = LocalDate.now();
        int dow = today.getDayOfWeek().getValue(); // 1=Mon … 7=Sun
        int daysUntil = dow == 7 ? 7 : 7 - dow;
        LocalDate nextSunday = today.plusDays(daysUntil);
        bulletin.setWorshipDate(nextSunday);

        // 직전 주보에서 반복 값 인계 — null 체크 후 복사
        bulletinService.getLatest().ifPresent(last -> {
            if (last.getEmcee()          != null) bulletin.setEmcee(last.getEmcee());
            if (last.getHymn1()          != null) bulletin.setHymn1(last.getHymn1());
            if (last.getHymn2()          != null) bulletin.setHymn2(last.getHymn2());
            if (last.getHymn3()          != null) bulletin.setHymn3(last.getHymn3());
            if (last.getResponsiveNo()   != null) bulletin.setResponsiveNo(last.getResponsiveNo());
            if (last.getScriptureRange() != null) bulletin.setScriptureRange(last.getScriptureRange());
        });

        model.addAttribute("bulletin", bulletin);
        return "admin/bulletin/form";
    }

    /** 주보 수정 폼. */
    @GetMapping("/bulletin/edit/{id}")
    public String bulletinEditForm(@PathVariable Long id, Model model) {
        model.addAttribute("bulletin", bulletinService.getById(id));
        return "admin/bulletin/form";
    }

    /** 주보 인쇄 미리보기 — 별도 레이아웃 없이 인쇄에 최적화된 뷰를 렌더링한다. */
    @GetMapping("/bulletin/{id}/print")
    public String bulletinPrint(@PathVariable Long id, Model model) {
        model.addAttribute("bulletin", bulletinService.getById(id));
        return "admin/bulletin/print";
    }

    /** 주보 저장 (등록/수정 공용). id 유무로 JPA가 INSERT/UPDATE를 자동 구분한다. */
    @PostMapping("/bulletin/save")
    public String bulletinSave(
            @ModelAttribute Bulletin bulletin,
            RedirectAttributes redirectAttributes) {
        return run(() -> {
            bulletinService.save(bulletin);
            redirectAttributes.addFlashAttribute("message", "주보가 저장되었습니다.");
            return "redirect:/admin/bulletin";
        }, "/admin/bulletin", "주보 저장 실패", redirectAttributes);
    }

    /** 주보 삭제. */
    @PostMapping("/bulletin/delete/{id}")
    public String bulletinDelete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        return run(() -> {
            bulletinService.delete(id);
            redirectAttributes.addFlashAttribute("message", "주보가 삭제되었습니다.");
            return "redirect:/admin/bulletin";
        }, "/admin/bulletin", "주보 삭제 실패", redirectAttributes);
    }

    // ====== 교회소개 관리 ======

    @GetMapping("/church-info")
    public String churchInfoEdit(Model model) {
        model.addAttribute("churchInfo", churchInfoService.getActive().orElse(new ChurchInfo()));
        return "admin/church-info/edit";
    }

    @PostMapping("/church-info/save")
    public String churchInfoSave(@ModelAttribute ChurchInfo churchInfo, RedirectAttributes redirectAttributes) {
        return run(() -> {
            churchInfoService.save(churchInfo);
            redirectAttributes.addFlashAttribute("message", "교회소개가 저장되었습니다.");
            return "redirect:/admin/church-info";
        }, "/admin/church-info", "교회소개 저장 실패", redirectAttributes);
    }

    // ====== 담임목사 관리 ======

    @GetMapping("/pastor")
    public String pastorEdit(Model model) {
        model.addAttribute("pastor", pastorService.getActive().orElse(new Pastor()));
        return "admin/pastor/edit";
    }

    @PostMapping("/pastor/save")
    public String pastorSave(
            @ModelAttribute Pastor pastor,
            @RequestParam(value = "photoFile", required = false) MultipartFile photoFile,
            RedirectAttributes redirectAttributes) {
        return run(() -> {
            String newPhotoUrl = (photoFile != null && !photoFile.isEmpty())
                    ? pastorService.savePhoto(photoFile) : null;
            pastorService.save(pastor, newPhotoUrl);
            redirectAttributes.addFlashAttribute("message", "담임목사 정보가 저장되었습니다.");
            return "redirect:/admin/pastor";
        }, "/admin/pastor", "담임목사 저장 실패", redirectAttributes);
    }

    // ====== 교역자 관리 ======

    @GetMapping("/staff")
    public String staffList(Model model) {
        model.addAttribute("staffList", staffMemberService.getAll());
        return "admin/staff/list";
    }

    @GetMapping("/staff/new")
    public String staffForm(Model model) {
        model.addAttribute("staff", new StaffMember());
        return "admin/staff/form";
    }

    @GetMapping("/staff/edit/{id}")
    public String staffEditForm(@PathVariable Long id, Model model) {
        model.addAttribute("staff", staffMemberService.getById(id));
        return "admin/staff/form";
    }

    @PostMapping("/staff/save")
    public String staffSave(
            @ModelAttribute StaffMember staff,
            @RequestParam(value = "isActive", required = false) String isActiveStr,
            @RequestParam(value = "photoFile", required = false) MultipartFile photoFile,
            RedirectAttributes redirectAttributes) {
        return run(() -> {
            staff.setIsActive("true".equals(isActiveStr));
            String newPhotoUrl = (photoFile != null && !photoFile.isEmpty())
                    ? staffMemberService.savePhoto(photoFile) : null;
            if (staff.getId() == null) {
                if (newPhotoUrl != null) staff.setPhotoUrl(newPhotoUrl);
                staffMemberService.create(staff);
                redirectAttributes.addFlashAttribute("message", "교역자가 등록되었습니다.");
            } else {
                staffMemberService.update(staff.getId(), staff, newPhotoUrl);
                redirectAttributes.addFlashAttribute("message", "교역자 정보가 수정되었습니다.");
            }
            return "redirect:/admin/staff";
        }, "/admin/staff", "교역자 저장 실패", redirectAttributes);
    }

    @PostMapping("/staff/delete/{id}")
    public String staffDelete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        return run(() -> {
            staffMemberService.delete(id);
            redirectAttributes.addFlashAttribute("message", "교역자가 삭제되었습니다.");
            return "redirect:/admin/staff";
        }, "/admin/staff", "교역자 삭제 실패", redirectAttributes);
    }

    @PostMapping("/staff/{id}/move-up")
    public String staffMoveUp(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        return run(() -> {
            staffMemberService.moveUp(id);
            return "redirect:/admin/staff";
        }, "/admin/staff", "순서 변경 실패", redirectAttributes);
    }

    @PostMapping("/staff/{id}/move-down")
    public String staffMoveDown(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        return run(() -> {
            staffMemberService.moveDown(id);
            return "redirect:/admin/staff";
        }, "/admin/staff", "순서 변경 실패", redirectAttributes);
    }

    // ====== 예배 시간표 관리 ======

    @GetMapping("/worship")
    public String worshipList(Model model) {
        model.addAttribute("schedules", worshipScheduleService.getAll());
        return "admin/worship/list";
    }

    @GetMapping("/worship/new")
    public String worshipForm(Model model) {
        model.addAttribute("schedule", new WorshipSchedule());
        return "admin/worship/form";
    }

    @GetMapping("/worship/edit/{id}")
    public String worshipEditForm(@PathVariable Long id, Model model) {
        model.addAttribute("schedule", worshipScheduleService.getById(id));
        return "admin/worship/form";
    }

    @PostMapping("/worship/save")
    public String worshipSave(
            @ModelAttribute WorshipSchedule schedule,
            @RequestParam(value = "isActive", required = false) String isActiveStr,
            RedirectAttributes redirectAttributes) {
        return run(() -> {
            schedule.setIsActive("true".equals(isActiveStr));
            if (schedule.getId() == null) {
                worshipScheduleService.create(schedule);
                redirectAttributes.addFlashAttribute("message", "예배가 등록되었습니다.");
            } else {
                worshipScheduleService.update(schedule.getId(), schedule);
                redirectAttributes.addFlashAttribute("message", "예배 정보가 수정되었습니다.");
            }
            return "redirect:/admin/worship";
        }, "/admin/worship", "예배 저장 실패", redirectAttributes);
    }

    @PostMapping("/worship/delete/{id}")
    public String worshipDelete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        return run(() -> {
            worshipScheduleService.delete(id);
            redirectAttributes.addFlashAttribute("message", "예배가 삭제되었습니다.");
            return "redirect:/admin/worship";
        }, "/admin/worship", "예배 삭제 실패", redirectAttributes);
    }

    @PostMapping("/worship/{id}/move-up")
    public String worshipMoveUp(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        return run(() -> {
            worshipScheduleService.moveUp(id);
            return "redirect:/admin/worship";
        }, "/admin/worship", "순서 변경 실패", redirectAttributes);
    }

    @PostMapping("/worship/{id}/move-down")
    public String worshipMoveDown(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        return run(() -> {
            worshipScheduleService.moveDown(id);
            return "redirect:/admin/worship";
        }, "/admin/worship", "순서 변경 실패", redirectAttributes);
    }

    // ====== 부서 안내 관리 ======

    @GetMapping("/department")
    public String departmentList(Model model) {
        model.addAttribute("departments", departmentService.getAll());
        return "admin/department/list";
    }

    @GetMapping("/department/new")
    public String departmentForm(Model model) {
        model.addAttribute("dept", new Department());
        return "admin/department/form";
    }

    @GetMapping("/department/edit/{id}")
    public String departmentEditForm(@PathVariable Long id, Model model) {
        model.addAttribute("dept", departmentService.getById(id));
        return "admin/department/form";
    }

    @PostMapping("/department/save")
    public String departmentSave(
            @ModelAttribute Department dept,
            @RequestParam(value = "isActive", required = false) String isActiveStr,
            RedirectAttributes redirectAttributes) {
        return run(() -> {
            dept.setIsActive("true".equals(isActiveStr));
            if (dept.getId() == null) {
                departmentService.create(dept);
                redirectAttributes.addFlashAttribute("message", "부서가 등록되었습니다.");
            } else {
                departmentService.update(dept.getId(), dept);
                redirectAttributes.addFlashAttribute("message", "부서 정보가 수정되었습니다.");
            }
            return "redirect:/admin/department";
        }, "/admin/department", "부서 저장 실패", redirectAttributes);
    }

    @PostMapping("/department/delete/{id}")
    public String departmentDelete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        return run(() -> {
            departmentService.delete(id);
            redirectAttributes.addFlashAttribute("message", "부서가 삭제되었습니다.");
            return "redirect:/admin/department";
        }, "/admin/department", "부서 삭제 실패", redirectAttributes);
    }

    @PostMapping("/department/{id}/move-up")
    public String departmentMoveUp(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        return run(() -> {
            departmentService.moveUp(id);
            return "redirect:/admin/department";
        }, "/admin/department", "순서 변경 실패", redirectAttributes);
    }

    @PostMapping("/department/{id}/move-down")
    public String departmentMoveDown(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        return run(() -> {
            departmentService.moveDown(id);
            return "redirect:/admin/department";
        }, "/admin/department", "순서 변경 실패", redirectAttributes);
    }
}
