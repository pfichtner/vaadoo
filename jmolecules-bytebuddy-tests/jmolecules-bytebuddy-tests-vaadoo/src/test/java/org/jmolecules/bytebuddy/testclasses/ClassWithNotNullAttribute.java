package org.jmolecules.bytebuddy.testclasses;

import jakarta.validation.constraints.NotNull;

@SuppressWarnings("unused")
public class ClassWithNotNullAttribute {
	private final String someString;

	public ClassWithNotNullAttribute(@NotNull String someString) {
		this.someString = someString;
	}
}