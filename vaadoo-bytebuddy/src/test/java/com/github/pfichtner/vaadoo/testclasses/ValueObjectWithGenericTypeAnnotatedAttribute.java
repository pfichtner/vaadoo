package com.github.pfichtner.vaadoo.testclasses;

import java.util.List;

import org.jmolecules.ddd.annotation.ValueObject;

import jakarta.validation.constraints.Positive;

@SuppressWarnings("unused")
@ValueObject
public class ValueObjectWithGenericTypeAnnotatedAttribute {

	private final List<@Positive Integer> positiveIntsList;

	public ValueObjectWithGenericTypeAnnotatedAttribute(List<@Positive Integer> positiveIntsList) {
		this.positiveIntsList = positiveIntsList;
	}

}
