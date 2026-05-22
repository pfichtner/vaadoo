package com.github.pfichtner.vaadoo;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.github.pfichtner.vaadoo.testclasses.custom.MultipleValidatorsExample;

class MultipleValidatorsTest {

	private final Transformer transformer = new Transformer();

	@Test
	void bothValidatorsMustPass() throws Exception {
		var transformed = transformer.transform(MultipleValidatorsExample.class);
		var constructor = transformed.getDeclaredConstructor(String.class);

		// Passes both: length >= 5 and starts with "A"
		assertThatNoException().isThrownBy(() -> constructor.newInstance("ABCDE"));

		// Fails Validator1 (length < 5)
		assertThatThrownBy(() -> constructor.newInstance("ABCD"))
				.hasCauseInstanceOf(IllegalArgumentException.class)
				.hasRootCauseMessage("value MultipleValidators failed");

		// Fails Validator2 (does not start with "A")
		assertThatThrownBy(() -> constructor.newInstance("BCDEF"))
				.hasCauseInstanceOf(IllegalArgumentException.class)
				.hasRootCauseMessage("value MultipleValidators failed");
                
		// Fails both
		assertThatThrownBy(() -> constructor.newInstance("BCDE"))
				.hasCauseInstanceOf(IllegalArgumentException.class)
				.hasRootCauseMessage("value MultipleValidators failed");
	}

}
