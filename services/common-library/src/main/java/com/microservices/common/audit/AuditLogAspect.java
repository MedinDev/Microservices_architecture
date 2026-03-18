package com.microservices.common.audit;

import com.microservices.common.security.DataMaskingUtil;
import com.microservices.common.tracing.CorrelationIdConstants;
import java.time.Instant;
import java.util.Collection;
import java.util.stream.Collectors;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AuditLogAspect {

    private static final Logger logger = LoggerFactory.getLogger(AuditLogAspect.class);

    @Around("@annotation(auditableAction)")
    public Object logAuditEvent(ProceedingJoinPoint joinPoint, AuditableAction auditableAction) throws Throwable {
        String correlationId = MDC.get(CorrelationIdConstants.CORRELATION_ID_MDC_KEY);
        String userId = resolveUserId();
        String roles = resolveRoles();
        Instant startedAt = Instant.now();
        try {
            Object result = joinPoint.proceed();
            logger.info(
                "audit.action={} audit.status=SUCCESS audit.user={} audit.roles={} audit.correlationId={} audit.method={} audit.timestamp={}",
                auditableAction.value(),
                DataMaskingUtil.maskIdentifier(userId),
                roles,
                correlationId,
                joinPoint.getSignature().toShortString(),
                startedAt
            );
            return result;
        } catch (Throwable ex) {
            logger.warn(
                "audit.action={} audit.status=FAILED audit.user={} audit.roles={} audit.correlationId={} audit.method={} audit.timestamp={} audit.error={}",
                auditableAction.value(),
                DataMaskingUtil.maskIdentifier(userId),
                roles,
                correlationId,
                joinPoint.getSignature().toShortString(),
                startedAt,
                ex.getClass().getSimpleName()
            );
            throw ex;
        }
    }

    private String resolveUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return "anonymous";
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            Object claim = jwt.getClaims().get("userId");
            if (claim != null) {
                return String.valueOf(claim);
            }
            return jwt.getSubject();
        }
        return authentication.getName();
    }

    private String resolveRoles() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return "";
        }
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        return authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.joining(","));
    }
}
