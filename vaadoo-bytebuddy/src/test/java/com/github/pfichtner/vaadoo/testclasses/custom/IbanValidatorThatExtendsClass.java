package com.github.pfichtner.vaadoo.testclasses.custom;

import com.github.pfichtner.vaadoo.CustomAnnotations;

import jakarta.validation.ConstraintValidatorContext;

/**
 * A class that extends a Validator and does not directly implement the
 * ConstraintValidator interface. {@link CustomAnnotations} failed to discover
 * this class as a validator, because it only looks for classes that directly
 * implement the ConstraintValidator interface.
 */
public class IbanValidatorThatExtendsClass extends IbanValidator {

	@Override
	public boolean isValid(String iban, ConstraintValidatorContext context) {
		return true;
	}

}
