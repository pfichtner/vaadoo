package com.github.pfichtner.vaadoo.testclasses.custom;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;

import jakarta.validation.Constraint;

/**
 * {@link IbanValidatorThatExtendsClass} is always true but this annotation was
 * introduced to have a test that validates if IbanValidatorThatExtendsClass is
 * detected and handled as custom validator.
 */
@Retention(RUNTIME)
@Constraint(validatedBy = IbanValidatorThatExtendsClass.class)
public @interface ValidIbanAlwaysTrue {
}
