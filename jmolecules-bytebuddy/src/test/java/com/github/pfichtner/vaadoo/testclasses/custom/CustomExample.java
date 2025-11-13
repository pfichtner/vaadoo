package com.github.pfichtner.vaadoo.testclasses.custom;

import org.jmolecules.ddd.annotation.ValueObject;

@SuppressWarnings("unused")
@ValueObject
public class CustomExample {

	private String iban;

	public CustomExample(@ValidIban String iban) {
		this.iban = iban;
	}

}
