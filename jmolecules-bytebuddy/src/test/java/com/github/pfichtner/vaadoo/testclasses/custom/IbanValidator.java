package com.github.pfichtner.vaadoo.testclasses.custom;

import static java.util.regex.Pattern.CASE_INSENSITIVE;

import java.util.regex.Pattern;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * A very simple IBAN validator (which only supports German IBANs).
 * 
 * Important: this only checks format and length. To fully validate an IBAN you
 * must also perform the ISO 7064 mod-97 checksum (convert letters → numbers and
 * check remainder 1).
 */
public class IbanValidator implements ConstraintValidator<ValidIbanWithMessage, String> {

	private static final Pattern DE_IBAN = Pattern.compile("^DE\\d{20}$", CASE_INSENSITIVE);

	@Override
	public boolean isValid(String iban, ConstraintValidatorContext context) {
		return DE_IBAN.matcher(clean(iban)).matches();
	}

	private static String clean(String iban) {
		return iban.replaceAll("\\s+", "");
	}

}
