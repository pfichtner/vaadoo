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
		String message() default "Value must be at least {value}";

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
		public StatefulExample(@Min(value = 42) int min42, @Min(value = 21) int min21) {
		}
	}

	@Test
	void statefulValidatorShouldBeInitialized() throws Exception {
		Transformer transformer = new Transformer();
		Class<?> transformed = transformer.transform(StatefulExample.class);
		var constructor = transformed.getDeclaredConstructor(int.class, int.class);

		assertThatNoException().isThrownBy(() -> constructor.newInstance(42, 21));
		assertThatThrownBy(() -> constructor.newInstance(41, 21)).hasCauseInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> constructor.newInstance(42, 20)).hasCauseInstanceOf(IllegalArgumentException.class);
	}

}
