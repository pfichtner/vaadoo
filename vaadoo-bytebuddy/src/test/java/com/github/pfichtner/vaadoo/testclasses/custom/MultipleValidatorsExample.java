package com.github.pfichtner.vaadoo.testclasses.custom;

import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public class MultipleValidatorsExample {
	private final String value;

	public MultipleValidatorsExample(@MultipleValidators String value) {
		this.value = value;
	}

	public String getValue() {
		return value;
	}
}
