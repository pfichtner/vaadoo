package com.github.pfichtner.vaadoo.testclasses.custom;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Target({ FIELD, METHOD, PARAMETER, ANNOTATION_TYPE })
@Retention(RUNTIME)
@Constraint(validatedBy = ComplexValidator.class)
public @interface ComplexReproduction {
	String message() default "complex failure";
	Class<?>[] groups() default {};
	Class<? extends Payload>[] payload() default {};
	
	String stringValue() default "";
	int intValue() default 0;
	boolean boolValue() default false;
	long longValue() default 0L;
	double doubleValue() default 0.0;
	float floatValue() default 0.0f;
	java.util.concurrent.TimeUnit enumValue() default java.util.concurrent.TimeUnit.SECONDS;
	Class<?> classValue() default Object.class;
}
