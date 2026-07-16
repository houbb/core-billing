package io.coreplatform.billing.api.security;

import io.coreplatform.billing.api.exception.ErrorCode;
import io.coreplatform.billing.application.exception.InsufficientPermissionException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Aspect
@Component
public class RoleAspect {

    private static final Logger log = LoggerFactory.getLogger(RoleAspect.class);

    @Around("@annotation(io.coreplatform.billing.api.security.RequireRole)")
    public Object checkRole(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        RequireRole requireRole = signature.getMethod().getAnnotation(RequireRole.class);

        String currentRole = SecurityContext.getRole();
        List<String> allowedRoles = Arrays.asList(requireRole.value());

        if (allowedRoles.stream().anyMatch(r -> r.equalsIgnoreCase(currentRole))) {
            return joinPoint.proceed();
        }

        log.warn("权限拒绝: userId={}, role={}, requiredRoles={}, method={}",
                SecurityContext.getUserId(), currentRole, allowedRoles,
                signature.getMethod().getName());

        throw new InsufficientPermissionException(
                "权限不足：需要 " + String.join(" / ", allowedRoles) +
                " 角色，当前角色: " + currentRole);
    }
}