package org.jmolecules.bytebuddy.testclasses;

import org.jmolecules.ddd.annotation.ValueObject;

import jakarta.validation.constraints.NotNull;

@SuppressWarnings("unused")
@ValueObject
public class ValueObjectWithAttribute {

	private final String someString;

	public ValueObjectWithAttribute(@NotNull String someString) {
		this.someString = someString;
	}

}