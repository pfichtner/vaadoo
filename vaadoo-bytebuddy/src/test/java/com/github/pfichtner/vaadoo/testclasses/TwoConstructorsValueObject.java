package com.github.pfichtner.vaadoo.testclasses;

import org.jmolecules.ddd.annotation.ValueObject;

import jakarta.validation.constraints.NotNull;

@ValueObject
public class TwoConstructorsValueObject {

	public TwoConstructorsValueObject(@NotNull String a) {
	}

	public TwoConstructorsValueObject(@NotNull String a, boolean b) {
	}

}
