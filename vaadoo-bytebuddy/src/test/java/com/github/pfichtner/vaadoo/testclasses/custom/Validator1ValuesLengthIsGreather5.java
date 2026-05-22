package com.github.pfichtner.vaadoo.testclasses.custom;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class Validator1ValuesLengthIsGreather5 implements ConstraintValidator<MultipleValidators, String> {

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		return value != null && value.length() >= 5;
	}

}
