package com.atlasflow.shared.request;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter dung chung cho tat ca HTTP request cua AtlasFlow.
 *
 * Vi du luong chay:
 *
 * Client
 *   -> RequestIdFilter
 *   -> Controller
 *   -> Application Service
 *   -> Response
 *
 * Nhiem vu chinh cua class nay la dam bao moi request co mot ma
 * X-Request-Id de co the truy vet request khi debug hoac xu ly loi.
 * Class nay khong tao Project, Job, Workflow va khong truy cap database.
 */
@Component
public class RequestIdFilter extends OncePerRequestFilter {

    /**
     * Tên HTTP header dùng để truyền request ID giữa client và server.
     *
     * Ví dụ client gửi:
     * X-Request-Id: request-123
     */
    public static final String HEADER_NAME = "X-Request-Id";

    /**
     * Tên attribute dùng để lưu request ID bên trong server.
     * Attribute chỉ tồn tại trong vòng đời của request hiện tại.
     */
    public static final String ATTRIBUTE_NAME = RequestIdFilter.class.getName();
    private static final int MAX_REQUEST_ID_LENGTH = 128;

    /**
     * Hàm này được Spring gọi một lần cho mỗi HTTP request.
     *
     * Luồng xử lý:
     * Client -> Filter -> Controller -> Service -> Response
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // Buoc 1: doc header X-Request-Id ma client gui len.
        // Neu client gui "X-Request-Id: abc-123" thi requestId = "abc-123".
        String requestId = request.getHeader(HEADER_NAME);

        // Buoc 2: neu client khong gui ID hoac gui chuoi rong,
        // server tu tao UUID. Nho vay request nao cung co danh tinh.
        if (requestId == null
                || requestId.isBlank()
                || requestId.length() > MAX_REQUEST_ID_LENGTH
                || containsControlCharacter(requestId)) {
            requestId = UUID.randomUUID().toString();
        }

        // Buoc 3: luu ID vao request attribute.
        // Attribute la du lieu noi bo trong server, chi ton tai trong request nay.
        request.setAttribute(ATTRIBUTE_NAME, requestId);

        // Buoc 4: tra ID ve client qua response header.
        // Khi client bao loi, developer co the dung ID nay de tim log.
        response.setHeader(HEADER_NAME, requestId);

        // Them cac security header co ban vao moi response.
        // nosniff: khong cho trinh duyet tu doan sai Content-Type.
        response.setHeader("X-Content-Type-Options", "nosniff");

        // DENY: khong cho website khac nhung trang cua minh vao iframe.
        response.setHeader("X-Frame-Options", "DENY");

        // Khong gui URL cua trang truoc do qua Referer header.
        response.setHeader("Referrer-Policy", "no-referrer");

        // Anonymous class ben duoi chi duoc dung bien local final/effectively final.
        String finalRequestId = requestId;

        // Bao request goc bang wrapper.
        // Muc dich: neu code phia sau goi request.getHeader("X-Request-Id")
        // thi no van nhan duoc ID do server tu tao, khong bi null.
        HttpServletRequest requestWithId = new HttpServletRequestWrapper(request) {
            @Override
            public String getHeader(String name) {
                // Chi thay doi header X-Request-Id.
                // Cac header khac van doc tu request goc.
                if (HEADER_NAME.equalsIgnoreCase(name)) {
                    return finalRequestId;
                }
                return super.getHeader(name);
            }
        };

        // Cho request di tiep den Controller va cac Filter tiep theo.
        // Neu thieu dong nay, request dung tai day va Controller khong chay.
        filterChain.doFilter(requestWithId, response);
    }

    private boolean containsControlCharacter(String value) {
        return value.chars().anyMatch(Character::isISOControl);
    }
}
