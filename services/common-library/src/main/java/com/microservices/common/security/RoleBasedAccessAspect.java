package com.microservices.common.security;

import com.microservices.common.exception.UnauthorizedException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
public class RoleBasedAccessAspect {

    private final JwtValidationUtil jwtValidationUtil;

    public RoleBasedAccessAspect(JwtValidationUtil jwtValidationUtil) {
        this.jwtValidationUtil = jwtValidationUtil;
    }

    @Around("@annotation(requiredRole)")
    public Object enforceRole(ProceedingJoinPoint joinPoint, RequiredRole requiredRole) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new UnauthorizedException("No request context available");
        }
        HttpServletRequest request = attributes.getRequest();
        Claims claims = jwtValidationUtil.validateAndExtract(request.getHeader("Authorization"));
        if (!jwtValidationUtil.hasRole(claims, requiredRole.value())) {
            throw new UnauthorizedException("Required role not present: " + requiredRole.value());
        }
        return joinPoint.proceed();
    }
}
