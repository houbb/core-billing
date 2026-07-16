package io.coreplatform.billing.api.security;

import java.lang.annotation.*;

/**
 * 角色权限注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {

    /**
     * 允许访问的角色列表
     */
    String[] value() default {"ADMIN"};
}