package com.github.pfichtner.vaadoo.fragments.impl;

import static com.github.pfichtner.vaadoo.fragments.impl.Template.bitwiseOr;
import static java.lang.Math.abs;
import static java.lang.Math.log10;
import static java.lang.Math.max;
import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static java.util.regex.Pattern.compile;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.IDN;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.chrono.HijrahDate;
import java.time.chrono.JapaneseDate;
import java.time.chrono.MinguoDate;
import java.time.chrono.ThaiBuddhistDate;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

import com.github.pfichtner.vaadoo.fragments.Jsr380CodeFragment;

import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Negative;
import jakarta.validation.constraints.NegativeOrZero;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public class JdkOnlyCodeFragment implements Jsr380CodeFragment {

	@Override
	public void check(Null anno, Object ref, Object[] args) {
		if (ref != null) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(NotNull anno, Object ref, Object[] args) {
		if (ref == null) {
			throw new NullValueException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(NotBlank anno, CharSequence charSequence, Object[] args) {
		if (charSequence == null) {
			throw new NullValueException(String.format(anno.message(), args));
		}
		// since there is no #isEmpty on CharSequence and we handle CharSequence s in
		// #notEmpty we use "length() == 0" here too to be consistent, even if we could
		// use String#isEmpty here
		if (charSequence.toString().trim().length() == 0) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Pattern anno, CharSequence charSequence, Object[] args) {
		if (charSequence != null) {
			if (!compile(anno.regexp(), bitwiseOr(anno.flags())).matcher(charSequence).matches()) {
				throw new IllegalArgumentException(String.format(anno.message(), args));
			}
		}
	}

	@Override
	public void check(Email anno, CharSequence charSequence, Object[] args) {
		if (charSequence != null && charSequence.length() != 0) {
			String stringValue = charSequence.toString();
			int splitPosition = stringValue.lastIndexOf('@');
			if (splitPosition < 0) {
				throw new IllegalArgumentException(String.format(anno.message(), args));
			}

			String localPart = stringValue.substring(0, splitPosition);
			if (localPart.length() > 64 || !compile("(?:" + "[a-z0-9!#$%&'*+/=?^_`{|}~\u0080-\uFFFF-]" + "+|\""
					+ "(?:[a-z0-9!#$%&'*.(),<>\\[\\]:;  @+/=?^_`{|}~\u0080-\uFFFF-]|\\\\\\\\|\\\\\\\")" + "+\")"
					+ "(?:\\." + "(?:" + "[a-z0-9!#$%&'*+/=?^_`{|}~\u0080-\uFFFF-]" + "+|\""
					+ "(?:[a-z0-9!#$%&'*.(),<>\\[\\]:;  @+/=?^_`{|}~\u0080-\uFFFF-]|\\\\\\\\|\\\\\\\")" + "+\")" + ")*",
					CASE_INSENSITIVE).matcher(localPart).matches()) {
				throw new IllegalArgumentException(String.format(anno.message(), args));
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
				throw new IllegalArgumentException(String.format(anno.message(), args));
			}

			// additional check
			if (!compile(anno.regexp(), bitwiseOr(anno.flags())).matcher(charSequence).matches()) {
				throw new IllegalArgumentException(String.format(anno.message(), args));
			}
		}
	}

	@Override
	public void check(NotEmpty anno, CharSequence charSequence, Object[] args) {
		if (charSequence == null) {
			throw new NullValueException(String.format(anno.message(), args));
		}
		if (charSequence.length() == 0) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(NotEmpty anno, Collection<?> collection, Object[] args) {
		if (collection == null) {
			throw new NullValueException(String.format(anno.message(), args));
		}
		if (collection.size() == 0) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(NotEmpty anno, Map<?, ?> map, Object[] args) {
		if (map == null) {
			throw new NullValueException(String.format(anno.message(), args));
		}
		if (map.size() == 0) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(NotEmpty anno, Object[] objects, Object[] args) {
		if (objects == null) {
			throw new NullValueException(String.format(anno.message(), args));
		}
		if (objects.length == 0) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	// -----------------------------------------------------------------

	@Override
	public void check(Size anno, CharSequence charSequence, Object[] args) {
		if (charSequence != null && (charSequence.length() < anno.min() || charSequence.length() > anno.max())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	public void check(Size anno, Collection<?> collection, Object[] args) {
		if (collection != null && (collection.size() < anno.min() || collection.size() > anno.max())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Size anno, Map<?, ?> map, Object[] args) {
		if (map != null && (map.size() < anno.min() || map.size() > anno.max())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Size anno, Object[] objects, Object[] args) {
		if (objects != null && (objects.length < anno.min() || objects.length > anno.max())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	// -----------------------------------------------------------------

	@Override
	public void check(AssertTrue anno, boolean value, Object[] args) {
		if (!value) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(AssertTrue anno, Boolean value, Object[] args) {
		if (value != null && !value) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(AssertFalse anno, boolean value, Object[] args) {
		if (value) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(AssertFalse anno, Boolean value, Object[] args) {
		if (value != null && value) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	// -----------------------------------------------------------------

	@Override
	public void check(Min anno, byte value, Object[] args) {
		if (value < anno.value()) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Min anno, short value, Object[] args) {
		if (value < anno.value()) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Min anno, int value, Object[] args) {
		if (value < anno.value()) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Min anno, long value, Object[] args) {
		if (value < anno.value()) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Min anno, Byte value, Object[] args) {
		if (value != null && value < anno.value()) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Min anno, Short value, Object[] args) {
		if (value != null && value < anno.value()) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Min anno, Integer value, Object[] args) {
		if (value != null && value < anno.value()) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Min anno, Long value, Object[] args) {
		if (value != null && value < anno.value()) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Min anno, BigInteger value, Object[] args) {
		if (value != null && value.compareTo(BigInteger.valueOf(anno.value())) < 0) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Min anno, BigDecimal value, Object[] args) {
		if (value != null && value.compareTo(BigDecimal.valueOf(anno.value())) < 0) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	// -----------------------------------------------------------------

	@Override
	public void check(Max anno, byte value, Object[] args) {
		if (value > anno.value()) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Max anno, short value, Object[] args) {
		if (value > anno.value()) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Max anno, int value, Object[] args) {
		if (value > anno.value()) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Max anno, long value, Object[] args) {
		if (value > anno.value()) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Max anno, Byte value, Object[] args) {
		if (value != null && value > anno.value()) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Max anno, Short value, Object[] args) {
		if (value != null && value > anno.value()) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Max anno, Integer value, Object[] args) {
		if (value != null && value > anno.value()) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Max anno, Long value, Object[] args) {
		if (value != null && value > anno.value()) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Max anno, BigInteger value, Object[] args) {
		if (value != null && value.compareTo(BigInteger.valueOf(anno.value())) > 0) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Max anno, BigDecimal value, Object[] args) {
		if (value != null && value.compareTo(BigDecimal.valueOf(anno.value())) > 0) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	// -----------------------------------------------------------------

	@Override
	public void check(DecimalMin anno, byte value, Object[] args) {
		if (new BigDecimal(value).compareTo(new BigDecimal(anno.value())) < (anno.inclusive() ? 0 : 1)) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(DecimalMin anno, short value, Object[] args) {
		if (new BigDecimal(value).compareTo(new BigDecimal(anno.value())) < (anno.inclusive() ? 0 : 1)) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(DecimalMin anno, int value, Object[] args) {
		if (new BigDecimal(value).compareTo(new BigDecimal(anno.value())) < (anno.inclusive() ? 0 : 1)) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(DecimalMin anno, long value, Object[] args) {
		if (new BigDecimal(value).compareTo(new BigDecimal(anno.value())) < (anno.inclusive() ? 0 : 1)) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(DecimalMin anno, Byte value, Object[] args) {
		if (value != null) {
			if (new BigDecimal(value).compareTo(new BigDecimal(anno.value())) < (anno.inclusive() ? 0 : 1)) {
				throw new IllegalArgumentException(String.format(anno.message(), args));
			}
		}
	}

	@Override
	public void check(DecimalMin anno, Short value, Object[] args) {
		if (value != null
				&& new BigDecimal(value).compareTo(new BigDecimal(anno.value())) < (anno.inclusive() ? 0 : 1)) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(DecimalMin anno, Integer value, Object[] args) {
		if (value != null
				&& new BigDecimal(value).compareTo(new BigDecimal(anno.value())) < (anno.inclusive() ? 0 : 1)) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(DecimalMin anno, Long value, Object[] args) {
		if (value != null
				&& new BigDecimal(value).compareTo(new BigDecimal(anno.value())) < (anno.inclusive() ? 0 : 1)) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(DecimalMin anno, BigInteger value, Object[] args) {
		if (value != null
				&& new BigDecimal(value).compareTo(new BigDecimal(anno.value())) < (anno.inclusive() ? 0 : 1)) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(DecimalMin anno, BigDecimal value, Object[] args) {
		if (value != null && value.compareTo(new BigDecimal(anno.value())) < (anno.inclusive() ? 0 : 1)) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(DecimalMin anno, CharSequence value, Object[] args) {
		try {
			if (value != null && new BigDecimal(String.valueOf(value))
					.compareTo(new BigDecimal(anno.value())) < (anno.inclusive() ? 0 : 1)) {
				throw new IllegalArgumentException(String.format(anno.message(), args));
			}
		} catch (NumberFormatException nfe) {
			// ignore
		}
	}

	// -----------------------------------------------------------------

	@Override
	public void check(DecimalMax anno, byte value, Object[] args) {
		if (new BigDecimal(value).compareTo(new BigDecimal(anno.value())) > (anno.inclusive() ? 0 : -1)) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(DecimalMax anno, short value, Object[] args) {
		if (new BigDecimal(value).compareTo(new BigDecimal(anno.value())) > (anno.inclusive() ? 0 : -1)) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(DecimalMax anno, int value, Object[] args) {
		if (new BigDecimal(value).compareTo(new BigDecimal(anno.value())) > (anno.inclusive() ? 0 : -1)) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(DecimalMax anno, long value, Object[] args) {
		if (new BigDecimal(value).compareTo(new BigDecimal(anno.value())) > (anno.inclusive() ? 0 : -1)) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(DecimalMax anno, Byte value, Object[] args) {
		if (value != null
				&& new BigDecimal(value).compareTo(new BigDecimal(anno.value())) > (anno.inclusive() ? 0 : -1)) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(DecimalMax anno, Short value, Object[] args) {
		if (value != null
				&& new BigDecimal(value).compareTo(new BigDecimal(anno.value())) > (anno.inclusive() ? 0 : -1)) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(DecimalMax anno, Integer value, Object[] args) {
		if (value != null
				&& new BigDecimal(value).compareTo(new BigDecimal(anno.value())) > (anno.inclusive() ? 0 : -1)) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(DecimalMax anno, Long value, Object[] args) {
		if (value != null
				&& new BigDecimal(value).compareTo(new BigDecimal(anno.value())) > (anno.inclusive() ? 0 : -1)) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(DecimalMax anno, BigInteger value, Object[] args) {
		if (value != null
				&& new BigDecimal(value).compareTo(new BigDecimal(anno.value())) > (anno.inclusive() ? 0 : -1)) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(DecimalMax anno, BigDecimal value, Object[] args) {
		if (value != null && value.compareTo(new BigDecimal(anno.value())) > (anno.inclusive() ? 0 : -1)) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(DecimalMax anno, CharSequence value, Object[] args) {
		try {
			if (value != null && new BigDecimal(String.valueOf(value))
					.compareTo(new BigDecimal(anno.value())) > (anno.inclusive() ? 0 : -1)) {
				throw new IllegalArgumentException(String.format(anno.message(), args));
			}
		} catch (NumberFormatException nfe) {
			// ignore
		}
	}

	// -----------------------------------------------------------------

	@Override
	public void check(Digits anno, byte value, Object[] args) {
		int length = max(1, (int) log10(abs(value)) + 1);
		if (length > anno.integer()) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Digits anno, short value, Object[] args) {
		int length = max(1, (int) log10(abs(value)) + 1);
		if (length > anno.integer()) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Digits anno, int value, Object[] args) {
		int length = value == Integer.MIN_VALUE ? 10 : max(1, (int) log10(abs(value)) + 1);
		if (length > anno.integer()) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Digits anno, long value, Object[] args) {
		int length = value == Long.MIN_VALUE ? 19 : max(1, (int) log10(abs(value)) + 1);
		if (length > anno.integer()) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Digits anno, Byte value, Object[] args) {
		if (value != null) {
			int length = max(1, (int) log10(abs(value)) + 1);
			if (length > anno.integer()) {
				throw new IllegalArgumentException(String.format(anno.message(), args));
			}
		}
	}

	@Override
	public void check(Digits anno, Short value, Object[] args) {
		if (value != null) {
			int length = max(1, (int) log10(abs(value)) + 1);
			if (length > anno.integer()) {
				throw new IllegalArgumentException(String.format(anno.message(), args));
			}
		}
	}

	@Override
	public void check(Digits anno, Integer value, Object[] args) {
		if (value != null) {
			int length = value == Integer.MIN_VALUE ? 10 : max(1, (int) log10(abs(value)) + 1);
			if (length > anno.integer()) {
				throw new IllegalArgumentException(String.format(anno.message(), args));
			}
		}
	}

	@Override
	public void check(Digits anno, Long value, Object[] args) {
		if (value != null) {
			int length = value == Long.MIN_VALUE ? 19 : max(1, (int) log10(abs(value)) + 1);
			if (length > anno.integer()) {
				throw new IllegalArgumentException(String.format(anno.message(), args));
			}
		}
	}

	@Override
	public void check(Digits anno, BigInteger value, Object[] args) {
		if (value != null) {
			int length = value.abs().toString().length();
			if (length > anno.integer()) {
				throw new IllegalArgumentException(String.format(anno.message(), args));
			}
		}
	}

	@Override
	public void check(Digits anno, BigDecimal value, Object[] args) {
		if (value != null) {
			int integerPartLength = value.precision() - value.scale();
			int fractionPartLength = max(0, value.scale());
			if (integerPartLength > anno.integer() || fractionPartLength > anno.fraction()) {
				throw new IllegalArgumentException(String.format(anno.message(), args));
			}
		}
	}

	@Override
	public void check(Digits anno, CharSequence value, Object[] args) {
		if (value != null) {
			try {
				BigDecimal bigNum = new BigDecimal(value.toString());
				int integerPartLength = bigNum.precision() - bigNum.scale();
				int fractionPartLength = max(0, bigNum.scale());
				if (integerPartLength > anno.integer() || fractionPartLength > anno.fraction()) {
					throw new IllegalArgumentException(String.format(anno.message(), args));
				}
			} catch (NumberFormatException e) {
				// ignore
			}
		}
	}

	// -----------------------------------------------------------------

	@Override
	public void check(Positive anno, byte value, Object[] args) {
		if (value <= 0) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Positive anno, short value, Object[] args) {
		if (value <= 0) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Positive anno, int value, Object[] args) {
		if (value <= 0) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Positive anno, long value, Object[] args) {
		if (value <= 0) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Positive anno, Byte value, Object[] args) {
		if (value != null && value <= 0) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Positive anno, Short value, Object[] args) {
		if (value != null && value <= 0) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Positive anno, Integer value, Object[] args) {
		if (value != null && value <= 0) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Positive anno, Long value, Object[] args) {
		if (value != null && value <= 0) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Positive anno, BigInteger value, Object[] args) {
		if (value != null && value.signum() <= 0) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Positive anno, BigDecimal value, Object[] args) {
		if (value != null && value.signum() <= 0) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	// -----------------------------------------------------------------
	@Override
	public void check(PositiveOrZero anno, byte value, Object[] args) {
		if (value < 0) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(PositiveOrZero anno, short value, Object[] args) {
		if (value < 0) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(PositiveOrZero anno, int value, Object[] args) {
		if (value < 0) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(PositiveOrZero anno, long value, Object[] args) {
		if (value < 0) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(PositiveOrZero anno, Byte value, Object[] args) {
		if (value != null && value < 0) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(PositiveOrZero anno, Short value, Object[] args) {
		if (value != null && value < 0) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(PositiveOrZero anno, Integer value, Object[] args) {
		if (value != null && value < 0) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(PositiveOrZero anno, Long value, Object[] args) {
		if (value != null && value < 0) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(PositiveOrZero anno, BigInteger value, Object[] args) {
		if (value != null && value.signum() < 0) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(PositiveOrZero anno, BigDecimal value, Object[] args) {
		if (value != null && value.signum() < 0) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	// -----------------------------------------------------------------

	@Override
	public void check(Negative anno, byte value, Object[] args) {
		if (value >= 0) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Negative anno, short value, Object[] args) {
		if (value >= 0) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Negative anno, int value, Object[] args) {
		if (value >= 0) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Negative anno, long value, Object[] args) {
		if (value >= 0) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Negative anno, Byte value, Object[] args) {
		if (value != null && value >= 0) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Negative anno, Short value, Object[] args) {
		if (value != null && value >= 0) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Negative anno, Integer value, Object[] args) {
		if (value != null && value >= 0) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Negative anno, Long value, Object[] args) {
		if (value != null && value >= 0) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Negative anno, BigInteger value, Object[] args) {
		if (value != null && value.signum() >= 0) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Negative anno, BigDecimal value, Object[] args) {
		if (value != null && value.signum() >= 0) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	// -----------------------------------------------------------------

	@Override
	public void check(NegativeOrZero anno, byte value, Object[] args) {
		if (value > 0) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(NegativeOrZero anno, short value, Object[] args) {
		if (value > 0) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(NegativeOrZero anno, int value, Object[] args) {
		if (value > 0) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(NegativeOrZero anno, long value, Object[] args) {
		if (value > 0) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(NegativeOrZero anno, Byte value, Object[] args) {
		if (value != null && value > 0) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(NegativeOrZero anno, Short value, Object[] args) {
		if (value != null && value > 0) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(NegativeOrZero anno, Integer value, Object[] args) {
		if (value != null && value > 0) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(NegativeOrZero anno, Long value, Object[] args) {
		if (value != null && value > 0) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(NegativeOrZero anno, BigInteger value, Object[] args) {
		if (value != null && value.signum() > 0) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(NegativeOrZero anno, BigDecimal value, Object[] args) {
		if (value != null && value.signum() > 0) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	// -----------------------------------------------------------------

	@Override
	public void check(Past anno, Date value, Object[] args) {
		if (value != null && value.getTime() >= System.currentTimeMillis()) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Past anno, Calendar value, Object[] args) {
		if (value != null && value.getTimeInMillis() >= System.currentTimeMillis()) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Past anno, Instant value, Object[] args) {
		if (value != null && !value.isBefore(Instant.now())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Past anno, LocalDate value, Object[] args) {
		if (value != null && !value.isBefore(LocalDate.now())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Past anno, LocalDateTime value, Object[] args) {
		if (value != null && !value.isBefore(LocalDateTime.now())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Past anno, LocalTime value, Object[] args) {
		if (value != null && !value.isBefore(LocalTime.now())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Past anno, MonthDay value, Object[] args) {
		if (value != null && !value.isBefore(MonthDay.now())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Past anno, OffsetDateTime value, Object[] args) {
		if (value != null && !value.isBefore(OffsetDateTime.now())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Past anno, Year value, Object[] args) {
		if (value != null && !value.isBefore(Year.now())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Past anno, YearMonth value, Object[] args) {
		if (value != null && !value.isBefore(YearMonth.now())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Past anno, ZonedDateTime value, Object[] args) {
		if (value != null && !value.isBefore(ZonedDateTime.now())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Past anno, HijrahDate value, Object[] args) {
		if (value != null && !value.isBefore(HijrahDate.now())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Past anno, JapaneseDate value, Object[] args) {
		if (value != null && !value.isBefore(JapaneseDate.now())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Past anno, MinguoDate value, Object[] args) {
		if (value != null && !value.isBefore(MinguoDate.now())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Past anno, ThaiBuddhistDate value, Object[] args) {
		if (value != null && !value.isBefore(ThaiBuddhistDate.now())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	// -----------------------------------------------------------------

	@Override
	public void check(PastOrPresent anno, Date value, Object[] args) {
		if (value != null && value.getTime() > System.currentTimeMillis()) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(PastOrPresent anno, Calendar value, Object[] args) {
		if (value != null && value.getTimeInMillis() > System.currentTimeMillis()) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(PastOrPresent anno, Instant value, Object[] args) {
		if (value != null && value.isAfter(Instant.now())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(PastOrPresent anno, LocalDate value, Object[] args) {
		if (value != null && value.isAfter(LocalDate.now())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(PastOrPresent anno, LocalDateTime value, Object[] args) {
		if (value != null && value.isAfter(LocalDateTime.now())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(PastOrPresent anno, LocalTime value, Object[] args) {
		if (value != null && value.isAfter(LocalTime.now())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(PastOrPresent anno, MonthDay value, Object[] args) {
		if (value != null && value.isAfter(MonthDay.now())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(PastOrPresent anno, OffsetDateTime value, Object[] args) {
		if (value != null && value.isAfter(OffsetDateTime.now())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(PastOrPresent anno, Year value, Object[] args) {
		if (value != null && value.isAfter(Year.now())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(PastOrPresent anno, YearMonth value, Object[] args) {
		if (value != null && value.isAfter(YearMonth.now())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(PastOrPresent anno, ZonedDateTime value, Object[] args) {
		if (value != null && value.isAfter(ZonedDateTime.now())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(PastOrPresent anno, HijrahDate value, Object[] args) {
		if (value != null && value.isAfter(HijrahDate.now())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(PastOrPresent anno, JapaneseDate value, Object[] args) {
		if (value != null && value.isAfter(JapaneseDate.now())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(PastOrPresent anno, MinguoDate value, Object[] args) {
		if (value != null && value.isAfter(MinguoDate.now())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(PastOrPresent anno, ThaiBuddhistDate value, Object[] args) {
		if (value != null && value.isAfter(ThaiBuddhistDate.now())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	// -----------------------------------------------------------------

	@Override
	public void check(Future anno, Date value, Object[] args) {
		if (value != null && value.getTime() <= System.currentTimeMillis()) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Future anno, Calendar value, Object[] args) {
		if (value != null && value.getTimeInMillis() <= System.currentTimeMillis()) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Future anno, Instant value, Object[] args) {
		if (value != null && !value.isAfter(Instant.now())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Future anno, LocalDate value, Object[] args) {
		if (value != null && !value.isAfter(LocalDate.now())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Future anno, LocalDateTime value, Object[] args) {
		if (value != null && !value.isAfter(LocalDateTime.now())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Future anno, LocalTime value, Object[] args) {
		if (value != null && !value.isAfter(LocalTime.now())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Future anno, MonthDay value, Object[] args) {
		if (value != null && !value.isAfter(MonthDay.now())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Future anno, OffsetDateTime value, Object[] args) {
		if (value != null && !value.isAfter(OffsetDateTime.now())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Future anno, Year value, Object[] args) {
		if (value != null && !value.isAfter(Year.now())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Future anno, YearMonth value, Object[] args) {
		if (value != null && !value.isAfter(YearMonth.now())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Future anno, ZonedDateTime value, Object[] args) {
		if (value != null && !value.isAfter(ZonedDateTime.now())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Future anno, HijrahDate value, Object[] args) {
		if (value != null && !value.isAfter(HijrahDate.now())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Future anno, JapaneseDate value, Object[] args) {
		if (value != null && !value.isAfter(JapaneseDate.now())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Future anno, MinguoDate value, Object[] args) {
		if (value != null && !value.isAfter(MinguoDate.now())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(Future anno, ThaiBuddhistDate value, Object[] args) {
		if (value != null && !value.isAfter(ThaiBuddhistDate.now())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	// -----------------------------------------------------------------

	@Override
	public void check(FutureOrPresent anno, Date value, Object[] args) {
		if (value != null && value.getTime() < System.currentTimeMillis()) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(FutureOrPresent anno, Calendar value, Object[] args) {
		if (value != null && value.getTimeInMillis() < System.currentTimeMillis()) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(FutureOrPresent anno, Instant value, Object[] args) {
		if (value != null && value.isBefore(Instant.now())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(FutureOrPresent anno, LocalDate value, Object[] args) {
		if (value != null && value.isBefore(LocalDate.now())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(FutureOrPresent anno, LocalDateTime value, Object[] args) {
		if (value != null && value.isBefore(LocalDateTime.now())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(FutureOrPresent anno, LocalTime value, Object[] args) {
		if (value != null && value.isBefore(LocalTime.now())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(FutureOrPresent anno, MonthDay value, Object[] args) {
		if (value != null && value.isBefore(MonthDay.now())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(FutureOrPresent anno, OffsetDateTime value, Object[] args) {
		if (value != null && value.isBefore(OffsetDateTime.now())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(FutureOrPresent anno, Year value, Object[] args) {
		if (value != null && value.isBefore(Year.now())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(FutureOrPresent anno, YearMonth value, Object[] args) {
		if (value != null && value.isBefore(YearMonth.now())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(FutureOrPresent anno, ZonedDateTime value, Object[] args) {
		if (value != null && value.isBefore(ZonedDateTime.now())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(FutureOrPresent anno, HijrahDate value, Object[] args) {
		if (value != null && value.isBefore(HijrahDate.now())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(FutureOrPresent anno, JapaneseDate value, Object[] args) {
		if (value != null && value.isBefore(JapaneseDate.now())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(FutureOrPresent anno, MinguoDate value, Object[] args) {
		if (value != null && value.isBefore(MinguoDate.now())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

	@Override
	public void check(FutureOrPresent anno, ThaiBuddhistDate value, Object[] args) {
		if (value != null && value.isBefore(ThaiBuddhistDate.now())) {
			throw new IllegalArgumentException(String.format(anno.message(), args));
		}
	}

}
