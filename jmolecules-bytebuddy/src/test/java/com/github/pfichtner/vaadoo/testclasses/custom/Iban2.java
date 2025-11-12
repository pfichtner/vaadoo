package com.github.pfichtner.vaadoo.testclasses.custom;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;

import jakarta.validation.Constraint;

@Retention(RUNTIME)
@Constraint(validatedBy = IbanValidator.class)
public @interface Iban2 {

	String message() default "{jakarta.validation.constraints.Iban2.message}";
}
