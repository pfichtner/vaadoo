package com.github.pfichtner.vaadoo.testclasses.custom;

import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public class MinExample {

	@SuppressWarnings("unused")
	private final Integer value;

	public MinExample(@MinReproduction(min = 10) Integer value) {
		this.value = value;
	}

}
