package com.github.pfichtner.vaadoo.testclasses;

import org.jmolecules.ddd.annotation.ValueObject;

import jakarta.validation.constraints.NotNull;

@SuppressWarnings("unused")
@ValueObject
public class ClassWithNotNullAttribute {

	private final String someString;

	public ClassWithNotNullAttribute(@NotNull String someString) {
		this.someString = someString;
	}
}