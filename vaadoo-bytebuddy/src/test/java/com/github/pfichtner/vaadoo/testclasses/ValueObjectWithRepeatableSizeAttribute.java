package com.github.pfichtner.vaadoo.testclasses;

import org.jmolecules.ddd.annotation.ValueObject;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@SuppressWarnings("unused")
@ValueObject
public class ValueObjectWithRepeatableSizeAttribute {

	private final String value;

	public ValueObjectWithRepeatableSizeAttribute( //
			@NotNull //
			@Size(min = 2, message = "must have at least 2 characters") //
			@Size(max = 10, message = "must have at most 10 characters") //
			String value) {
		this.value = value;
	}

}
