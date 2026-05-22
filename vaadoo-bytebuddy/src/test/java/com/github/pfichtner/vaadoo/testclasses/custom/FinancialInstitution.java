package com.github.pfichtner.vaadoo.testclasses.custom;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.jmolecules.ddd.annotation.ValueObject;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@ValueObject
public class FinancialInstitution {
	
	public static class BicValidator implements ConstraintValidator<Bic, String> {

		private static final String REGEX = "^[A-Za-z0-9]{4}[A-Za-z]{2}[A-Za-z0-9]{2}([A-Za-z0-9]{3})?$";
//		private static final Pattern REGEX = Pattern.compile(REGEX);

		@Override
		public boolean isValid(String value, ConstraintValidatorContext context) {
//			return value == null || REGEX.matcher(value).matches();
			return value == null || value.matches(REGEX);
		}

	}

	@Documented
	@Constraint(validatedBy = BicValidator.class)
	@Target({ FIELD, PARAMETER })
	@Retention(RUNTIME)
	public static @interface Bic {

		String message() default "Bic %s is invalid";

		Class<?>[] groups() default {};

		Class<? extends Payload>[] payload() default {};
	}

	public FinancialInstitution(@NotNull @Bic String bic, @NotBlank String name) {
	}

}
