package io.coreplatform.billing.api.security;

/**
 * 请求上下文：ThreadLocal 存储当前请求的用户身份
 */
public class SecurityContext {

    private static final ThreadLocal<String> userIdHolder = new ThreadLocal<>();
    private static final ThreadLocal<String> tenantIdHolder = new ThreadLocal<>();
    private static final ThreadLocal<String> roleHolder = new ThreadLocal<>();

    static void setUserId(String userId) { userIdHolder.set(userId); }
    static void setTenantId(String tenantId) { tenantIdHolder.set(tenantId); }
    static void setRole(String role) { roleHolder.set(role); }

    public static String getUserId() {
        String id = userIdHolder.get();
        return id != null ? id : "demo-user";
    }

    public static String getTenantId() {
        String id = tenantIdHolder.get();
        return id != null ? id : "default";
    }

    public static String getRole() {
        String role = roleHolder.get();
        return role != null ? role : "USER";
    }

    public static boolean hasRole(String role) {
        return getRole().equalsIgnoreCase(role);
    }

    public static boolean isSuperAdmin() { return hasRole("SUPER_ADMIN"); }
    public static boolean isAdminOrAbove() {
        return hasRole("ADMIN") || hasRole("SUPER_ADMIN");
    }

    public static void clear() {
        userIdHolder.remove();
        tenantIdHolder.remove();
        roleHolder.remove();
    }
}