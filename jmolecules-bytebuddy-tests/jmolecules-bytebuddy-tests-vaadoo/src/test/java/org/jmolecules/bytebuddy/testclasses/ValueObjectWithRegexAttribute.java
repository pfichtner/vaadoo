package org.jmolecules.bytebuddy.testclasses;

import org.jmolecules.ddd.annotation.ValueObject;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@SuppressWarnings("unused")
@ValueObject
public class ValueObjectWithRegexAttribute {

	private final String someTwoDigits;

	public ValueObjectWithRegexAttribute(@NotNull @Pattern(regexp = "\\d\\d") String someTwoDigits) {
		this.someTwoDigits = someTwoDigits;
	}

}