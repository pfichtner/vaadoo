package com.github.pfichtner.vaadoo.testclasses.custom;

import org.jmolecules.ddd.annotation.ValueObject;

@SuppressWarnings("unused")
@ValueObject
public class CustomExample {

	private String iban;

	public CustomExample(@Iban String iban) {
		this.iban = iban;
	}

	public CustomExample(@Iban2 String iban, String ignore) {
		this.iban = iban;
	}

	public CustomExample(@Iban2(message = "a custom message") String iban, boolean ignore) {
		this.iban = iban;
	}

}
