package com.github.pfichtner.vaadoo.fragments.impl;

import static java.lang.reflect.Proxy.newProxyInstance;
import static java.util.function.Predicate.not;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.time.LocalDate;
import java.util.Map;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import com.github.pfichtner.vaadoo.fragments.Jsr380CodeFragment;

import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Negative;
import jakarta.validation.constraints.NegativeOrZero;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.time.api.Dates;

/**
 * Property-based tests for {@link JdkOnlyCodeFragment} covering the same
 * behavior exercised in the example unit tests. These tests focus on generating
 * a wide variety of valid values and also specific invalid value generators for
 * the negative cases.
 */
class JdkOnlyCodeFragmentPropertyTest {

	private static <A extends Annotation> A anno(Class<A> annotationType) {
		return anno(annotationType, Map.of());
	}

	private static <A extends Annotation> A anno(Class<A> annotationType, Map<String, Object> values) {
		return annotationType.cast(newProxyInstance(annotationType.getClassLoader(), new Class<?>[] { annotationType },
				(InvocationHandler) (p, m, a) -> {
					if (m.getName().equals("annotationType")) {
						return annotationType;
					}
					if (m.getName().equals("message")) {
						return "theMessage";
					}
					Object object = values.get(m.getName());
					return (object == null) ? m.getDefaultValue() : object;
				}));
	}

	Jsr380CodeFragment sut = new JdkOnlyCodeFragment();

	// NotNull: generated non-null strings should never fail
	@Property
	void notNull_should_accept_any_non_null_string(@ForAll("nonNullStrings") String s) {
		var a = anno(NotNull.class);
		assertDoesNotThrow(() -> sut.check(a, s));
	}

	@Property
	void notNull_should_throw_on_null() {
		var a = anno(NotNull.class);
		assertThrows(NullPointerException.class, () -> sut.check(a, (Object) null));
	}

	// NotBlank: non-blank strings should pass
	@Property
	void notBlank_passes_for_non_blank(@ForAll("nonBlankStrings") String s) {
		var a = anno(NotBlank.class);
		assertDoesNotThrow(() -> sut.check(a, s));
	}

	@Property
	void notBlank_fails_for_blank() {
		var a = anno(NotBlank.class);
		assertThrows(IllegalArgumentException.class, () -> sut.check(a, "   "));
	}

	// Pattern: generated strings matching the pattern should pass
	@Property
	void pattern_matches_generated_values(@ForAll("twoDigits") String s) {
		var a = anno(Pattern.class, Map.of("regexp", "\\d{2}"));
		assertDoesNotThrow(() -> sut.check(a, s));
	}

	// NotEmpty variants: char sequence, collection and map are covered in unit
	// tests;
	// here we at least property-test char sequences and arrays
	@Property
	void notEmpty_charsequence_passes(@ForAll("nonBlankStrings") String s) {
		var a = anno(NotEmpty.class);
		assertDoesNotThrow(() -> sut.check(a, s));
	}

	@Property
	void notEmpty_array_fails_for_empty() {
		var a = anno(NotEmpty.class);
		assertThrows(IllegalArgumentException.class, () -> sut.check(a, new Object[0]));
	}

	// Size: generate valid sizes and invalid sizes explicitly
	@Property
	void size_accepts_values_within_bounds(@ForAll("sizeStrings") String s) {
		var a = anno(Size.class, Map.of("min", 2, "max", 4));
		assertDoesNotThrow(() -> sut.check(a, s));
	}

	@Property
	void size_rejects_too_short(@ForAll("shortStrings") String s) {
		var a = anno(Size.class, Map.of("min", 2, "max", 4));
		assertThrows(IllegalArgumentException.class, () -> sut.check(a, s));
	}

	// AssertTrue / AssertFalse
	@Property
	void assertTrue_accepts_true(@ForAll boolean b) {
		var a = anno(AssertTrue.class);
		if (b) {
			assertDoesNotThrow(() -> sut.check(a, b));
		} else {
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, b));
		}
	}

	@Property
	void assertFalse_accepts_false(@ForAll boolean b) {
		var a = anno(AssertFalse.class);
		if (!b) {
			assertDoesNotThrow(() -> sut.check(a, b));
		} else {
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, b));
		}
	}

	// Min / Max for ints
	@Property
	void min_accepts_values_at_or_above(@ForAll int v) {
		var a = anno(Min.class, Map.of("value", 0L));
		if (v >= 0) {
			assertDoesNotThrow(() -> sut.check(a, v));
		} else {
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, v));
		}
	}

	@Property
	void max_accepts_values_below_limit(@ForAll int v) {
		var a = anno(Max.class, Map.of("value", 100L));
		if (v <= 100) {
			assertDoesNotThrow(() -> sut.check(a, v));
		} else {
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, v));
		}
	}

	// DecimalMin / DecimalMax for CharSequence values (strings representing
	// numbers)
	@Property
	void decimalMin_accepts_strings_greater_or_equal(@ForAll("numericStringsGE2") String s) {
		var a = anno(DecimalMin.class, Map.of("value", "2"));
		assertDoesNotThrow(() -> sut.check(a, s));
	}

	@Property
	void decimalMax_rejects_greater_strings(@ForAll("numericStringsGT5") String s) {
		var a = anno(DecimalMax.class, Map.of("value", "5"));
		assertThrows(IllegalArgumentException.class, () -> sut.check(a, s));
	}

	// Digits and sign related constraints
	@Property
	void digits_rejects_too_many_integer_digits(@ForAll int v) {
		var a = anno(Digits.class, Map.of("integer", 2, "fraction", 0));
		if (Math.abs(v) <= 99) {
			assertDoesNotThrow(() -> sut.check(a, v));
		} else {
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, v));
		}
	}

	@Property
	void positive_and_negative_behaviour(@ForAll int v) {
		var p = anno(Positive.class);
		var pz = anno(PositiveOrZero.class);
		var n = anno(Negative.class);
		var nz = anno(NegativeOrZero.class);

		if (v > 0) {
			assertDoesNotThrow(() -> sut.check(p, v));
		} else {
			assertThrows(IllegalArgumentException.class, () -> sut.check(p, v));
		}

		if (v >= 0) {
			assertDoesNotThrow(() -> sut.check(pz, v));
		} else {
			assertThrows(IllegalArgumentException.class, () -> sut.check(pz, v));
		}

		if (v < 0) {
			assertDoesNotThrow(() -> sut.check(n, v));
		} else {
			assertThrows(IllegalArgumentException.class, () -> sut.check(n, v));
		}

		if (v <= 0) {
			assertDoesNotThrow(() -> sut.check(nz, v));
		} else {
			assertThrows(IllegalArgumentException.class, () -> sut.check(nz, v));
		}
	}

	// Temporal: Past / Future for LocalDate
	@Property
	void past_accepts_dates_before_today(@ForAll("pastDates") LocalDate d) {
		var a = anno(Past.class);
		assertDoesNotThrow(() -> sut.check(a, d));
	}

	@Test
	void future_rejects_past_date() {
		var a = anno(jakarta.validation.constraints.Future.class);
		assertThrows(IllegalArgumentException.class, () -> sut.check(a, LocalDate.now().minusDays(1)));
	}

	@Property
	void future_dates_are_future_and_rejected_by_past(@ForAll("futureDates") LocalDate d) {
		var pastAnno = anno(Past.class);
		var futureAnno = anno(Future.class);

		// future dates should be rejected by @Past and accepted by @Future
		assertThrows(IllegalArgumentException.class, () -> sut.check(pastAnno, d));
		assertDoesNotThrow(() -> sut.check(futureAnno, d));
	}

	@Property
	void futureOrPresent_accepts_present_and_future(@ForAll("futureOrPresentDates") LocalDate d) {
		var futureOrPresent = anno(FutureOrPresent.class);
		var pastOrPresent = anno(PastOrPresent.class);
		LocalDate today = LocalDate.now();

		// FutureOrPresent should accept today and future dates
		assertDoesNotThrow(() -> sut.check(futureOrPresent, d));

		// PastOrPresent should reject strictly future dates, but accept today
		if (d.isAfter(today)) {
			assertThrows(IllegalArgumentException.class, () -> sut.check(pastOrPresent, d));
		} else {
			assertDoesNotThrow(() -> sut.check(pastOrPresent, d));
		}
	}

	@Property
	void pastOrPresent_accepts_present_and_past(@ForAll("pastOrPresentDates") LocalDate d) {
		var pastOrPresent = anno(PastOrPresent.class);
		var futureOrPresent = anno(FutureOrPresent.class);
		LocalDate today = LocalDate.now();

		// PastOrPresent should accept today and past dates
		assertDoesNotThrow(() -> sut.check(pastOrPresent, d));

		// FutureOrPresent should reject strictly past dates, but accept today
		if (d.isBefore(today)) {
			assertThrows(IllegalArgumentException.class, () -> sut.check(futureOrPresent, d));
		} else {
			assertDoesNotThrow(() -> sut.check(futureOrPresent, d));
		}
	}

	/* --- Arbitraries --- */

	// Providers for named arbitraries used by @ForAll("name")
	@Provide
	Arbitrary<String> nonNullStrings() {
		return Arbitraries.strings().ofMinLength(0).ofMaxLength(20);
	}

	@Provide
	Arbitrary<String> nonBlankStrings() {
		return Arbitraries.strings().ofMinLength(0).ofMaxLength(20).filter(not(s -> s.trim().isEmpty()));
	}

	@Provide
	Arbitrary<String> twoDigits() {
		return Arbitraries.integers().between(0, 99).map(i -> String.format("%02d", i));
	}

	@Provide
	Arbitrary<String> sizeStrings() {
		return Arbitraries.strings().withCharRange('a', 'z').ofMinLength(2).ofMaxLength(4);
	}

	@Provide
	Arbitrary<String> shortStrings() {
		return Arbitraries.strings().withCharRange('a', 'z').ofMaxLength(1);
	}

	@Provide
	Arbitrary<String> numericStringsGE2() {
		return Arbitraries.integers().between(2, 1000).map(Object::toString);
	}

	@Provide
	Arbitrary<String> numericStringsGT5() {
		return Arbitraries.integers().greaterOrEqual(6).map(Object::toString);
	}

	@Provide
	Arbitrary<LocalDate> pastDates() {
		return Dates.dates().atTheEarliest(LocalDate.of(1970, 1, 1)).atTheLatest(LocalDate.now().minusDays(1));
	}

	@Provide
	Arbitrary<LocalDate> futureDates() {
		return Dates.dates().atTheEarliest(LocalDate.now().plusDays(1)).atTheLatest(LocalDate.now().plusYears(10));
	}

	@Provide
	Arbitrary<LocalDate> futureOrPresentDates() {
		return Dates.dates().atTheEarliest(LocalDate.now()).atTheLatest(LocalDate.now().plusYears(10));
	}

	@Provide
	Arbitrary<LocalDate> pastOrPresentDates() {
		return Dates.dates().atTheEarliest(LocalDate.of(1970, 1, 1)).atTheLatest(LocalDate.now());
	}

}
