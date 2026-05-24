package com.github.pfichtner.vaadoo;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.github.pfichtner.vaadoo.testclasses.custom.MinExample;

class ConstraintValidatorInitializeTest {

	private final Transformer transformer = new Transformer();

	@Test
	void initializeMustBeCalled() throws Exception {
		var transformed = transformer.transform(MinExample.class);
		var constructor = transformed.getDeclaredConstructor(Integer.class);

		// Passes: 10 >= 10
		assertThatNoException().isThrownBy(() -> constructor.newInstance(Integer.valueOf(10)));

		// Fails: 5 < 10
		// If initialize is NOT called, threshold is 0, so 5 >= 0 would PASS.
		// So this test SHOULD FAIL with current implementation.
		assertThatThrownBy(() -> constructor.newInstance(Integer.valueOf(5)))
				.hasCauseInstanceOf(IllegalArgumentException.class)
				.hasRootCauseMessage("value too small");
	}

}
