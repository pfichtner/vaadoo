package com.github.pfichtner.vaadoo.testclasses;

import org.jmolecules.ddd.annotation.ValueObject;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@SuppressWarnings("unused")
@ValueObject
public class ValueObjectWithRepeatableRegexAttribute {

	private static final String AT_LEAST = "Password must contain at least ";

	private final String password;

	public ValueObjectWithRepeatableRegexAttribute( //
			@NotNull //
			@Pattern(regexp = ".*[A-Z].*", message = AT_LEAST + "one uppercase letter") //
			@Pattern(regexp = ".*[0-9].*", message = AT_LEAST + "one digit") //
			@Pattern(regexp = ".*[!@#$%^&*()].*", message = AT_LEAST + "one special character") //
			String password) {
		this.password = password;
	}

}