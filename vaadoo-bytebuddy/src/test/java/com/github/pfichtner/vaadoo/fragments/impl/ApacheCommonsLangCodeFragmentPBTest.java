package com.github.pfichtner.vaadoo.fragments.impl;

import static com.github.pfichtner.vaadoo.fragments.impl.Util.booleanTypes;
import static com.github.pfichtner.vaadoo.fragments.impl.Util.byteTypes;
import static com.github.pfichtner.vaadoo.fragments.impl.Util.intTypes;
import static com.github.pfichtner.vaadoo.fragments.impl.Util.longTypes;
import static com.github.pfichtner.vaadoo.fragments.impl.Util.shortTypes;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.github.pfichtner.vaadoo.fragments.impl.Util.Fixture;
import com.github.pfichtner.vaadoo.fragments.impl.Util.FixtureFactory;

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
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

/**
 * Property-based tests for {@link ApacheCommonsLangCodeFragment} covering the
 * same behavior exercised in the example unit tests. These tests focus on
 * generating a wide variety of valid values and also specific invalid value
 * generators for the negative cases.
 */
class ApacheCommonsLangCodeFragmentPBTest extends ConstraintArbitraries {

	FixtureFactory fixtureFactory = new FixtureFactory(new ApacheCommonsLangCodeFragment(), NullPointerException.class);

	Fixture notEmpty = fixtureFactory.create(NotEmpty.class);

	Fixture notNull = fixtureFactory.create(NotNull.class);
	Fixture notBlank = fixtureFactory.create(NotBlank.class);
	Fixture pattern = fixtureFactory.create(Pattern.class, Map.of("regexp", "\\d{2}"));

	Fixture size2To4 = fixtureFactory.create(Size.class, Map.of("min", 2, "max", 4));
	Fixture digitsInt2Fraction0 = fixtureFactory.create(Digits.class, Map.of("integer", 2, "fraction", 0));
	Fixture min0 = fixtureFactory.create(Min.class, Map.of("value", 0L));
	Fixture max100 = fixtureFactory.create(Max.class, Map.of("value", 100L));
	Fixture decimalMin2 = fixtureFactory.create(DecimalMin.class, Map.of("value", "2"));
	Fixture decimalMax5 = fixtureFactory.create(DecimalMax.class, Map.of("value", "5"));

	Fixture assertTrue = fixtureFactory.create(AssertTrue.class);
	Fixture assertFalse = fixtureFactory.create(AssertFalse.class);

	Fixture positive = fixtureFactory.create(Positive.class);
	Fixture positiveOrZero = fixtureFactory.create(PositiveOrZero.class);
	Fixture negative = fixtureFactory.create(Negative.class);
	Fixture negativeOrZero = fixtureFactory.create(NegativeOrZero.class);

	Fixture future = fixtureFactory.create(Future.class);
	Fixture futureOrPresent = fixtureFactory.create(FutureOrPresent.class);
	Fixture past = fixtureFactory.create(Past.class);
	Fixture pastOrPresent = fixtureFactory.create(PastOrPresent.class);

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

	@Example
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

}
