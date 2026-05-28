package com.github.pfichtner.vaadoo;

import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.annotation.Retention;

import org.jmolecules.ddd.annotation.ValueObject;
import org.junit.jupiter.api.Test;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

class StatefulValidatorTest {

	@Retention(RUNTIME)
	@Constraint(validatedBy = MinValidator.class)
	public static @interface Min {
		String message() default "{@@@NAME@@@} must be at least {anno.value()}";

		int value();
	}

	public static class MinValidator implements ConstraintValidator<Min, Integer> {

		private int minValue;

		@Override
		public void initialize(Min constraintAnnotation) {
			this.minValue = constraintAnnotation.value();
		}

		@Override
		public boolean isValid(Integer value, ConstraintValidatorContext context) {
			return value == null || value >= minValue;
		}

	}

	@ValueObject
	public static class StatefulExample {
		public StatefulExample( //
				@Min(value = 42) int value1, //
				@Min(value = 21, message = "Custom message for {@@@NAME@@@}") int value2 //
		) {
		}
	}

	@Test
	void statefulValidatorShouldBeInitialized() throws Exception {
		Transformer transformer = new Transformer();
		Class<?> transformed = transformer.transform(StatefulExample.class);
		var constructor = transformed.getDeclaredConstructor(int.class, int.class);

		assertThatNoException().isThrownBy(() -> constructor.newInstance(42, 21));

		// Default message with placeholders
		assertThatThrownBy(() -> constructor.newInstance(41, 21)).hasRootCauseInstanceOf(IllegalArgumentException.class)
				.hasRootCauseMessage("value1 must be at least 42");

		// Custom message with placeholders
		assertThatThrownBy(() -> constructor.newInstance(42, 20)).hasRootCauseInstanceOf(IllegalArgumentException.class)
				.hasRootCauseMessage("Custom message for value2");
	}

}
