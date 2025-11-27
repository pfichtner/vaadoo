package com.github.pfichtner.vaadoo.testclasses.custom;

import org.jmolecules.ddd.annotation.ValueObject;

@SuppressWarnings("unused")
@ValueObject
public class CustomExampleWithCustomMessage {

	private final String iban;

	public CustomExampleWithCustomMessage(@ValidIbanWithMessage String iban, String ignore) {
		this.iban = iban;
	}

	public CustomExampleWithCustomMessage(@ValidIbanWithMessage(message = "a custom message") String iban,
			boolean ignore) {
		this.iban = iban;
	}

}
