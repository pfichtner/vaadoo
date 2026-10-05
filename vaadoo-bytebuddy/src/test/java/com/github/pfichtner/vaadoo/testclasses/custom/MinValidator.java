package com.github.pfichtner.vaadoo.testclasses.custom;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class MinValidator implements ConstraintValidator<MinReproduction, Integer> {
	private int min;

	@Override
	public void initialize(MinReproduction annotation) {
		this.min = annotation.min();
	}

	@Override
	public boolean isValid(Integer value, ConstraintValidatorContext context) {
		return value != null && value >= min;
	}
}
