# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 협업 지침

- 모든 대화는 현재 열려있는 이 IntelliJ 프로젝트(SpringBoot_MCP_Project)를 기반으로 한다.
- 응답 전 자체 검증 후 확실한 내용만 답변한다.

## 프로젝트 개요

샘물교회 공식 웹사이트. Spring Boot 4.0.2 + Thymeleaf + Spring Security + MySQL 기반의 교회 웹사이트로, 공개 페이지와 관리자 페이지로 구성된다.

- **Java 21**, Gradle, Spring Boot 4.0.2
- **DB**: 운영 MySQL (`church_db`), `DB_USERNAME` / `DB_PASSWORD`는 로컬은 `application-local.properties`, 운영은 환경변수로 주입
- **파일 업로드**: `${user.dir}/uploads/ministry`, `${user.dir}/uploads/people` (`file.upload.dir`, `file.upload.people.dir`)
- **카카오맵 API**: `kakao.map.api.key` — 실제 키는 `application-local.properties`(로컬) 또는 환경변수 `KAKAO_MAP_API_KEY`(운영)로 주입

## 빌드 및 실행 명령어

```bash
# 빌드
./gradlew build

# 실행 (MySQL 기동 상태 필요)
./gradlew bootRun

# 테스트 전체
./gradlew test

# 단일 테스트 클래스 실행
./gradlew test --tests "com.church.website.ChurchWebsiteApplicationTests"
```

서버 기본 포트: `http://localhost:8080`

## 아키텍처

### 레이어 구조

```
Controller → Service → Repository (JPA) → MySQL
```

- **Controller**: URL 라우팅만 담당, 비즈니스 로직은 Service에 위임
- **Service**: 비즈니스 로직 처리
- **Repository**: Spring Data JPA (메서드명 쿼리 방식)
- **Entity**: Lombok `@Data` / `@Builder` 사용

### 주요 모듈별 책임

| 모듈 | Controller | 설명 |
|------|-----------|------|
| 공개 페이지 | `MainController` | 홈, 소개, 예배, 공지, 오시는길, 사역소개 |
| 관리자 | `AdminController` | 공지사항·사역사진·새가족·주보·설교영상·교회정보·목회자·섬기는사람들·예배시간표·부서·계정·설정 CRUD |
| 로그인 | `LoginController` | 로그인 페이지 렌더링 |
| 새가족 등록 | `NewFamilyController` | 방문자용 새가족 등록 폼 |

### Security

- `SecurityConfig`: `/admin/**`는 `ROLE_ADMIN` 필요, 나머지 공개
- `CustomUserDetailsService`: DB `User` 엔티티 기반 인증
- 비밀번호: `BCryptPasswordEncoder`
- 로그인 성공 → `/admin` 리다이렉트
- `LoginAttemptService`: 로그인 5회 연속 실패 시 15분 잠금 (인메모리 캐시, 앱 재시작 시 초기화)

### 예외 처리 패턴

두 레이어가 역할을 나눠 처리한다. 새 예외 처리 추가 시 반드시 이 구분을 따를 것.

- `BaseController.run()`: POST 핸들러 내부 예외 → flash 메시지 + redirect
  - `IllegalArgumentException`: 비즈니스 규칙 위반 (비밀번호 불일치 등) → 메시지 그대로 노출
  - `Exception`: 시스템 오류 → 일반 메시지만 노출, 상세는 로그에만 기록
- `BaseController.runWithModel()`: redirect 없이 같은 뷰에 오류를 표시할 때 (새가족 등록 등)
- `GlobalExceptionHandler`: GET 핸들러 예외 → 에러 페이지 렌더링
  - `EntityNotFoundException` → `error/404`
  - `Exception` → `error/500`

### QueryDSL 커스텀 Repository 패턴

동적 쿼리가 필요한 엔티티(`Notice`, `NewFamily`, `Sermon`)는 세 파일 구조를 사용한다.

```
XxxRepositoryCustom   (인터페이스 — 메서드 선언)
XxxRepositoryImpl     (구현체 — JPAQueryFactory 사용)
XxxRepository         (extends JpaRepository + XxxRepositoryCustom)
```

단순 조회는 메서드명 쿼리, 검색·필터링 등 복잡한 조건은 QueryDSL Impl에 작성한다.

### 템플릿 구조

- 관리자 레이아웃: `templates/admin/fragments/layout.html` (Thymeleaf fragment)
- 공개 페이지: `templates/` 루트 및 하위 폴더 (about, worship, notice, ministry, new-family)

### 엔티티 목록

| 엔티티 | 테이블 | 설명 |
|--------|--------|------|
| `User` | `users` | 관리자 계정 |
| `Notice` | `notice` | 공지사항 |
| `NewFamily` | `new_family` | 새가족 등록 정보 |
| `MinistryPhoto` | `ministry_photo` | 사역 사진 |
| `MainImage` | `main_image` | 메인 페이지 이미지 |
| `Location` | `location` | 교회 위치 정보 |
| `Bulletin` | `bulletin` | 주보 (예배 식순, 찬양, 설교 노트 등) |
| `Sermon` | `sermon` | 설교 영상 (YouTube URL 기반, `getYoutubeVideoId()` 유틸 포함) |
| `ChurchInfo` | `church_info` | 교회 소개 정보 |
| `Pastor` | `pastor` | 담임목사 소개 |
| `StaffMember` | `staff_member` | 섬기는 사람들 |
| `WorshipSchedule` | `worship_schedule` | 예배 시간표 |
| `Department` | `department` | 부서 (교회학교) |

### 주의사항

- 민감 정보(DB 비밀번호, 카카오맵 API 키)는 `application-local.properties`에만 존재하며 `.gitignore`로 커밋에서 제외됨. `application.properties`에는 플레이스홀더만 있음 — 실제 값을 절대 커밋하지 말 것
- 저장소는 **public**임 (`github.com/minsu0922/church-website`, 과거 이름 `SpringBoot_MCP_Project`에서 변경). 커밋 전 민감 정보 포함 여부를 반드시 확인할 것
- 2026-08-06: 과거 히스토리에 하드코딩돼 있던 DB 비밀번호·카카오맵 API 키를 `git filter-repo`로 전체 제거하고 force push 완료. 이때 **모든 커밋 해시가 재작성됨** (구 `18f9781` → 신 `ed77e1c`) — 그 이전 커밋 해시를 참조하는 문서·이슈는 더 이상 유효하지 않음. DB 비밀번호는 교체 완료, 카카오맵 JS 키는 개발자 콘솔의 도메인 제한으로 방어 중
- H2는 테스트 전용(runtime), 운영·로컬은 MySQL만 사용
- 업로드 파일은 `${user.dir}/uploads/` 하위에 저장됨 (git 추적 제외)