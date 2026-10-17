package com.github.pfichtner.vaadoo.testclasses.custom;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ComplexValidator implements ConstraintValidator<ComplexReproduction, Object> {
	private ComplexReproduction annotation;

	@Override
	public void initialize(ComplexReproduction annotation) {
		this.annotation = annotation;
	}

	@Override
	public boolean isValid(Object value, ConstraintValidatorContext context) {
		return annotation != null;
	}
}
