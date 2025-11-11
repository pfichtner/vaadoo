package com.github.pfichtner.vaadoo.testclasses;

import org.jmolecules.ddd.annotation.ValueObject;

@SuppressWarnings("unused")
@ValueObject
public class ClassWithAttribute {

	private final String someString;

	public ClassWithAttribute(String someString) {
		this.someString = someString;
	}

}