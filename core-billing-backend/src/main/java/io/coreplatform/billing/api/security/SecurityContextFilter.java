package io.coreplatform.billing.api.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * 从 HTTP Header 提取用户身份信息
 */
public class SecurityContextFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(SecurityContextFilter.class);

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_TENANT_ID = "X-Tenant-Id";
    public static final String HEADER_ROLE = "X-Role";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;

        String userId = httpRequest.getHeader(HEADER_USER_ID);
        String tenantId = httpRequest.getHeader(HEADER_TENANT_ID);
        String role = httpRequest.getHeader(HEADER_ROLE);

        // 开发阶段：缺失时使用默认值
        if (userId == null || userId.isEmpty()) {
            userId = "demo-user";
        }
        if (role == null || role.isEmpty()) {
            role = "ADMIN"; // 本地开发默认管理员，方便调试
        }
        if (tenantId == null || tenantId.isEmpty()) {
            tenantId = "default";
        }

        SecurityContext.setUserId(userId);
        SecurityContext.setTenantId(tenantId);
        SecurityContext.setRole(role);

        log.debug("Security: userId={}, tenantId={}, role={}", userId, tenantId, role);

        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContext.clear();
        }
    }
}