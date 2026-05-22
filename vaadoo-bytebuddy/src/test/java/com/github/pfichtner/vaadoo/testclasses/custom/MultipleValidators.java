package com.github.pfichtner.vaadoo.testclasses.custom;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;

import jakarta.validation.Constraint;

@Retention(RUNTIME)
@Constraint(validatedBy = { Validator1.class, Validator2.class })
public @interface MultipleValidators {
	String message() default "{@@@NAME@@@} MultipleValidators failed";
}
