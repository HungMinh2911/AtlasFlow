package com.atlasflow.security.tenant;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD) // chi duoc dat tren  method
@Retention(RetentionPolicy.RUNTIME) // ton tai trong runtime
public @interface RequireWorkflowAccess {

    String pathVariable() default "workflowId";
}
