package com.github.pfichtner.vaadoo.fragments.impl;

import static java.lang.Math.abs;
import static java.lang.reflect.Proxy.newProxyInstance;
import static java.util.function.Function.identity;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.IntStream.range;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
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
import lombok.Value;
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

	@Value
	private static class Fixture {

		ThrowingCallable throwingCallable;

		public static Fixture of(ThrowingCallable throwingCallable) {
			return new Fixture(throwingCallable);
		}

		public void noEx() {
			assertThatNoException().isThrownBy(throwingCallable);
		}

		public void npe() {
			assertThatNullPointerException().isThrownBy(throwingCallable).withMessage("theMessage");
		}

		public void iae() {
			assertThatIllegalArgumentException().isThrownBy(throwingCallable).withMessage("theMessage");
		}

	}

	private static final String[] emptyStringArray = new String[0];
	private static final Object[] emptyObjectArray = new Object[0];

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
		Fixture.of(() -> sut.check(a, s)).noEx();
	}

	@Property
	void notNull_should_throw_on_null() {
		var a = anno(NotNull.class);
		Fixture.of(() -> sut.check(a, (Object) null)).npe();
	}

	// NotBlank: non-blank strings should pass
	@Property
	void notBlank_passes_for_non_blank(@ForAll("nonBlankStrings") String s) {
		var a = anno(NotBlank.class);
		Fixture.of(() -> sut.check(a, s)).noEx();
	}

	@Property
	void notBlank_fails_for_blank() {
		var a = anno(NotBlank.class);
		Fixture.of(() -> sut.check(a, "   ")).iae();
	}

	// Pattern: generated strings matching the pattern should pass
	@Property
	void pattern_matches_generated_values(@ForAll("twoDigits") String s) {
		var a = anno(Pattern.class, Map.of("regexp", "\\d{2}"));
		Fixture.of(() -> sut.check(a, s)).noEx();
	}

	// NotEmpty variants: char sequence, collection and map are covered in unit
	// tests;
	// here we at least property-test char sequences and arrays
	@Property
	void notEmpty_charsequence_passes(@ForAll("nonBlankStrings") String s) {
		var a = anno(NotEmpty.class);
		Fixture.of(() -> sut.check(a, s)).noEx();
	}

	@Property
	void notEmpty_collection_passes(@ForAll("nonEmptyLists") List<String> l) {
		var a = anno(NotEmpty.class);
		Fixture.of(() -> sut.check(a, l)).noEx();
	}

	@Property
	void notEmpty_map_passes(@ForAll("nonEmptyMaps") Map<String, Integer> m) {
		var a = anno(NotEmpty.class);
		Fixture.of(() -> sut.check(a, m)).noEx();
	}

	@Property
	void notEmpty_array_fails_for_empty() {
		var a = anno(NotEmpty.class);
		Fixture.of(() -> sut.check(a, emptyObjectArray)).iae();
	}

	// Size: generate valid sizes and invalid sizes explicitly
	@Property
	void size_accepts_values_within_bounds(@ForAll("sizeStrings") String s) {
		var a = anno(Size.class, Map.of("min", 2, "max", 4));
		Fixture.of(() -> sut.check(a, s)).noEx();
	}

	@Property
	void size_rejects_too_short(@ForAll("shortStrings") String s) {
		var a = anno(Size.class, Map.of("min", 2, "max", 4));
		Fixture.of(() -> sut.check(a, s)).iae();
	}

	@Property
	void size_collection_accepts_within_bounds(@ForAll("sizeLists") List<String> l) {
		var a = anno(Size.class, Map.of("min", 2, "max", 4));
		Fixture.of(() -> sut.check(a, l)).noEx();
	}

	@Property
	void size_collection_rejects_too_short(@ForAll("shortLists") List<String> l) {
		var a = anno(Size.class, Map.of("min", 2, "max", 4));
		Fixture.of(() -> sut.check(a, l)).iae();
	}

	@Property
	void size_map_accepts_within_bounds(@ForAll("sizeMaps") Map<String, Integer> m) {
		var a = anno(Size.class, Map.of("min", 2, "max", 4));
		Fixture.of(() -> sut.check(a, m)).noEx();
	}

	@Property
	void size_map_rejects_too_short(@ForAll("shortMaps") Map<String, Integer> m) {
		var a = anno(Size.class, Map.of("min", 2, "max", 4));
		Fixture.of(() -> sut.check(a, m)).iae();
	}

	@Property
	void size_array_accepts_within_bounds(@ForAll("sizeArrays") Object[] arr) {
		var a = anno(Size.class, Map.of("min", 2, "max", 4));
		Fixture.of(() -> sut.check(a, arr)).noEx();
	}

	@Property
	void size_array_rejects_too_short(@ForAll("shortArrays") Object[] arr) {
		var a = anno(Size.class, Map.of("min", 2, "max", 4));
		Fixture.of(() -> sut.check(a, arr)).iae();
	}

	// AssertTrue / AssertFalse
	@Property
	void assertTrue_accepts_true(@ForAll boolean b) {
		Fixture t = Fixture.of(() -> sut.check(anno(AssertTrue.class), b));
		Fixture f = Fixture.of(() -> sut.check(anno(AssertFalse.class), b));
		if (b) {
			t.noEx();
			f.iae();
		} else {
			t.iae();
			f.noEx();
		}
	}

	// Min / Max for ints
	@Property
	void min_accepts_values_at_or_above(@ForAll int v) {
		var a = anno(Min.class, Map.of("value", 0L));

		Fixture fbp = Fixture.of(() -> sut.check(a, (byte) v));
		Fixture fsp = Fixture.of(() -> sut.check(a, (short) v));
		Fixture fip = Fixture.of(() -> sut.check(a, (int) v));
		Fixture flp = Fixture.of(() -> sut.check(a, (long) v));

		Fixture fbob = Fixture.of(() -> sut.check(a, Byte.valueOf((byte) v)));
		Fixture fsob = Fixture.of(() -> sut.check(a, Short.valueOf((short) v)));
		Fixture fiob = Fixture.of(() -> sut.check(a, Integer.valueOf((int) v)));
		Fixture flob = Fixture.of(() -> sut.check(a, Long.valueOf((long) v)));

		Fixture fbi = Fixture.of(() -> sut.check(a, BigInteger.valueOf(v)));
		Fixture fbd = Fixture.of(() -> sut.check(a, BigDecimal.valueOf(v)));

		// primitives
		if ((byte) v >= 0)
			fbp.noEx();
		else {
			fbp.iae();
		}

		if ((short) v >= 0)
			fsp.noEx();
		else
			fsp.iae();

		if ((int) v >= 0)
			fip.noEx();
		else
			fip.iae();

		if ((long) v >= 0)
			flp.noEx();
		else
			flp.iae();

		// wrappers
		if (Byte.valueOf((byte) v) >= 0)
			fbob.noEx();
		else
			fbob.iae();

		if (Short.valueOf((short) v) >= 0)
			fsob.noEx();
		else
			fsob.iae();

		if (Integer.valueOf((int) v) >= 0)
			fiob.noEx();
		else
			fiob.iae();

		if (Long.valueOf((long) v) >= 0)
			flob.noEx();
		else
			flob.iae();

		if (BigInteger.valueOf(v).compareTo(BigInteger.ZERO) >= 0)
			fbi.noEx();
		else
			fbi.iae();

		if (BigDecimal.valueOf(v).compareTo(BigDecimal.ZERO) >= 0)
			fbd.noEx();
		else
			fbd.iae();
	}

	@Property
	void max_accepts_values_below_limit(@ForAll int v) {
		var a = anno(Max.class, Map.of("value", 100L));

		Fixture fbp = Fixture.of(() -> sut.check(a, (byte) v));
		Fixture fsp = Fixture.of(() -> sut.check(a, (short) v));
		Fixture fip = Fixture.of(() -> sut.check(a, (int) v));
		Fixture flp = Fixture.of(() -> sut.check(a, (long) v));

		Fixture fbob = Fixture.of(() -> sut.check(a, Byte.valueOf((byte) v)));
		Fixture fsob = Fixture.of(() -> sut.check(a, Short.valueOf((short) v)));
		Fixture fiob = Fixture.of(() -> sut.check(a, Integer.valueOf((int) v)));
		Fixture flob = Fixture.of(() -> sut.check(a, Long.valueOf((long) v)));

		Fixture fbi = Fixture.of(() -> sut.check(a, BigInteger.valueOf(v)));
		Fixture fbd = Fixture.of(() -> sut.check(a, BigDecimal.valueOf(v)));

		if ((byte) v <= 100)
			fbp.noEx();
		else
			fbp.iae();

		if ((short) v <= 100)
			fsp.noEx();
		else
			fsp.iae();

		if ((int) v <= 100)
			fip.noEx();
		else
			fip.iae();

		if ((long) v <= 100)
			flp.noEx();
		else
			flp.iae();

		if (Byte.valueOf((byte) v) <= 100)
			fbob.noEx();
		else
			fbob.iae();

		if (Short.valueOf((short) v) <= 100)
			fsob.noEx();
		else
			fsob.iae();

		if (Integer.valueOf((int) v) <= 100)
			fiob.noEx();
		else
			fiob.iae();

		if (Long.valueOf((long) v) <= 100)
			flob.noEx();
		else
			flob.iae();

		if (BigInteger.valueOf(v).compareTo(BigInteger.valueOf(100)) <= 0)
			fbi.noEx();
		else
			fbi.iae();

		if (BigDecimal.valueOf(v).compareTo(BigDecimal.valueOf(100)) <= 0)
			fbd.noEx();
		else
			fbd.iae();
	}

	// DecimalMin / DecimalMax for CharSequence values (strings representing
	// numbers)
	@Property
	void decimalMin_accepts_strings_greater_or_equal(@ForAll("numericStringsGE2") String s) {
		var a = anno(DecimalMin.class, Map.of("value", "2"));
		Fixture.of(() -> sut.check(a, s)).noEx();
	}

	@Property
	void decimalMin_accepts_numbers_greater_or_equal(@ForAll int v) {
		var a = anno(DecimalMin.class, Map.of("value", "2"));

		Fixture fbp = Fixture.of(() -> sut.check(a, (byte) v));
		Fixture fsp = Fixture.of(() -> sut.check(a, (short) v));
		Fixture fip = Fixture.of(() -> sut.check(a, (int) v));
		Fixture flp = Fixture.of(() -> sut.check(a, (long) v));

		Fixture fbob = Fixture.of(() -> sut.check(a, Byte.valueOf((byte) v)));
		Fixture fsob = Fixture.of(() -> sut.check(a, Short.valueOf((short) v)));
		Fixture fiob = Fixture.of(() -> sut.check(a, Integer.valueOf((int) v)));
		Fixture flob = Fixture.of(() -> sut.check(a, Long.valueOf((long) v)));

		Fixture fbi = Fixture.of(() -> sut.check(a, BigInteger.valueOf(v)));
		Fixture fbd = Fixture.of(() -> sut.check(a, BigDecimal.valueOf(v)));

		if ((byte) v >= 2)
			fbp.noEx();
		else
			fbp.iae();

		if ((short) v >= 2)
			fsp.noEx();
		else
			fsp.iae();

		if ((int) v >= 2)
			fip.noEx();
		else
			fip.iae();

		if ((long) v >= 2)
			flp.noEx();
		else
			flp.iae();

		if (Byte.valueOf((byte) v) >= 2)
			fbob.noEx();
		else
			fbob.iae();

		if (Short.valueOf((short) v) >= 2)
			fsob.noEx();
		else
			fsob.iae();

		if (Integer.valueOf((int) v) >= 2)
			fiob.noEx();
		else
			fiob.iae();

		if (Long.valueOf((long) v) >= 2)
			flob.noEx();
		else
			flob.iae();

		if (BigInteger.valueOf(v).compareTo(BigInteger.valueOf(2)) >= 0)
			fbi.noEx();
		else
			fbi.iae();

		if (BigDecimal.valueOf(v).compareTo(BigDecimal.valueOf(2)) >= 0)
			fbd.noEx();
		else
			fbd.iae();
	}

	@Property
	void decimalMax_rejects_greater_strings(@ForAll("numericStringsGT5") String s) {
		var a = anno(DecimalMax.class, Map.of("value", "5"));
		Fixture.of(() -> sut.check(a, s)).iae();
	}

	@Property
	void decimalMax_rejects_greater_numbers(@ForAll int v) {
		var a = anno(DecimalMax.class, Map.of("value", "5"));

		Fixture fbp = Fixture.of(() -> sut.check(a, (byte) v));
		Fixture fsp = Fixture.of(() -> sut.check(a, (short) v));
		Fixture fip = Fixture.of(() -> sut.check(a, (int) v));
		Fixture flp = Fixture.of(() -> sut.check(a, (long) v));

		Fixture fbob = Fixture.of(() -> sut.check(a, Byte.valueOf((byte) v)));
		Fixture fsob = Fixture.of(() -> sut.check(a, Short.valueOf((short) v)));
		Fixture fiob = Fixture.of(() -> sut.check(a, Integer.valueOf((int) v)));
		Fixture flob = Fixture.of(() -> sut.check(a, Long.valueOf((long) v)));

		Fixture fbi = Fixture.of(() -> sut.check(a, BigInteger.valueOf(v)));
		Fixture fbd = Fixture.of(() -> sut.check(a, BigDecimal.valueOf(v)));

		if ((byte) v <= 5)
			fbp.noEx();
		else
			fbp.iae();

		if ((short) v <= 5)
			fsp.noEx();
		else
			fsp.iae();

		if ((int) v <= 5)
			fip.noEx();
		else
			fip.iae();

		if ((long) v <= 5)
			flp.noEx();
		else
			flp.iae();

		if (Byte.valueOf((byte) v) <= 5)
			fbob.noEx();
		else
			fbob.iae();

		if (Short.valueOf((short) v) <= 5)
			fsob.noEx();
		else
			fsob.iae();

		if (Integer.valueOf((int) v) <= 5)
			fiob.noEx();
		else
			fiob.iae();

		if (Long.valueOf((long) v) <= 5)
			flob.noEx();
		else
			flob.iae();

		if (BigInteger.valueOf(v).compareTo(BigInteger.valueOf(5)) <= 0)
			fbi.noEx();
		else
			fbi.iae();

		if (BigDecimal.valueOf(v).compareTo(BigDecimal.valueOf(5)) <= 0)
			fbd.noEx();
		else
			fbd.iae();
	}

	// Digits and sign related constraints
	@Property
	void digits_rejects_too_many_integer_digits(@ForAll int v) {
		var a = anno(Digits.class, Map.of("integer", 2, "fraction", 0));

		Fixture fbp = Fixture.of(() -> sut.check(a, (byte) v));
		Fixture fsp = Fixture.of(() -> sut.check(a, (short) v));
		Fixture fip = Fixture.of(() -> sut.check(a, (int) v));
		Fixture flp = Fixture.of(() -> sut.check(a, (long) v));

		Fixture fbob = Fixture.of(() -> sut.check(a, Byte.valueOf((byte) v)));
		Fixture fsob = Fixture.of(() -> sut.check(a, Short.valueOf((short) v)));
		Fixture fiob = Fixture.of(() -> sut.check(a, Integer.valueOf((int) v)));
		Fixture flob = Fixture.of(() -> sut.check(a, Long.valueOf((long) v)));

		Fixture fbi = Fixture.of(() -> sut.check(a, BigInteger.valueOf(v)));
		Fixture fbd = Fixture.of(() -> sut.check(a, BigDecimal.valueOf(v)));

		Predicate<Long> fits = x -> abs(x) <= 99;

		if (fits.test((long) (byte) v))
			fbp.noEx();
		else
			fbp.iae();

		if (fits.test((long) (short) v))
			fsp.noEx();
		else
			fsp.iae();

		if (fits.test((long) (int) v))
			fip.noEx();
		else
			fip.iae();

		if (fits.test((long) v))
			flp.noEx();
		else
			flp.iae();

		if (fits.test(Byte.valueOf((byte) v).longValue()))
			fbob.noEx();
		else
			fbob.iae();

		if (fits.test(Short.valueOf((short) v).longValue()))
			fsob.noEx();
		else
			fsob.iae();

		if (fits.test(Integer.valueOf((int) v).longValue()))
			fiob.noEx();
		else
			fiob.iae();

		if (fits.test(Long.valueOf((long) v).longValue()))
			flob.noEx();
		else
			flob.iae();

		if (BigInteger.valueOf(v).abs().toString().length() <= 2)
			fbi.noEx();
		else
			fbi.iae();

		if (BigDecimal.valueOf(v).abs().toBigInteger().toString().length() <= 2)
			fbd.noEx();
		else
			fbd.iae();
	}

	@Property
	void positive_and_negative_behaviour(@ForAll int v) {
		var p = anno(Positive.class);
		var pz = anno(PositiveOrZero.class);
		var n = anno(Negative.class);
		var nz = anno(NegativeOrZero.class);

		byte bv = (byte) v;
		short sv = (short) v;
		int iv = (int) v;
		long lv = (long) v;
		Byte bObj = Byte.valueOf((byte) v);
		Short sObj = Short.valueOf((short) v);
		Integer iObj = Integer.valueOf((int) v);
		Long lObj = Long.valueOf((long) v);

		BigInteger bi = BigInteger.valueOf(v);
		BigDecimal bd = BigDecimal.valueOf(v);

		// Positive
		if (bv > 0) {
			Fixture f1 = Fixture.of(() -> sut.check(p, bv));
			f1.noEx();
		} else
			Fixture.of(() -> sut.check(p, bv)).iae();

		if (sv > 0)
			Fixture.of(() -> sut.check(p, sv)).noEx();
		else
			Fixture.of(() -> sut.check(p, sv)).iae();

		if (iv > 0)
			Fixture.of(() -> sut.check(p, iv)).noEx();
		else
			Fixture.of(() -> sut.check(p, iv)).iae();

		if (lv > 0)
			Fixture.of(() -> sut.check(p, lv)).noEx();
		else
			Fixture.of(() -> sut.check(p, lv)).iae();

		if (bObj > 0)
			Fixture.of(() -> sut.check(p, bObj)).noEx();
		else
			Fixture.of(() -> sut.check(p, bObj)).iae();

		if (sObj > 0)
			Fixture.of(() -> sut.check(p, sObj)).noEx();
		else
			Fixture.of(() -> sut.check(p, sObj)).iae();

		if (iObj > 0)
			Fixture.of(() -> sut.check(p, iObj)).noEx();
		else
			Fixture.of(() -> sut.check(p, iObj)).iae();

		if (lObj > 0)
			Fixture.of(() -> sut.check(p, lObj)).noEx();
		else
			Fixture.of(() -> sut.check(p, lObj)).iae();

		if (bi.compareTo(BigInteger.ZERO) > 0)
			Fixture.of(() -> sut.check(p, bi)).noEx();
		else
			Fixture.of(() -> sut.check(p, bi)).iae();

		if (bd.compareTo(BigDecimal.ZERO) > 0)
			Fixture.of(() -> sut.check(p, bd)).noEx();
		else
			Fixture.of(() -> sut.check(p, bd)).iae();

		// PositiveOrZero
		if (bv >= 0)
			Fixture.of(() -> sut.check(pz, bv)).noEx();
		else
			Fixture.of(() -> sut.check(pz, bv)).iae();

		if (sv >= 0)
			Fixture.of(() -> sut.check(pz, sv)).noEx();
		else
			Fixture.of(() -> sut.check(pz, sv)).iae();

		if (iv >= 0)
			Fixture.of(() -> sut.check(pz, iv)).noEx();
		else
			Fixture.of(() -> sut.check(pz, iv)).iae();

		if (lv >= 0)
			Fixture.of(() -> sut.check(pz, lv)).noEx();
		else
			Fixture.of(() -> sut.check(pz, lv)).iae();

		if (bObj >= 0)
			Fixture.of(() -> sut.check(pz, bObj)).noEx();
		else
			Fixture.of(() -> sut.check(pz, bObj)).iae();

		if (sObj >= 0)
			Fixture.of(() -> sut.check(pz, sObj)).noEx();
		else
			Fixture.of(() -> sut.check(pz, sObj)).iae();

		if (iObj >= 0)
			Fixture.of(() -> sut.check(pz, iObj)).noEx();
		else
			Fixture.of(() -> sut.check(pz, iObj)).iae();

		if (lObj >= 0)
			Fixture.of(() -> sut.check(pz, lObj)).noEx();
		else
			Fixture.of(() -> sut.check(pz, lObj)).iae();

		if (bi.compareTo(BigInteger.ZERO) >= 0)
			Fixture.of(() -> sut.check(pz, bi)).noEx();
		else
			Fixture.of(() -> sut.check(pz, bi)).iae();

		if (bd.compareTo(BigDecimal.ZERO) >= 0)
			Fixture.of(() -> sut.check(pz, bd)).noEx();
		else
			Fixture.of(() -> sut.check(pz, bd)).iae();

		// Negative
		if (bv < 0)
			Fixture.of(() -> sut.check(n, bv)).noEx();
		else
			Fixture.of(() -> sut.check(n, bv)).iae();

		if (sv < 0)
			Fixture.of(() -> sut.check(n, sv)).noEx();
		else
			Fixture.of(() -> sut.check(n, sv)).iae();

		if (iv < 0)
			Fixture.of(() -> sut.check(n, iv)).noEx();
		else
			Fixture.of(() -> sut.check(n, iv)).iae();

		if (lv < 0)
			Fixture.of(() -> sut.check(n, lv)).noEx();
		else
			Fixture.of(() -> sut.check(n, lv)).iae();

		if (bObj < 0)
			Fixture.of(() -> sut.check(n, bObj)).noEx();
		else
			Fixture.of(() -> sut.check(n, bObj)).iae();

		if (sObj < 0)
			Fixture.of(() -> sut.check(n, sObj)).noEx();
		else
			Fixture.of(() -> sut.check(n, sObj)).iae();

		if (iObj < 0)
			Fixture.of(() -> sut.check(n, iObj)).noEx();
		else
			Fixture.of(() -> sut.check(n, iObj)).iae();

		if (lObj < 0)
			Fixture.of(() -> sut.check(n, lObj)).noEx();
		else
			Fixture.of(() -> sut.check(n, lObj)).iae();

		if (bi.compareTo(BigInteger.ZERO) < 0)
			Fixture.of(() -> sut.check(n, bi)).noEx();
		else
			Fixture.of(() -> sut.check(n, bi)).iae();

		if (bd.compareTo(BigDecimal.ZERO) < 0)
			Fixture.of(() -> sut.check(n, bd)).noEx();
		else
			Fixture.of(() -> sut.check(n, bd)).iae();

		// NegativeOrZero
		if (bv <= 0)
			Fixture.of(() -> sut.check(nz, bv)).noEx();
		else
			Fixture.of(() -> sut.check(nz, bv)).iae();

		if (sv <= 0)
			Fixture.of(() -> sut.check(nz, sv)).noEx();
		else
			Fixture.of(() -> sut.check(nz, sv)).iae();

		if (iv <= 0)
			Fixture.of(() -> sut.check(nz, iv)).noEx();
		else
			Fixture.of(() -> sut.check(nz, iv)).iae();

		if (lv <= 0)
			Fixture.of(() -> sut.check(nz, lv)).noEx();
		else
			Fixture.of(() -> sut.check(nz, lv)).iae();

		if (bObj <= 0)
			Fixture.of(() -> sut.check(nz, bObj)).noEx();
		else
			Fixture.of(() -> sut.check(nz, bObj)).iae();

		if (sObj <= 0)
			Fixture.of(() -> sut.check(nz, sObj)).noEx();
		else
			Fixture.of(() -> sut.check(nz, sObj)).iae();

		if (iObj <= 0)
			Fixture.of(() -> sut.check(nz, iObj)).noEx();
		else
			Fixture.of(() -> sut.check(nz, iObj)).iae();

		if (lObj <= 0)
			Fixture.of(() -> sut.check(nz, lObj)).noEx();
		else
			Fixture.of(() -> sut.check(nz, lObj)).iae();

		if (bi.compareTo(BigInteger.ZERO) <= 0)
			Fixture.of(() -> sut.check(nz, bi)).noEx();
		else
			Fixture.of(() -> sut.check(nz, bi)).iae();

		if (bd.compareTo(BigDecimal.ZERO) <= 0)
			Fixture.of(() -> sut.check(nz, bd)).noEx();
		else
			Fixture.of(() -> sut.check(nz, bd)).iae();
	}

	// Temporal: Past / Future for LocalDate
	@Property
	void past_accepts_dates_before_today(@ForAll("pastDates") LocalDate d) {
		var a = anno(Past.class);
		Fixture.of(() -> sut.check(a, d)).noEx();
	}

	@Test
	void future_rejects_past_date() {
		var a = anno(jakarta.validation.constraints.Future.class);
		Fixture.of(() -> sut.check(a, LocalDate.now().minusDays(1))).iae();
	}

	@Property
	void future_dates_are_future_and_rejected_by_past(@ForAll("futureDates") LocalDate d) {
		var pastAnno = anno(Past.class);
		var futureAnno = anno(Future.class);

		// future dates should be rejected by @Past and accepted by @Future
		Fixture.of(() -> sut.check(pastAnno, d)).iae();
		Fixture.of(() -> sut.check(futureAnno, d)).noEx();
	}

	@Property
	void futureOrPresent_accepts_present_and_future(@ForAll("futureOrPresentDates") LocalDate d) {
		var futureOrPresent = anno(FutureOrPresent.class);
		var pastOrPresent = anno(PastOrPresent.class);
		LocalDate today = LocalDate.now();

		// FutureOrPresent should accept today and future dates
		Fixture.of(() -> sut.check(futureOrPresent, d)).noEx();

		// PastOrPresent should reject strictly future dates, but accept today
		Fixture fixture = Fixture.of(() -> sut.check(pastOrPresent, d));
		if (d.isAfter(today)) {
			fixture.iae();
		} else {
			fixture.noEx();
		}
	}

	@Property
	void pastOrPresent_accepts_present_and_past(@ForAll("pastOrPresentDates") LocalDate d) {
		var pastOrPresent = anno(PastOrPresent.class);
		var futureOrPresent = anno(FutureOrPresent.class);
		LocalDate today = LocalDate.now();

		// PastOrPresent should accept today and past dates
		Fixture.of(() -> sut.check(pastOrPresent, d)).noEx();

		// FutureOrPresent should reject strictly past dates, but accept today
		Fixture fixture = Fixture.of(() -> sut.check(futureOrPresent, d));
		if (d.isBefore(today)) {
			fixture.iae();
		} else {
			fixture.noEx();
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
		return sizeLists()
				.map(l -> range(0, l.size()).mapToObj(Integer::valueOf).collect(toMap(i -> "k" + i, identity())));
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
