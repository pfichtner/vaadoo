package com.github.pfichtner.vaadoo.fragments.impl;

import static java.lang.Math.abs;
import static java.lang.Math.log10;
import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static java.util.regex.Pattern.compile;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.IDN;
import java.util.Collection;
import java.util.Map;

import com.github.pfichtner.vaadoo.fragments.Jsr380CodeFragment;

import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Negative;
import jakarta.validation.constraints.NegativeOrZero;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Pattern.Flag;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public class JdkOnlyCodeFragment implements Jsr380CodeFragment {

	@Override
	public void check(Null anno, Object ref) {
		if (ref != null) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(NotNull anno, Object ref) {
		if (ref == null) {
			throw new NullPointerException(anno.message());
		}
	}

	@Override
	public void check(NotBlank anno, CharSequence charSequence) {
		if (charSequence == null) {
			throw new NullPointerException(anno.message());
		}
		if (charSequence.toString().trim().length() <= 0) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(jakarta.validation.constraints.Pattern anno, CharSequence charSequence) {
		if (charSequence != null) {
			int flagValue = 0;
			for (Flag flag : anno.flags()) {
				flagValue |= flag.getValue();
			}
			// TODO this should be optimized by converting this into private static final
			// field, beside to optimization it would be a fail fast for invalid regular
			// expression
			if (!compile(anno.regexp(), flagValue).matcher(charSequence).matches()) {
				throw new IllegalArgumentException(anno.message());
			}
		}
	}

	@Override
	public void check(Email anno, CharSequence charSequence) {
		if (charSequence != null && charSequence.length() != 0) {
			String stringValue = charSequence.toString();
			int splitPosition = stringValue.lastIndexOf('@');
			if (splitPosition < 0) {
				throw new IllegalArgumentException(anno.message());
			}

			String localPart = stringValue.substring(0, splitPosition);
			if (localPart.length() > 64 || !compile("(?:" + "[a-z0-9!#$%&'*+/=?^_`{|}~\u0080-\uFFFF-]" + "+|\""
					+ "(?:[a-z0-9!#$%&'*.(),<>\\[\\]:;  @+/=?^_`{|}~\u0080-\uFFFF-]|\\\\\\\\|\\\\\\\")" + "+\")"
					+ "(?:\\." + "(?:" + "[a-z0-9!#$%&'*+/=?^_`{|}~\u0080-\uFFFF-]" + "+|\""
					+ "(?:[a-z0-9!#$%&'*.(),<>\\[\\]:;  @+/=?^_`{|}~\u0080-\uFFFF-]|\\\\\\\\|\\\\\\\")" + "+\")" + ")*",
					CASE_INSENSITIVE).matcher(localPart).matches()) {
				throw new IllegalArgumentException(anno.message());
			}

			String domainPart = stringValue.substring(splitPosition + 1);
			boolean validEmailDomainAddress = false;
			try {
				validEmailDomainAddress = !domainPart.endsWith(".") && IDN.toASCII(domainPart).length() <= 255
						&& compile("(?:" + "[a-z\u0080-\uFFFF0-9!#$%&'*+/=?^_`{|}~]" + "-*)*"
								+ "[a-z\u0080-\uFFFF0-9!#$%&'*+/=?^_`{|}~]" + "+" + "+(?:\\." + "(?:"
								+ "[a-z\u0080-\uFFFF0-9!#$%&'*+/=?^_`{|}~]" + "-*)*"
								+ "[a-z\u0080-\uFFFF0-9!#$%&'*+/=?^_`{|}~]" + "+" + "+)*" + "|\\["
								+ "[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}" + "\\]|" + "\\[IPv6:"
								+ "(?:(?:[0-9a-fA-F]{1,4}:){7,7}[0-9a-fA-F]{1,4}|(?:[0-9a-fA-F]{1,4}:){1,7}:|(?:[0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|(?:[0-9a-fA-F]{1,4}:){1,5}(?::[0-9a-fA-F]{1,4}){1,2}|(?:[0-9a-fA-F]{1,4}:){1,4}(?::[0-9a-fA-F]{1,4}){1,3}|(?:[0-9a-fA-F]{1,4}:){1,3}(?::[0-9a-fA-F]{1,4}){1,4}|(?:[0-9a-fA-F]{1,4}:){1,2}(?::[0-9a-fA-F]{1,4}){1,5}|[0-9a-fA-F]{1,4}:(?:(?::[0-9a-fA-F]{1,4}){1,6})|:(?:(?::[0-9a-fA-F]{1,4}){1,7}|:)|fe80:(?::[0-9a-fA-F]{0,4}){0,4}%[0-9a-zA-Z]{1,}|::(?:ffff(:0{1,4}){0,1}:){0,1}(?:(?:25[0-5]|(?:2[0-4]|1{0,1}[0-9]){0,1}[0-9])\\.){3,3}(?:25[0-5]|(?:2[0-4]|1{0,1}[0-9]){0,1}[0-9])|(?:[0-9a-fA-F]{1,4}:){1,4}:(?:(?:25[0-5]|(?:2[0-4]|1{0,1}[0-9]){0,1}[0-9])\\.){3,3}(?:25[0-5]|(?:2[0-4]|1{0,1}[0-9]){0,1}[0-9]))"
								+ "\\]", CASE_INSENSITIVE).matcher(domainPart).matches();

			} catch (IllegalArgumentException e) {
			}
			if (!validEmailDomainAddress) {
				throw new IllegalArgumentException(anno.message());
			}

			// additional check
			int flagValue = 0;
			for (Flag flag : anno.flags()) {
				flagValue |= flag.getValue();
			}
			if (!compile(anno.regexp(), flagValue).matcher(charSequence).matches()) {
				throw new IllegalArgumentException(anno.message());
			}
		}
	}

	@Override
	public void check(NotEmpty anno, CharSequence charSequence) {
		if (charSequence == null) {
			throw new NullPointerException(anno.message());
		}
		if (charSequence.length() <= 0) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	public void check(NotEmpty anno, Collection<?> collection) {
		if (collection == null) {
			throw new NullPointerException(anno.message());
		}
		if (collection.size() <= 0) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(NotEmpty anno, Map<?, ?> map) {
		if (map == null) {
			throw new NullPointerException(anno.message());
		}
		if (map.size() <= 0) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(NotEmpty anno, Object[] objects) {
		if (objects == null) {
			throw new NullPointerException(anno.message());
		}
		if (objects.length <= 0) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	// -----------------------------------------------------------------

	@Override
	public void check(Size anno, CharSequence charSequence) {
		if (charSequence != null && (charSequence.length() < anno.min() || charSequence.length() > anno.max())) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	public void check(Size anno, Collection<?> collection) {
		if (collection != null && (collection.size() < anno.min() || collection.size() > anno.max())) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(Size anno, Map<?, ?> map) {
		if (map != null && (map.size() < anno.min() || map.size() > anno.max())) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(Size anno, Object[] objects) {
		if (objects != null && (objects.length < anno.min() || objects.length > anno.max())) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	// -----------------------------------------------------------------

	@Override
	public void check(AssertTrue anno, boolean value) {
		if (!value) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(AssertTrue anno, Boolean value) {
		if (value != null && !value) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(AssertFalse anno, boolean value) {
		if (value) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(AssertFalse anno, Boolean value) {
		if (value != null && value) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	// -----------------------------------------------------------------

	@Override
	public void check(Min anno, byte value) {
		if (value < anno.value()) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(Min anno, short value) {
		if (value < anno.value()) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(Min anno, int value) {
		if (value < anno.value()) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(Min anno, long value) {
		if (value < anno.value()) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(Min anno, Byte value) {
		if (value != null && value < anno.value()) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(Min anno, Short value) {
		if (value != null && value < anno.value()) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(Min anno, Integer value) {
		if (value != null && value < anno.value()) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(Min anno, Long value) {
		if (value != null && value < anno.value()) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(Min anno, BigInteger value) {
		if (value != null && value.compareTo(BigInteger.valueOf(anno.value())) < 0) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(Min anno, BigDecimal value) {
		if (value != null && value.compareTo(BigDecimal.valueOf(anno.value())) < 0) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	// -----------------------------------------------------------------

	@Override
	public void check(Max anno, byte value) {
		if (value > anno.value()) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(Max anno, short value) {
		if (value > anno.value()) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(Max anno, int value) {
		if (value > anno.value()) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(Max anno, long value) {
		if (value > anno.value()) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(Max anno, Byte value) {
		if (value != null && value > anno.value()) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(Max anno, Short value) {
		if (value != null && value > anno.value()) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(Max anno, Integer value) {
		if (value != null && value > anno.value()) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(Max anno, Long value) {
		if (value != null && value > anno.value()) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(Max anno, BigInteger value) {
		if (value != null && value.compareTo(BigInteger.valueOf(anno.value())) > 0) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(Max anno, BigDecimal value) {
		if (value != null && value.compareTo(BigDecimal.valueOf(anno.value())) > 0) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	// -----------------------------------------------------------------

	@Override
	public void check(DecimalMin anno, byte value) {
		if (new BigDecimal(value).compareTo(new BigDecimal(anno.value())) < (anno.inclusive() ? 0 : 1)) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(DecimalMin anno, short value) {
		if (new BigDecimal(value).compareTo(new BigDecimal(anno.value())) < (anno.inclusive() ? 0 : 1)) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(DecimalMin anno, int value) {
		if (new BigDecimal(value).compareTo(new BigDecimal(anno.value())) < (anno.inclusive() ? 0 : 1)) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(DecimalMin anno, long value) {
		if (new BigDecimal(value).compareTo(new BigDecimal(anno.value())) < (anno.inclusive() ? 0 : 1)) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(DecimalMin anno, Byte value) {
		if (value != null) {
			if (new BigDecimal(value).compareTo(new BigDecimal(anno.value())) < (anno.inclusive() ? 0 : 1)) {
				throw new IllegalArgumentException(anno.message());
			}
		}
	}

	@Override
	public void check(DecimalMin anno, Short value) {
		if (value != null
				&& new BigDecimal(value).compareTo(new BigDecimal(anno.value())) < (anno.inclusive() ? 0 : 1)) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(DecimalMin anno, Integer value) {
		if (value != null
				&& new BigDecimal(value).compareTo(new BigDecimal(anno.value())) < (anno.inclusive() ? 0 : 1)) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(DecimalMin anno, Long value) {
		if (value != null
				&& new BigDecimal(value).compareTo(new BigDecimal(anno.value())) < (anno.inclusive() ? 0 : 1)) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(DecimalMin anno, BigInteger value) {
		if (value != null
				&& new BigDecimal(value).compareTo(new BigDecimal(anno.value())) < (anno.inclusive() ? 0 : 1)) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(DecimalMin anno, BigDecimal value) {
		if (value != null && value.compareTo(new BigDecimal(anno.value())) < (anno.inclusive() ? 0 : 1)) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(DecimalMin anno, CharSequence value) {
		try {
			if (value != null && new BigDecimal(String.valueOf(value))
					.compareTo(new BigDecimal(anno.value())) < (anno.inclusive() ? 0 : 1)) {
				throw new IllegalArgumentException(anno.message());
			}
		} catch (NumberFormatException nfe) {
			// ignore
		}
	}

	// -----------------------------------------------------------------

	@Override
	public void check(DecimalMax anno, byte value) {
		if (new BigDecimal(value).compareTo(new BigDecimal(anno.value())) > (anno.inclusive() ? 0 : -1)) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(DecimalMax anno, short value) {
		if (new BigDecimal(value).compareTo(new BigDecimal(anno.value())) > (anno.inclusive() ? 0 : -1)) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(DecimalMax anno, int value) {
		if (new BigDecimal(value).compareTo(new BigDecimal(anno.value())) > (anno.inclusive() ? 0 : -1)) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(DecimalMax anno, long value) {
		if (new BigDecimal(value).compareTo(new BigDecimal(anno.value())) > (anno.inclusive() ? 0 : -1)) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(DecimalMax anno, Byte value) {
		if (value != null
				&& new BigDecimal(value).compareTo(new BigDecimal(anno.value())) > (anno.inclusive() ? 0 : -1)) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(DecimalMax anno, Short value) {
		if (value != null
				&& new BigDecimal(value).compareTo(new BigDecimal(anno.value())) > (anno.inclusive() ? 0 : -1)) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(DecimalMax anno, Integer value) {
		if (value != null
				&& new BigDecimal(value).compareTo(new BigDecimal(anno.value())) > (anno.inclusive() ? 0 : -1)) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(DecimalMax anno, Long value) {
		if (value != null
				&& new BigDecimal(value).compareTo(new BigDecimal(anno.value())) > (anno.inclusive() ? 0 : -1)) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(DecimalMax anno, BigInteger value) {
		if (value != null
				&& new BigDecimal(value).compareTo(new BigDecimal(anno.value())) > (anno.inclusive() ? 0 : -1)) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(DecimalMax anno, BigDecimal value) {
		if (value != null && value.compareTo(new BigDecimal(anno.value())) > (anno.inclusive() ? 0 : -1)) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(DecimalMax anno, CharSequence value) {
		try {
			if (value != null && new BigDecimal(String.valueOf(value))
					.compareTo(new BigDecimal(anno.value())) > (anno.inclusive() ? 0 : -1)) {
				throw new IllegalArgumentException(anno.message());
			}
		} catch (NumberFormatException nfe) {
			// ignore
		}
	}

	// -----------------------------------------------------------------

	@Override
	public void check(Digits anno, byte value) {
		int length = (value == 0) ? 1 : (int) log10(abs(value)) + 1;
		if (length > anno.integer()) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(Digits anno, short value) {
		int length = (value == 0) ? 1 : (int) log10(abs(value)) + 1;
		if (length > anno.integer()) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(Digits anno, int value) {
		int length = (value == 0) ? 1 : (int) log10(abs(value)) + 1;
		if (length > anno.integer()) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(Digits anno, long value) {
		long absValue = (value == Long.MIN_VALUE) ? -(value + 1) : abs(value);
		int length = (value == 0) ? 1 : (int) Math.log10(absValue) + 1;
		if (length > anno.integer()) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(Digits anno, Byte value) {
		if (value != null) {
			int length = (value == 0) ? 1 : (int) log10(abs(value)) + 1;
			if (length > anno.integer()) {
				throw new IllegalArgumentException(anno.message());
			}
		}
	}

	@Override
	public void check(Digits anno, Short value) {
		if (value != null) {
			int length = (value == 0) ? 1 : (int) log10(abs(value)) + 1;
			if (length > anno.integer()) {
				throw new IllegalArgumentException(anno.message());
			}
		}
	}

	@Override
	public void check(Digits anno, Integer value) {
		if (value != null) {
			int length = (value == 0) ? 1 : (int) log10(abs(value)) + 1;
			if (length > anno.integer()) {
				throw new IllegalArgumentException(anno.message());
			}
		}
	}

	@Override
	public void check(Digits anno, Long value) {
		if (value != null) {
			long absValue = (value == Long.MIN_VALUE) ? -(value + 1) : abs(value);
			int length = (value == 0) ? 1 : (int) Math.log10(absValue) + 1;
			if (length > anno.integer()) {
				throw new IllegalArgumentException(anno.message());
			}
		}
	}

	@Override
	public void check(Digits anno, BigInteger value) {
		if (value != null) {
			int length = value.toString().length();
			if (length > anno.integer()) {
				throw new IllegalArgumentException(anno.message());
			}
		}
	}

	@Override
	public void check(Digits anno, BigDecimal value) {
		if (value != null) {
			int integerPartLength = value.precision() - value.scale();
			int fractionPartLength = value.scale() < 0 ? 0 : value.scale();
			if (integerPartLength > anno.integer() || fractionPartLength > anno.fraction()) {
				throw new IllegalArgumentException(anno.message());
			}
		}
	}

	@Override
	public void check(Digits anno, CharSequence value) {
		if (value != null) {
			try {
				BigDecimal bigNum = new BigDecimal(value.toString());
				int integerPartLength = bigNum.precision() - bigNum.scale();
				int fractionPartLength = bigNum.scale() < 0 ? 0 : bigNum.scale();
				if (integerPartLength > anno.integer() || fractionPartLength > anno.fraction()) {
					throw new IllegalArgumentException(anno.message());
				}
			} catch (NumberFormatException e) {
				// ignore
			}
		}
	}

	// -----------------------------------------------------------------

	@Override
	public void check(Positive anno, byte value) {
		if (value <= 0) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(Positive anno, short value) {
		if (value <= 0) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(Positive anno, int value) {
		if (value <= 0) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(Positive anno, long value) {
		if (value <= 0) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(Positive anno, Byte value) {
		if (value != null && value <= 0) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(Positive anno, Short value) {
		if (value != null && value <= 0) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(Positive anno, Integer value) {
		if (value != null && value <= 0) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(Positive anno, Long value) {
		if (value != null && value <= 0) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(Positive anno, BigInteger value) {
		if (value != null && value.signum() <= 0) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(Positive anno, BigDecimal value) {
		if (value != null && value.signum() <= 0) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	// -----------------------------------------------------------------
	@Override
	public void check(PositiveOrZero anno, byte value) {
		if (value < 0) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(PositiveOrZero anno, short value) {
		if (value < 0) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(PositiveOrZero anno, int value) {
		if (value < 0) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(PositiveOrZero anno, long value) {
		if (value < 0) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(PositiveOrZero anno, Byte value) {
		if (value != null && value < 0) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(PositiveOrZero anno, Short value) {
		if (value != null && value < 0) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(PositiveOrZero anno, Integer value) {
		if (value != null && value < 0) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(PositiveOrZero anno, Long value) {
		if (value != null && value < 0) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(PositiveOrZero anno, BigInteger value) {
		if (value != null && value.signum() < 0) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(PositiveOrZero anno, BigDecimal value) {
		if (value != null && value.signum() < 0) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	// -----------------------------------------------------------------

	@Override
	public void check(Negative anno, byte value) {
		if (value >= 0) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(Negative anno, short value) {
		if (value >= 0) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(Negative anno, int value) {
		if (value >= 0) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(Negative anno, long value) {
		if (value >= 0) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(Negative anno, Byte value) {
		if (value != null && value >= 0) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(Negative anno, Short value) {
		if (value != null && value >= 0) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(Negative anno, Integer value) {
		if (value != null && value >= 0) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(Negative anno, Long value) {
		if (value != null && value >= 0) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(Negative anno, BigInteger value) {
		if (value != null && value.signum() >= 0) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(Negative anno, BigDecimal value) {
		if (value != null && value.signum() >= 0) {
			throw new IllegalArgumentException(anno.message());
		}
	}
	// -----------------------------------------------------------------

	@Override
	public void check(NegativeOrZero anno, byte value) {
		if (value > 0) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(NegativeOrZero anno, short value) {
		if (value > 0) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(NegativeOrZero anno, int value) {
		if (value > 0) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(NegativeOrZero anno, long value) {
		if (value > 0) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(NegativeOrZero anno, Byte value) {
		if (value != null && value > 0) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(NegativeOrZero anno, Short value) {
		if (value != null && value > 0) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(NegativeOrZero anno, Integer value) {
		if (value != null && value > 0) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(NegativeOrZero anno, Long value) {
		if (value != null && value > 0) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(NegativeOrZero anno, BigInteger value) {
		if (value != null && value.signum() > 0) {
			throw new IllegalArgumentException(anno.message());
		}
	}

	@Override
	public void check(NegativeOrZero anno, BigDecimal value) {
		if (value != null && value.signum() > 0) {
			throw new IllegalArgumentException(anno.message());
		}
	}

}
