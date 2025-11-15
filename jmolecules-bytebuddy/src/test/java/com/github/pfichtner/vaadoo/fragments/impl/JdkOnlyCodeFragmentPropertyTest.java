package com.github.pfichtner.vaadoo.fragments.impl;

import static java.lang.reflect.Proxy.newProxyInstance;
import static java.util.function.Predicate.not;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.math.BigInteger;
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
	void notEmpty_collection_passes(@ForAll("nonEmptyLists") java.util.List<String> l) {
		var a = anno(NotEmpty.class);
		assertDoesNotThrow(() -> sut.check(a, l));
	}

	@Property
	void notEmpty_map_passes(@ForAll("nonEmptyMaps") java.util.Map<String, Integer> m) {
		var a = anno(NotEmpty.class);
		assertDoesNotThrow(() -> sut.check(a, m));
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

	@Property
	void size_collection_accepts_within_bounds(@ForAll("sizeLists") java.util.List<String> l) {
		var a = anno(Size.class, Map.of("min", 2, "max", 4));
		assertDoesNotThrow(() -> sut.check(a, l));
	}

	@Property
	void size_collection_rejects_too_short(@ForAll("shortLists") java.util.List<String> l) {
		var a = anno(Size.class, Map.of("min", 2, "max", 4));
		assertThrows(IllegalArgumentException.class, () -> sut.check(a, l));
	}

	@Property
	void size_map_accepts_within_bounds(@ForAll("sizeMaps") java.util.Map<String, Integer> m) {
		var a = anno(Size.class, Map.of("min", 2, "max", 4));
		assertDoesNotThrow(() -> sut.check(a, m));
	}

	@Property
	void size_map_rejects_too_short(@ForAll("shortMaps") java.util.Map<String, Integer> m) {
		var a = anno(Size.class, Map.of("min", 2, "max", 4));
		assertThrows(IllegalArgumentException.class, () -> sut.check(a, m));
	}

	@Property
	void size_array_accepts_within_bounds(@ForAll("sizeArrays") Object[] arr) {
		var a = anno(Size.class, Map.of("min", 2, "max", 4));
		assertDoesNotThrow(() -> sut.check(a, arr));
	}

	@Property
	void size_array_rejects_too_short(@ForAll("shortArrays") Object[] arr) {
		var a = anno(Size.class, Map.of("min", 2, "max", 4));
		assertThrows(IllegalArgumentException.class, () -> sut.check(a, arr));
	}

	// AssertTrue / AssertFalse
	@Property
	void assertTrue_accepts_true(@ForAll boolean b) {
		var t = anno(AssertTrue.class);
		var f = anno(AssertFalse.class);
		if (b) {
			assertDoesNotThrow(() -> sut.check(t, b));
			assertThrows(IllegalArgumentException.class, () -> sut.check(f, b));
		} else {
			assertThrows(IllegalArgumentException.class, () -> sut.check(t, b));
			assertDoesNotThrow(() -> sut.check(f, b));
		}
	}

	// Min / Max for ints
	@Property
	void min_accepts_values_at_or_above(@ForAll int v) {
		var a = anno(Min.class, Map.of("value", 0L));

		byte bv = (byte) v;
		short sv = (short) v;
		int iv = v;
		long lv = v;
		Byte bObj = bv;
		Short sObj = sv;
		Integer iObj = iv;
		Long lObj = lv;
		BigInteger bi = BigInteger.valueOf(v);
		BigDecimal bd = BigDecimal.valueOf(v);

		// primitives
		if (bv >= 0)
			assertDoesNotThrow(() -> sut.check(a, bv));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, bv));

		if (sv >= 0)
			assertDoesNotThrow(() -> sut.check(a, sv));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, sv));

		if (iv >= 0)
			assertDoesNotThrow(() -> sut.check(a, iv));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, iv));

		if (lv >= 0)
			assertDoesNotThrow(() -> sut.check(a, lv));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, lv));

		// wrappers
		if (bObj >= 0)
			assertDoesNotThrow(() -> sut.check(a, bObj));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, bObj));

		if (sObj >= 0)
			assertDoesNotThrow(() -> sut.check(a, sObj));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, sObj));

		if (iObj >= 0)
			assertDoesNotThrow(() -> sut.check(a, iObj));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, iObj));

		if (lObj >= 0)
			assertDoesNotThrow(() -> sut.check(a, lObj));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, lObj));

		if (bi.compareTo(BigInteger.ZERO) >= 0)
			assertDoesNotThrow(() -> sut.check(a, bi));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, bi));

		if (bd.compareTo(BigDecimal.ZERO) >= 0)
			assertDoesNotThrow(() -> sut.check(a, bd));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, bd));
	}

	@Property
	void max_accepts_values_below_limit(@ForAll int v) {
		var a = anno(Max.class, Map.of("value", 100L));

		byte bv = (byte) v;
		short sv = (short) v;
		int iv = v;
		long lv = v;
		Byte bObj = bv;
		Short sObj = sv;
		Integer iObj = iv;
		Long lObj = lv;
		BigInteger bi = BigInteger.valueOf(v);
		BigDecimal bd = BigDecimal.valueOf(v);

		if (bv <= 100)
			assertDoesNotThrow(() -> sut.check(a, bv));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, bv));

		if (sv <= 100)
			assertDoesNotThrow(() -> sut.check(a, sv));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, sv));

		if (iv <= 100)
			assertDoesNotThrow(() -> sut.check(a, iv));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, iv));

		if (lv <= 100)
			assertDoesNotThrow(() -> sut.check(a, lv));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, lv));

		if (bObj <= 100)
			assertDoesNotThrow(() -> sut.check(a, bObj));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, bObj));

		if (sObj <= 100)
			assertDoesNotThrow(() -> sut.check(a, sObj));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, sObj));

		if (iObj <= 100)
			assertDoesNotThrow(() -> sut.check(a, iObj));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, iObj));

		if (lObj <= 100)
			assertDoesNotThrow(() -> sut.check(a, lObj));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, lObj));

		if (bi.compareTo(BigInteger.valueOf(100)) <= 0)
			assertDoesNotThrow(() -> sut.check(a, bi));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, bi));

		if (bd.compareTo(BigDecimal.valueOf(100)) <= 0)
			assertDoesNotThrow(() -> sut.check(a, bd));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, bd));
	}

	// DecimalMin / DecimalMax for CharSequence values (strings representing
	// numbers)
	@Property
	void decimalMin_accepts_strings_greater_or_equal(@ForAll("numericStringsGE2") String s) {
		var a = anno(DecimalMin.class, Map.of("value", "2"));
		assertDoesNotThrow(() -> sut.check(a, s));
	}

	@Property
	void decimalMin_accepts_numbers_greater_or_equal(@ForAll int v) {
		var a = anno(DecimalMin.class, Map.of("value", "2"));

		byte bv = (byte) v;
		short sv = (short) v;
		int iv = v;
		long lv = v;
		Byte bObj = bv;
		Short sObj = sv;
		Integer iObj = iv;
		Long lObj = lv;
		BigInteger bi = BigInteger.valueOf(v);
		BigDecimal bd = BigDecimal.valueOf(v);

		if (bv >= 2)
			assertDoesNotThrow(() -> sut.check(a, bv));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, bv));

		if (sv >= 2)
			assertDoesNotThrow(() -> sut.check(a, sv));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, sv));

		if (iv >= 2)
			assertDoesNotThrow(() -> sut.check(a, iv));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, iv));

		if (lv >= 2)
			assertDoesNotThrow(() -> sut.check(a, lv));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, lv));

		if (bObj >= 2)
			assertDoesNotThrow(() -> sut.check(a, bObj));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, bObj));

		if (sObj >= 2)
			assertDoesNotThrow(() -> sut.check(a, sObj));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, sObj));

		if (iObj >= 2)
			assertDoesNotThrow(() -> sut.check(a, iObj));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, iObj));

		if (lObj >= 2)
			assertDoesNotThrow(() -> sut.check(a, lObj));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, lObj));

		if (bi.compareTo(BigInteger.valueOf(2)) >= 0)
			assertDoesNotThrow(() -> sut.check(a, bi));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, bi));

		if (bd.compareTo(BigDecimal.valueOf(2)) >= 0)
			assertDoesNotThrow(() -> sut.check(a, bd));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, bd));
	}

	@Property
	void decimalMax_rejects_greater_strings(@ForAll("numericStringsGT5") String s) {
		var a = anno(DecimalMax.class, Map.of("value", "5"));
		assertThrows(IllegalArgumentException.class, () -> sut.check(a, s));
	}

	@Property
	void decimalMax_rejects_greater_numbers(@ForAll int v) {
		var a = anno(DecimalMax.class, Map.of("value", "5"));

		byte bv = (byte) v;
		short sv = (short) v;
		int iv = v;
		long lv = v;
		Byte bObj = bv;
		Short sObj = sv;
		Integer iObj = iv;
		Long lObj = lv;
		BigInteger bi = BigInteger.valueOf(v);
		BigDecimal bd = BigDecimal.valueOf(v);

		if (bv <= 5)
			assertDoesNotThrow(() -> sut.check(a, bv));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, bv));

		if (sv <= 5)
			assertDoesNotThrow(() -> sut.check(a, sv));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, sv));

		if (iv <= 5)
			assertDoesNotThrow(() -> sut.check(a, iv));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, iv));

		if (lv <= 5)
			assertDoesNotThrow(() -> sut.check(a, lv));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, lv));

		if (bObj <= 5)
			assertDoesNotThrow(() -> sut.check(a, bObj));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, bObj));

		if (sObj <= 5)
			assertDoesNotThrow(() -> sut.check(a, sObj));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, sObj));

		if (iObj <= 5)
			assertDoesNotThrow(() -> sut.check(a, iObj));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, iObj));

		if (lObj <= 5)
			assertDoesNotThrow(() -> sut.check(a, lObj));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, lObj));

		if (bi.compareTo(BigInteger.valueOf(5)) <= 0)
			assertDoesNotThrow(() -> sut.check(a, bi));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, bi));

		if (bd.compareTo(BigDecimal.valueOf(5)) <= 0)
			assertDoesNotThrow(() -> sut.check(a, bd));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, bd));
	}

	// Digits and sign related constraints
	@Property
	void digits_rejects_too_many_integer_digits(@ForAll int v) {
		var a = anno(Digits.class, Map.of("integer", 2, "fraction", 0));

		byte bv = (byte) v;
		short sv = (short) v;
		int iv = v;
		long lv = v;
		Byte bObj = bv;
		Short sObj = sv;
		Integer iObj = iv;
		Long lObj = lv;
		BigInteger bi = BigInteger.valueOf(v);
		BigDecimal bd = BigDecimal.valueOf(v);

		Predicate<Long> fits = x -> Math.abs(x) <= 99;

		if (fits.test((long) bv))
			assertDoesNotThrow(() -> sut.check(a, bv));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, bv));

		if (fits.test((long) sv))
			assertDoesNotThrow(() -> sut.check(a, sv));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, sv));

		if (fits.test((long) iv))
			assertDoesNotThrow(() -> sut.check(a, iv));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, iv));

		if (fits.test(lv))
			assertDoesNotThrow(() -> sut.check(a, lv));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, lv));

		if (fits.test(bObj.longValue()))
			assertDoesNotThrow(() -> sut.check(a, bObj));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, bObj));

		if (fits.test(sObj.longValue()))
			assertDoesNotThrow(() -> sut.check(a, sObj));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, sObj));

		if (fits.test(iObj.longValue()))
			assertDoesNotThrow(() -> sut.check(a, iObj));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, iObj));

		if (fits.test(lObj.longValue()))
			assertDoesNotThrow(() -> sut.check(a, lObj));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, lObj));

		if (bi.abs().toString().length() <= 2)
			assertDoesNotThrow(() -> sut.check(a, bi));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, bi));

		if (bd.abs().toBigInteger().toString().length() <= 2)
			assertDoesNotThrow(() -> sut.check(a, bd));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(a, bd));
	}

	@Property
	void positive_and_negative_behaviour(@ForAll int v) {
		var p = anno(Positive.class);
		var pz = anno(PositiveOrZero.class);
		var n = anno(Negative.class);
		var nz = anno(NegativeOrZero.class);

		byte bv = (byte) v;
		short sv = (short) v;
		int iv = v;
		long lv = v;
		Byte bObj = bv;
		Short sObj = sv;
		Integer iObj = iv;
		Long lObj = lv;
		BigInteger bi = BigInteger.valueOf(v);
		BigDecimal bd = BigDecimal.valueOf(v);

		// Positive
		if (bv > 0)
			assertDoesNotThrow(() -> sut.check(p, bv));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(p, bv));

		if (sv > 0)
			assertDoesNotThrow(() -> sut.check(p, sv));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(p, sv));

		if (iv > 0)
			assertDoesNotThrow(() -> sut.check(p, iv));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(p, iv));

		if (lv > 0)
			assertDoesNotThrow(() -> sut.check(p, lv));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(p, lv));

		if (bObj > 0)
			assertDoesNotThrow(() -> sut.check(p, bObj));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(p, bObj));

		if (sObj > 0)
			assertDoesNotThrow(() -> sut.check(p, sObj));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(p, sObj));

		if (iObj > 0)
			assertDoesNotThrow(() -> sut.check(p, iObj));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(p, iObj));

		if (lObj > 0)
			assertDoesNotThrow(() -> sut.check(p, lObj));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(p, lObj));

		if (bi.compareTo(BigInteger.ZERO) > 0)
			assertDoesNotThrow(() -> sut.check(p, bi));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(p, bi));

		if (bd.compareTo(BigDecimal.ZERO) > 0)
			assertDoesNotThrow(() -> sut.check(p, bd));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(p, bd));

		// PositiveOrZero
		if (bv >= 0)
			assertDoesNotThrow(() -> sut.check(pz, bv));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(pz, bv));

		if (sv >= 0)
			assertDoesNotThrow(() -> sut.check(pz, sv));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(pz, sv));

		if (iv >= 0)
			assertDoesNotThrow(() -> sut.check(pz, iv));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(pz, iv));

		if (lv >= 0)
			assertDoesNotThrow(() -> sut.check(pz, lv));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(pz, lv));

		if (bObj >= 0)
			assertDoesNotThrow(() -> sut.check(pz, bObj));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(pz, bObj));

		if (sObj >= 0)
			assertDoesNotThrow(() -> sut.check(pz, sObj));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(pz, sObj));

		if (iObj >= 0)
			assertDoesNotThrow(() -> sut.check(pz, iObj));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(pz, iObj));

		if (lObj >= 0)
			assertDoesNotThrow(() -> sut.check(pz, lObj));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(pz, lObj));

		if (bi.compareTo(BigInteger.ZERO) >= 0)
			assertDoesNotThrow(() -> sut.check(pz, bi));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(pz, bi));

		if (bd.compareTo(BigDecimal.ZERO) >= 0)
			assertDoesNotThrow(() -> sut.check(pz, bd));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(pz, bd));

		// Negative
		if (bv < 0)
			assertDoesNotThrow(() -> sut.check(n, bv));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(n, bv));

		if (sv < 0)
			assertDoesNotThrow(() -> sut.check(n, sv));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(n, sv));

		if (iv < 0)
			assertDoesNotThrow(() -> sut.check(n, iv));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(n, iv));

		if (lv < 0)
			assertDoesNotThrow(() -> sut.check(n, lv));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(n, lv));

		if (bObj < 0)
			assertDoesNotThrow(() -> sut.check(n, bObj));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(n, bObj));

		if (sObj < 0)
			assertDoesNotThrow(() -> sut.check(n, sObj));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(n, sObj));

		if (iObj < 0)
			assertDoesNotThrow(() -> sut.check(n, iObj));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(n, iObj));

		if (lObj < 0)
			assertDoesNotThrow(() -> sut.check(n, lObj));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(n, lObj));

		if (bi.compareTo(BigInteger.ZERO) < 0)
			assertDoesNotThrow(() -> sut.check(n, bi));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(n, bi));

		if (bd.compareTo(BigDecimal.ZERO) < 0)
			assertDoesNotThrow(() -> sut.check(n, bd));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(n, bd));

		// NegativeOrZero
		if (bv <= 0)
			assertDoesNotThrow(() -> sut.check(nz, bv));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(nz, bv));

		if (sv <= 0)
			assertDoesNotThrow(() -> sut.check(nz, sv));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(nz, sv));

		if (iv <= 0)
			assertDoesNotThrow(() -> sut.check(nz, iv));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(nz, iv));

		if (lv <= 0)
			assertDoesNotThrow(() -> sut.check(nz, lv));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(nz, lv));

		if (bObj <= 0)
			assertDoesNotThrow(() -> sut.check(nz, bObj));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(nz, bObj));

		if (sObj <= 0)
			assertDoesNotThrow(() -> sut.check(nz, sObj));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(nz, sObj));

		if (iObj <= 0)
			assertDoesNotThrow(() -> sut.check(nz, iObj));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(nz, iObj));

		if (lObj <= 0)
			assertDoesNotThrow(() -> sut.check(nz, lObj));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(nz, lObj));

		if (bi.compareTo(BigInteger.ZERO) <= 0)
			assertDoesNotThrow(() -> sut.check(nz, bi));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(nz, bi));

		if (bd.compareTo(BigDecimal.ZERO) <= 0)
			assertDoesNotThrow(() -> sut.check(nz, bd));
		else
			assertThrows(IllegalArgumentException.class, () -> sut.check(nz, bd));
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
	Arbitrary<java.util.List<String>> nonEmptyLists() {
		return Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).list().ofMinSize(1).ofMaxSize(5);
	}

	@Provide
	Arbitrary<java.util.Map<String, Integer>> nonEmptyMaps() {
		return nonEmptyLists().map(l -> java.util.Map.of(l.get(0), 1));
	}

	@Provide
	Arbitrary<Object[]> nonEmptyArrays() {
		return Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).list().ofMinSize(1).ofMaxSize(5)
				.map(list -> list.toArray(new String[0]));
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
	Arbitrary<java.util.List<String>> sizeLists() {
		return Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).list().ofMinSize(2).ofMaxSize(4);
	}

	@Provide
	Arbitrary<java.util.List<String>> shortLists() {
		return Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).list().ofMaxSize(1);
	}

	@Provide
	Arbitrary<java.util.Map<String, Integer>> sizeMaps() {
		return sizeLists().map(l -> {
			java.util.Map<String, Integer> m = new java.util.LinkedHashMap<>();
			for (int i = 0; i < l.size(); i++) {
				m.put("k" + i, i);
			}
			return m;
		});
	}

	@Provide
	Arbitrary<java.util.Map<String, Integer>> shortMaps() {
		return shortLists().map(l -> {
			if (l.isEmpty())
				return java.util.Map.of();
			return java.util.Map.of(l.get(0), 1);
		});
	}

	@Provide
	Arbitrary<Object[]> sizeArrays() {
		return sizeLists().map(list -> list.toArray(new Object[0]));
	}

	@Provide
	Arbitrary<Object[]> shortArrays() {
		return shortLists().map(list -> list.toArray(new Object[0]));
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
