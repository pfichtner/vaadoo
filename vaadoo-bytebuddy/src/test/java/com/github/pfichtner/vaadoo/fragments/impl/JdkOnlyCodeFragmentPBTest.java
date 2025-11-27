package com.github.pfichtner.vaadoo.fragments.impl;

import static java.math.RoundingMode.UNNECESSARY;
import static java.util.Collections.emptyMap;
import static java.util.function.Function.identity;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.IntStream.range;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.github.pfichtner.vaadoo.AnnotationFactory;
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
import lombok.Value;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Example;
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
class JdkOnlyCodeFragmentPBTest {

	@Value
	private static class Fixture {

		Jsr380CodeFragment sut;
		Annotation anno;

		private static Fixture of(Jsr380CodeFragment sut, Class<? extends Annotation> clazz) {
			return of(sut, clazz, emptyMap());
		}

		private static Fixture of(Jsr380CodeFragment sut, Class<? extends Annotation> clazz, Map<String, Object> data) {
			return new Fixture(sut, AnnotationFactory.make(clazz, data));
		}

		public void noException(Object v, Class<?>... types) {
			for (Class<?> type : types) {
				assertThatNoException().isThrownBy(() -> accept(v, type));
			}
		}

		public void nullPointerExceptionIf(boolean b, Object v, Class<?>... types) {
			for (Class<?> type : types) {
				assertException(b, NullPointerException.class, v, type);
			}
		}

		public void illegalArgumentExceptionIf(boolean b, Object v, Class<?>... types) {
			for (Class<?> type : types) {
				assertException(b, IllegalArgumentException.class, v, type);
			}
		}

		private void assertException(boolean b, Class<? extends Exception> exceptionType, Object v, Class<?> type) {
			if (b) {
				assertThatExceptionOfType(exceptionType).isThrownBy(() -> accept(v, type)).withMessage("theMessage");
			} else {
				assertThatNoException().isThrownBy(() -> accept(v, type));
			}
		}

		private void accept(Object value, Class<?> param1Type) {
			Method method = only(Arrays.stream(sut.getClass().getMethods()).filter(m -> m.getName().equals("check"))
					.filter(m -> m.getParameterTypes().length == 2 && m.getParameterTypes()[0].isInstance(anno)
							&& m.getParameterTypes()[1].isAssignableFrom(param1Type)));
			try {
				method.invoke(sut, anno, convert(param1Type, value));
			} catch (IllegalAccessException | IllegalArgumentException e) {
				throw new RuntimeException(e);
			} catch (InvocationTargetException e) {
				Throwable cause = e.getCause();
				throw cause instanceof RuntimeException ? (RuntimeException) cause : new RuntimeException(e);
			}
		}

		private static <T> T only(Stream<T> stream) {
			return stream.reduce((_ign1, _ign2) -> {
				throw new IllegalStateException("multiple elements");
			}).orElseThrow(() -> new IllegalStateException("no match"));
		}

		private static Object convert(Class<?> target, Object value) {
			if (value == null)
				return null;

			if (target == String.class)
				return String.valueOf(value);

			if (target.isPrimitive()) {
				if (target == byte.class)
					return ((Number) value).byteValue();
				if (target == short.class)
					return ((Number) value).shortValue();
				if (target == int.class)
					return ((Number) value).intValue();
				if (target == long.class)
					return ((Number) value).longValue();
				if (target == float.class)
					return ((Number) value).floatValue();
				if (target == double.class)
					return ((Number) value).doubleValue();
				if (target == char.class)
					return (char) ((Number) value).intValue();
				if (target == boolean.class)
					return value;
			}

			// wrapper types
			if (Number.class.isAssignableFrom(target) && value instanceof Number) {
				Number n = (Number) value;
				if (target == Byte.class)
					return n.byteValue();
				if (target == Short.class)
					return n.shortValue();
				if (target == Integer.class)
					return n.intValue();
				if (target == Long.class)
					return n.longValue();
				if (target == Float.class)
					return n.floatValue();
				if (target == Double.class)
					return n.doubleValue();
				if (target == BigInteger.class)
					return BigInteger.valueOf(n.longValue());
				if (target == BigDecimal.class) {
					BigDecimal bd = BigDecimal.valueOf(n.doubleValue());
					return value.getClass() == Integer.class || value.getClass() == Long.class
							? bd.setScale(0, UNNECESSARY)
							: bd;
				}
			}

			return target.cast(value);
		}
	}

	Jsr380CodeFragment sut = new JdkOnlyCodeFragment();

	Class<?>[] booleanTypes = new Class<?>[] { boolean.class, Boolean.class };
	Class<?>[] numberTypes = new Class<?>[] { byte.class, short.class, int.class, long.class, Byte.class, Short.class,
			Integer.class, Long.class, BigInteger.class, BigDecimal.class };

	Fixture notEmpty = Fixture.of(sut, NotEmpty.class);

	Fixture notNull = Fixture.of(sut, NotNull.class);
	Fixture notBlank = Fixture.of(sut, NotBlank.class);
	Fixture pattern = Fixture.of(sut, Pattern.class, Map.of("regexp", "\\d{2}"));

	Fixture size2To4 = Fixture.of(sut, Size.class, Map.of("min", 2, "max", 4));
	Fixture digitsInt2Fraction0 = Fixture.of(sut, Digits.class, Map.of("integer", 2, "fraction", 0));
	Fixture min0 = Fixture.of(sut, Min.class, Map.of("value", 0L));
	Fixture max100 = Fixture.of(sut, Max.class, Map.of("value", 100L));
	Fixture decimalMin2 = Fixture.of(sut, DecimalMin.class, Map.of("value", "2"));
	Fixture decimalMax5 = Fixture.of(sut, DecimalMax.class, Map.of("value", "5"));

	Fixture assertTrue = Fixture.of(sut, AssertTrue.class);
	Fixture assertFalse = Fixture.of(sut, AssertFalse.class);

	Fixture positive = Fixture.of(sut, Positive.class);
	Fixture positiveOrZero = Fixture.of(sut, PositiveOrZero.class);
	Fixture negative = Fixture.of(sut, Negative.class);
	Fixture negativeOrZero = Fixture.of(sut, NegativeOrZero.class);

	Fixture future = Fixture.of(sut, Future.class);
	Fixture futureOrPresent = Fixture.of(sut, FutureOrPresent.class);
	Fixture past = Fixture.of(sut, Past.class);
	Fixture pastOrPresent = Fixture.of(sut, PastOrPresent.class);

	Class<?>[] byteTypes = new Class<?>[] { byte.class, Byte.class };
	Class<?>[] shortTypes = new Class<?>[] { short.class, Short.class };
	Class<?>[] intTypes = new Class<?>[] { int.class, Integer.class };
	Class<?>[] longTypes = new Class<?>[] { long.class, Long.class };

	String[] emptyStringArray = new String[0];
	Object[] emptyObjectArray = new Object[0];

	// NotNull: generated non-null strings should never fail
	@Property
	void notNull_should_accept_any_non_null_string(@ForAll("nonNullStrings") String value) {
		notNull.noException(value, String.class);
	}

	@Example
	void notNull_should_throw_on_null() {
		notNull.nullPointerExceptionIf(true, null, Object.class);
	}

	// NotBlank: non-blank strings should pass
	@Property
	void notBlank_passes_for_non_blank(@ForAll("nonBlankStrings") String value) {
		notBlank.noException(value, String.class);
	}

	@Property
	void notBlank_fails_for_blank(@ForAll("blankStrings") String value) {
		notBlank.illegalArgumentExceptionIf(true, value, String.class);
	}

	// Pattern: generated strings matching the pattern should pass
	@Property
	void pattern_matches_generated_values(@ForAll("twoDigits") String value) {
		pattern.noException(value, String.class);
	}

	// NotEmpty variants: char sequence, collection and map are covered in unit
	// tests;
	// here we at least property-test char sequences and arrays
	@Property
	void notEmpty_charsequence_passes(@ForAll("nonBlankStrings") String value) {
		notEmpty.noException(value, String.class);
	}

	@Property
	void notEmpty_collection_passes(@ForAll("nonEmptyLists") List<String> value) {
		notEmpty.noException(value, List.class);
	}

	@Property
	void notEmpty_map_passes(@ForAll("nonEmptyMaps") Map<String, Integer> value) {
		notEmpty.noException(value, Map.class);
	}

	@Property
	void notEmpty_array_fails_for_empty(@ForAll("emptyArray") Object value) {
		notEmpty.illegalArgumentExceptionIf(true, value, Object[].class, value.getClass());
	}

	// Size: generate valid sizes and invalid sizes explicitly
	@Property
	void size_accepts_values_within_bounds(@ForAll("sizeStrings") String value) {
		size2To4.noException(value, String.class);
	}

	@Property
	void size_rejects_too_short(@ForAll("shortStrings") String value) {
		size2To4.illegalArgumentExceptionIf(true, value, String.class);
	}

	@Property
	void size_collection_accepts_within_bounds(@ForAll("sizeLists") List<String> value) {
		size2To4.noException(value, List.class);
	}

	@Property
	void size_collection_rejects_too_short(@ForAll("shortLists") List<String> value) {
		size2To4.illegalArgumentExceptionIf(true, value, List.class);
	}

	@Property
	void size_map_accepts_within_bounds(@ForAll("sizeMaps") Map<String, Integer> value) {
		size2To4.noException(value, Map.class);
	}

	@Property
	void size_map_rejects_too_short(@ForAll("shortMaps") Map<String, Integer> value) {
		size2To4.illegalArgumentExceptionIf(true, value, Map.class);
	}

	@Property
	void size_array_accepts_within_bounds(@ForAll("sizeArrays") Object[] value) {
		size2To4.noException(value, Object[].class);
	}

	@Property
	void size_array_rejects_too_short(@ForAll("shortArrays") Object[] value) {
		size2To4.illegalArgumentExceptionIf(true, value, Object[].class);
	}

	// AssertTrue / AssertFalse
	@Property
	void assertTrue_accepts_true(@ForAll boolean value) {
		assertTrue.illegalArgumentExceptionIf(!value, value, booleanTypes);
		assertFalse.illegalArgumentExceptionIf(value, value, booleanTypes);
	}

	// Min / Max for ints
	@Property
	void min_accepts_values_at_or_above(@ForAll byte value) {
		min0.illegalArgumentExceptionIf(value < 0, value, byteTypes);
	}

	@Property
	void min_accepts_values_at_or_above(@ForAll short value) {
		min0.illegalArgumentExceptionIf(value < 0, value, shortTypes);
	}

	@Property
	void min_accepts_values_at_or_above(@ForAll int value) {
		min0.illegalArgumentExceptionIf(value < 0, value, intTypes);
	}

	@Property
	void min_accepts_values_at_or_above(@ForAll long value) {
		min0.illegalArgumentExceptionIf(value < 0, value, longTypes);
	}

	@Property
	void max_accepts_values_below_limit(@ForAll byte value) {
		max100.illegalArgumentExceptionIf(value > 100, value, byteTypes);
	}

	@Property
	void max_accepts_values_below_limit(@ForAll short value) {
		max100.illegalArgumentExceptionIf(value > 100, value, shortTypes);
	}

	@Property
	void max_accepts_values_below_limit(@ForAll int value) {
		max100.illegalArgumentExceptionIf(value > 100, value, intTypes);
	}

	@Property
	void max_accepts_values_below_limit(@ForAll long value) {
		max100.illegalArgumentExceptionIf(value > 100, value, longTypes);
	}

	@Property
	void decimalMin_accepts_numbers_greater_or_equal(@ForAll byte value) {
		decimalMin2.illegalArgumentExceptionIf(value < 2, value, byteTypes);
		decimalMin2.illegalArgumentExceptionIf(value < 2, value, String.class);
	}

	@Property
	void decimalMin_accepts_numbers_greater_or_equal(@ForAll short value) {
		decimalMin2.illegalArgumentExceptionIf(value < 2, value, shortTypes);
		decimalMin2.illegalArgumentExceptionIf(value < 2, value, String.class);
	}

	@Property
	void decimalMin_accepts_numbers_greater_or_equal(@ForAll int value) {
		decimalMin2.illegalArgumentExceptionIf(value < 2, value, intTypes);
		decimalMin2.illegalArgumentExceptionIf(value < 2, value, String.class);
	}

	@Property
	void decimalMin_accepts_numbers_greater_or_equal(@ForAll long value) {
		decimalMin2.illegalArgumentExceptionIf(value < 2, value, longTypes);
		decimalMin2.illegalArgumentExceptionIf(value < 2, value, String.class);
	}

	@Property
	void decimalMax_rejects_greater_numbers(@ForAll byte value) {
		decimalMax5.illegalArgumentExceptionIf(value > 5, value, byteTypes);
		decimalMax5.illegalArgumentExceptionIf(value > 5, String.valueOf(value), String.class);
	}

	@Property
	void decimalMax_rejects_greater_numbers(@ForAll short value) {
		decimalMax5.illegalArgumentExceptionIf(value > 5, value, shortTypes);
		decimalMax5.illegalArgumentExceptionIf(value > 5, String.valueOf(value), String.class);
	}

	@Property
	void decimalMax_rejects_greater_numbers(@ForAll int value) {
		decimalMax5.illegalArgumentExceptionIf(value > 5, value, intTypes);
		decimalMax5.illegalArgumentExceptionIf(value > 5, String.valueOf(value), String.class);
	}

	@Property
	void decimalMax_rejects_greater_numbers(@ForAll long value) {
		decimalMax5.illegalArgumentExceptionIf(value > 5, value, longTypes);
		decimalMax5.illegalArgumentExceptionIf(value > 5, String.valueOf(value), String.class);
	}

	// Digits and sign related constraints
	@Property
	void digits_rejects_too_many_integer_digits(@ForAll byte value) {
		digitsInt2Fraction0.illegalArgumentExceptionIf(value < -99 || value > 99, value, byteTypes);
	}

	@Property
	void digits_rejects_too_many_integer_digits(@ForAll short value) {
		digitsInt2Fraction0.illegalArgumentExceptionIf(value < -99 || value > 99, value, shortTypes);
	}

	@Property
	void digits_rejects_too_many_integer_digits(@ForAll int value) {
		digitsInt2Fraction0.illegalArgumentExceptionIf(value < -99 || value > 99, value, intTypes);
	}

	@Property
	void digits_rejects_too_many_integer_digits(@ForAll long value) {
		digitsInt2Fraction0.illegalArgumentExceptionIf(value < -99 || value > 99, value, longTypes);
	}

	@Property
	void zero_behaviour() {
		positive_and_negative_behaviour_zero(byteTypes);
		positive_and_negative_behaviour_zero(shortTypes);
		positive_and_negative_behaviour_zero(intTypes);
		positive_and_negative_behaviour_zero(longTypes);
	}

	@Property
	void positive_behaviour(@ForAll @net.jqwik.api.constraints.Positive byte v) {
		positive_and_negative_behaviour_greater_zero(v, byteTypes);
	}

	@Property
	void negative_behaviour(@ForAll @net.jqwik.api.constraints.Negative byte v) {
		positive_and_negative_behaviour_less_zero(v, byteTypes);
	}

	@Property
	void positive_behaviour(@ForAll @net.jqwik.api.constraints.Positive short v) {
		positive_and_negative_behaviour_greater_zero(v, shortTypes);
	}

	@Property
	void negative_behaviour(@ForAll @net.jqwik.api.constraints.Negative short v) {
		positive_and_negative_behaviour_less_zero(v, shortTypes);
	}

	@Property
	void positive_behaviour(@ForAll @net.jqwik.api.constraints.Positive int v) {
		positive_and_negative_behaviour_greater_zero(v, intTypes);
	}

	@Property
	void negative_behaviour(@ForAll @net.jqwik.api.constraints.Negative int v) {
		positive_and_negative_behaviour_less_zero(v, intTypes);
	}

	@Property
	void positive_behaviour(@ForAll @net.jqwik.api.constraints.Positive long v) {
		positive_and_negative_behaviour_greater_zero(v, longTypes);
	}

	@Property
	void negative_behaviour(@ForAll @net.jqwik.api.constraints.Negative long v) {
		positive_and_negative_behaviour_less_zero(v, longTypes);
	}

	// TODO add BigInteger, BigDecimal

	private void positive_and_negative_behaviour_less_zero(long v, Class<?>... types) {
		positive.illegalArgumentExceptionIf(true, v, types);
		positiveOrZero.illegalArgumentExceptionIf(true, v, types);
		negative.noException(v, types);
		negativeOrZero.noException(v, types);
	}

	private void positive_and_negative_behaviour_greater_zero(long v, Class<?>... types) {
		positive.noException(v, types);
		positiveOrZero.noException(v, types);
		negative.illegalArgumentExceptionIf(true, v, types);
		negativeOrZero.illegalArgumentExceptionIf(true, v, types);
	}

	private void positive_and_negative_behaviour_zero(Class<?>... types) {
		positive.illegalArgumentExceptionIf(true, (long) 0, types);
		positiveOrZero.noException((long) 0, types);
		negative.illegalArgumentExceptionIf(true, (long) 0, types);
		negativeOrZero.noException((long) 0, types);
	}

	// Temporal: Past / Future for LocalDate
	@Property
	void past_accepts_dates_before_today(@ForAll("pastDates") LocalDate d) {
		past.noException(d, LocalDate.class);
	}

	@Test
	void future_rejects_past_date() {
		future.illegalArgumentExceptionIf(true, LocalDate.now().minusDays(1), LocalDate.class);
	}

	@Property
	void future_dates_are_future_and_rejected_by_past(@ForAll("futureDates") LocalDate d) {
		// future dates should be rejected by @Past and accepted by @Future
		past.illegalArgumentExceptionIf(true, d, LocalDate.class);
		future.noException(d, LocalDate.class);
	}

	@Property
	void futureOrPresent_accepts_present_and_future(@ForAll("futureOrPresentDates") LocalDate d) {
		// FutureOrPresent should accept today and future dates
		futureOrPresent.noException(d, LocalDate.class);

		// PastOrPresent should reject strictly future dates, but accept today
		pastOrPresent.illegalArgumentExceptionIf(d.isAfter(LocalDate.now()), d, LocalDate.class);
	}

	@Property
	void pastOrPresent_accepts_present_and_past(@ForAll("pastOrPresentDates") LocalDate d) {
		// PastOrPresent should accept today and past dates
		pastOrPresent.noException(d, LocalDate.class);

		// FutureOrPresent should reject strictly past dates, but accept today
		futureOrPresent.illegalArgumentExceptionIf(d.isBefore(LocalDate.now()), d, LocalDate.class);
	}

	/* --- Arbitraries --- */

	// Providers for named arbitraries used by @ForAll("name")
	@Provide
	Arbitrary<String> nonNullStrings() {
		return Arbitraries.strings().ofMinLength(0).ofMaxLength(20);
	}

	@Provide
	Arbitrary<String> blankStrings() {
		return Arbitraries.strings().withChars(' ', '\t', '\n', '\r', '\f').ofMinLength(0).ofMaxLength(20);
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
		return Arbitraries.strings().ofMinLength(2).ofMaxLength(4);
	}

	@Provide
	Arbitrary<String> shortStrings() {
		return Arbitraries.strings().ofMaxLength(1);
	}

	@Provide
	Arbitrary<List<String>> nonEmptyLists() {
		return Arbitraries.strings().ofMinLength(1).list().ofMinSize(1).ofMaxSize(5);
	}

	@Provide
	Arbitrary<Map<String, Integer>> nonEmptyMaps() {
		return nonEmptyLists().map(l -> Map.of(l.get(0), 1));
	}

	@Provide
	Arbitrary<Object> emptyArray() {
		return Arbitraries.of(Object.class, String.class, Number.class, Boolean.class, Byte.class, Short.class,
				Integer.class, Long.class, Double.class, Float.class, BigInteger.class, BigDecimal.class,
				Collection.class, List.class, Set.class, Map.class).map(component -> Array.newInstance(component, 0));
	}

	@Provide
	Arbitrary<Object[]> nonEmptyArrays() {
		return Arbitraries.strings().list().ofMinSize(1).ofMaxSize(5).map(l -> l.toArray(emptyStringArray));
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
	Arbitrary<List<String>> sizeLists() {
		return Arbitraries.strings().list().ofMinSize(2).ofMaxSize(4);
	}

	@Provide
	Arbitrary<List<String>> shortLists() {
		return Arbitraries.strings().list().ofMaxSize(1);
	}

	@Provide
	Arbitrary<Map<String, Integer>> sizeMaps() {
		return sizeLists().map(l -> range(0, l.size()).boxed().collect(toMap(i -> "k" + i, identity())));
	}

	@Provide
	Arbitrary<Map<String, Integer>> shortMaps() {
		return shortLists().map(l -> l.isEmpty() ? Map.of() : Map.of(l.get(0), 1));
	}

	@Provide
	Arbitrary<Object[]> sizeArrays() {
		return sizeLists().map(l -> l.toArray(emptyObjectArray));
	}

	@Provide
	Arbitrary<Object[]> shortArrays() {
		return shortLists().map(l -> l.toArray(emptyObjectArray));
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
