package com.duyanh.audit;

import java.lang.reflect.Method;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

import com.duyanh.service.AuditService;

import lombok.RequiredArgsConstructor;

/**
 * Ghi Audit Log sau khi method có @Auditable chạy thành công (ném exception thì không ghi).
 * Chạy bên trong transaction của method (xem TransactionConfig).
 */
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class AuditAspect {

	private static final ExpressionParser PARSER = new SpelExpressionParser();
	private static final ParameterNameDiscoverer PARAM_NAMES = new DefaultParameterNameDiscoverer();

	private final AuditService auditService;

	@AfterReturning(pointcut = "@annotation(auditable)", returning = "result")
	public void audit(JoinPoint joinPoint, Auditable auditable, Object result) {
		Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
		var context = new MethodBasedEvaluationContext(null, method, joinPoint.getArgs(), PARAM_NAMES);
		context.setVariable("result", result);

		Long actorId = auditable.actorId().isBlank()
				? auditService.currentUserId()
				: evaluate(auditable.actorId(), context, Long.class);
		auditService.record(actorId, auditable.action(),
				auditable.entityType().isBlank() ? null : auditable.entityType(),
				evaluate(auditable.entityId(), context, Long.class),
				evaluate(auditable.details(), context, Object.class));
	}

	private static <T> T evaluate(String expression, MethodBasedEvaluationContext context, Class<T> type) {
		return expression.isBlank() ? null : PARSER.parseExpression(expression).getValue(context, type);
	}
}
