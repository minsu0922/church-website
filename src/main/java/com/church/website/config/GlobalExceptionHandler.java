package com.church.website.config;

import com.church.website.exception.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 전역 예외 핸들러
 *
 * BaseController.run()과 역할 분담:
 *   - run()       : POST 핸들러 내부에서 발생하는 예외 → flash 메시지 + redirect 처리
 *   - 이 클래스   : GET 핸들러(상세 조회 등)나 run() 밖에서 발생하는 예외 → 에러 페이지 렌더링
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    // 존재하지 않는 데이터 접근 (예: /admin/notices/edit/9999)
    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleEntityNotFound(EntityNotFoundException ex, Model model) {
        model.addAttribute("message", ex.getMessage());
        return "error/404";
    }

    // 없는 주소나 업로드 파일 요청. 아래 Exception 처리로 넘어가면 500 화면과 ERROR 로그가 남는다
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNoResource(NoResourceFoundException ex) {
        log.debug("없는 주소 요청: {}", ex.getResourcePath());
        return "error/404";
    }

    // 동영상 스트리밍 중 클라이언트가 연결을 끊은 경우 — 정상 현상, 무시
    @ExceptionHandler(ClientAbortException.class)
    public void handleClientAbort(ClientAbortException ex) {
        log.debug("클라이언트 연결 끊김 (정상): {}", ex.getMessage());
    }

    // 예상치 못한 모든 예외의 최후 방어선
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleException(Exception ex, Model model) {
        log.error("처리되지 않은 예외 발생", ex);
        model.addAttribute("message", "서버 오류가 발생했습니다.");
        return "error/500";
    }
}
