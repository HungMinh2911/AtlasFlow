package com.atlasflow.ratelimit;

import com.atlasflow.auth.api.LoginRequest;
import com.atlasflow.job.api.CreateJobRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class RateLimitAspect {

    private final RateLimitService rateLimitService;
    private final HttpServletRequest request;


    public RateLimitAspect(RateLimitService rateLimitService, HttpServletRequest request) {
        this.rateLimitService = rateLimitService;
        this.request = request;
    }

    @Around("@annotation(rateLimited)")
    public Object checkRateLimit (
            ProceedingJoinPoint joinPoint,
            RateLimited rateLimited
    ) throws Throwable {
        RateLimitPolicy policy = rateLimited.value();
        String key = createKey(joinPoint, policy);

        RateLimitDecision decision =
                rateLimitService.check(
                        key,
                        policy.limit(),
                        policy.windowSeconds()
                );

        if (!decision.allowed()) {
            throw new RateLimitExceededException(
                    decision.retryAfterSeconds()
            );
        }

        return joinPoint.proceed();
    }


    private String createKey (
            ProceedingJoinPoint joinPoint,
            RateLimitPolicy policy
    ) {
        // Implementation for creating a unique key based on the join point and policy

        Object[] args = joinPoint.getArgs();
        if (policy == RateLimitPolicy.LOGIN){
            LoginRequest loginRequest =
                    findArgument(args,LoginRequest.class);

            return RateLimitKeyFactory.login(clientIp(), loginRequest.getEmail());

        }

        if (policy == RateLimitPolicy.SUBMIT_JOB){

            CreateJobRequest jobRequest =
                    findArgument(args, CreateJobRequest.class);

            return RateLimitKeyFactory.submitJob(
                    jobRequest.project_id()
            );
        }
        throw new IllegalArgumentException(
                "Unsupported rate-limit policy: " + policy
        );
    }

    private String clientIp() {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",", 2)[0].trim();
        }

        String realIp = request.getHeader("X-Real-IP");
        return realIp == null || realIp.isBlank()
                ? request.getRemoteAddr()
                : realIp.trim();
    }

    private <T> T findArgument(
            Object[] args,
            Class<T> type
    ){

        for (Object arg : args){
            if (type.isInstance(arg)){
                return type.cast(arg);
            }
        }
        throw new IllegalStateException(
                "Required rate-limit argument not found: " + type.getName()
        );
    }


}
