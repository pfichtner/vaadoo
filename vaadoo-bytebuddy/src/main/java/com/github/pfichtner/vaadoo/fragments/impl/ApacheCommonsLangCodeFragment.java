package com.github.pfichtner.vaadoo.fragments.impl;

import static com.github.pfichtner.vaadoo.fragments.impl.Template.bitwiseOr;
import static java.lang.Math.abs;
import static java.lang.Math.log10;
import static java.lang.Math.max;
import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static java.util.regex.Pattern.compile;
import static org.apache.commons.lang3.Validate.inclusiveBetween;
import static org.apache.commons.lang3.Validate.isTrue;
import static org.apache.commons.lang3.Validate.notBlank;
import static org.apache.commons.lang3.Validate.notEmpty;
import static org.apache.commons.lang3.Validate.notNull;

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

public class ApacheCommonsLangCodeFragment implements Jsr380CodeFragment {

	@Override
	public void check(Null anno, Object ref) {
		isTrue(ref == null, anno.message());
	}

	@Override
	public void check(NotNull anno, Object ref) {
		notNull(ref, anno.message());
	}

	@Override
	public void check(NotBlank anno, CharSequence charSequence) {
		notBlank(charSequence, anno.message());
	}

	@Override
	public void check(Pattern anno, CharSequence charSequence) {
		if (charSequence != null) {
			isTrue(compile(anno.regexp(), bitwiseOr(anno.flags())).matcher(charSequence).matches(), anno.message());
		}
	}

	@Override
	public void check(Email anno, CharSequence charSequence) {
		if (charSequence != null && charSequence.length() != 0) {
			String stringValue = charSequence.toString();
			int splitPosition = stringValue.lastIndexOf('@');
			isTrue(splitPosition >= 0, anno.message());

			String localPart = stringValue.substring(0, splitPosition);
			isTrue(localPart.length() <= 64 && compile("(?:" + "[a-z0-9!#$%&'*+/=?^_`{|}~\u0080-\uFFFF-]" + "+|\""
					+ "(?:[a-z0-9!#$%&'*.(),<>\\[\\]:;  @+/=?^_`{|}~\u0080-\uFFFF-]|\\\\\\\\|\\\\\\\")" + "+\")"
					+ "(?:\\." + "(?:" + "[a-z0-9!#$%&'*+/=?^_`{|}~\u0080-\uFFFF-]" + "+|\""
					+ "(?:[a-z0-9!#$%&'*.(),<>\\[\\]:;  @+/=?^_`{|}~\u0080-\uFFFF-]|\\\\\\\\|\\\\\\\")" + "+\")" + ")*",
					CASE_INSENSITIVE).matcher(localPart).matches(), anno.message());

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
			isTrue(validEmailDomainAddress, anno.message());

			// additional check
			isTrue(compile(anno.regexp(), bitwiseOr(anno.flags())).matcher(charSequence).matches(), anno.message());
		}
	}

	// -----------------------------------------------------------------

	@Override
	public void check(NotEmpty anno, CharSequence charSequence) {
		notEmpty(charSequence, anno.message());
	}

	public void check(NotEmpty anno, Collection<?> collection) {
		notEmpty(collection, anno.message());
	}

	@Override
	public void check(NotEmpty anno, Map<?, ?> map) {
		notEmpty(map, anno.message());
	}

	@Override
	public void check(NotEmpty anno, Object[] objects) {
		notEmpty(objects, anno.message());
	}

	// -----------------------------------------------------------------

	@Override
	public void check(Size anno, CharSequence charSequence) {
		int length = notNull(charSequence, anno.message()).length();
		inclusiveBetween(anno.min(), anno.max(), length, anno.message());
	}

	public void check(Size anno, Collection<?> collection) {
		int length = notNull(collection, anno.message()).size();
		inclusiveBetween(anno.min(), anno.max(), length, anno.message());
	}

	@Override
	public void check(Size anno, Map<?, ?> map) {
		int length = notNull(map, anno.message()).size();
		inclusiveBetween(anno.min(), anno.max(), length, anno.message());
	}

	@Override
	public void check(Size anno, Object[] objects) {
		int length = notNull(objects, anno.message()).length;
		inclusiveBetween(anno.min(), anno.max(), length, anno.message());
	}

	// -----------------------------------------------------------------

	@Override
	public void check(AssertTrue anno, boolean value) {
		isTrue(value, anno.message());
	}

	@Override
	public void check(AssertTrue anno, Boolean value) {
		isTrue(value == null || value, anno.message());
	}

	@Override
	public void check(AssertFalse anno, boolean value) {
		isTrue(!value, anno.message());
	}

	@Override
	public void check(AssertFalse anno, Boolean value) {
		isTrue(value == null || !value, anno.message());
	}

	// -----------------------------------------------------------------

	@Override
	public void check(Min anno, byte value) {
		isTrue(value >= anno.value(), anno.message());
	}

	@Override
	public void check(Min anno, short value) {
		isTrue(value >= anno.value(), anno.message());
	}

	@Override
	public void check(Min anno, int value) {
		isTrue(value >= anno.value(), anno.message());
	}

	@Override
	public void check(Min anno, long value) {
		isTrue(value >= anno.value(), anno.message());
	}

	@Override
	public void check(Min anno, Byte value) {
		isTrue(value == null || value >= anno.value(), anno.message());
	}

	@Override
	public void check(Min anno, Short value) {
		isTrue(value == null || value >= anno.value(), anno.message());
	}

	@Override
	public void check(Min anno, Integer value) {
		isTrue(value == null || value >= anno.value(), anno.message());
	}

	@Override
	public void check(Min anno, Long value) {
		isTrue(value == null || value >= anno.value(), anno.message());
	}

	@Override
	public void check(Min anno, BigInteger value) {
		isTrue(value == null || value.compareTo(BigInteger.valueOf(anno.value())) >= 0, anno.message());
	}

	@Override
	public void check(Min anno, BigDecimal value) {
		isTrue(value == null || value.compareTo(BigDecimal.valueOf(anno.value())) >= 0, anno.message());
	}

	// -----------------------------------------------------------------

	@Override
	public void check(Max anno, byte value) {
		isTrue(value <= anno.value(), anno.message());
	}

	@Override
	public void check(Max anno, short value) {
		isTrue(value <= anno.value(), anno.message());
	}

	@Override
	public void check(Max anno, int value) {
		isTrue(value <= anno.value(), anno.message());
	}

	@Override
	public void check(Max anno, long value) {
		isTrue(value <= anno.value(), anno.message());
	}

	@Override
	public void check(Max anno, Byte value) {
		isTrue(value == null || value <= anno.value(), anno.message());
	}

	@Override
	public void check(Max anno, Short value) {
		isTrue(value == null || value <= anno.value(), anno.message());
	}

	@Override
	public void check(Max anno, Integer value) {
		isTrue(value == null || value <= anno.value(), anno.message());
	}

	@Override
	public void check(Max anno, Long value) {
		isTrue(value == null || value <= anno.value(), anno.message());
	}

	@Override
	public void check(Max anno, BigInteger value) {
		isTrue(value == null || value.compareTo(BigInteger.valueOf(anno.value())) <= 0, anno.message());
	}

	@Override
	public void check(Max anno, BigDecimal value) {
		isTrue(value == null || value.compareTo(BigDecimal.valueOf(anno.value())) <= 0, anno.message());
	}

	// -----------------------------------------------------------------

	@Override
	public void check(DecimalMin anno, byte value) {
		isTrue(new BigDecimal(value).compareTo(new BigDecimal(anno.value())) >= (anno.inclusive() ? 0 : 1),
				anno.message());
	}

	@Override
	public void check(DecimalMin anno, short value) {
		isTrue(new BigDecimal(value).compareTo(new BigDecimal(anno.value())) >= (anno.inclusive() ? 0 : 1),
				anno.message());
	}

	@Override
	public void check(DecimalMin anno, int value) {
		isTrue(new BigDecimal(value).compareTo(new BigDecimal(anno.value())) >= (anno.inclusive() ? 0 : 1),
				anno.message());
	}

	@Override
	public void check(DecimalMin anno, long value) {
		isTrue(new BigDecimal(value).compareTo(new BigDecimal(anno.value())) >= (anno.inclusive() ? 0 : 1),
				anno.message());
	}

	@Override
	public void check(DecimalMin anno, Byte value) {
		isTrue(value == null
				|| new BigDecimal(value).compareTo(new BigDecimal(anno.value())) >= (anno.inclusive() ? 0 : 1),
				anno.message());
	}

	@Override
	public void check(DecimalMin anno, Short value) {
		isTrue(value == null
				|| new BigDecimal(value).compareTo(new BigDecimal(anno.value())) >= (anno.inclusive() ? 0 : 1),
				anno.message());
	}

	@Override
	public void check(DecimalMin anno, Integer value) {
		isTrue(value == null
				|| new BigDecimal(value).compareTo(new BigDecimal(anno.value())) >= (anno.inclusive() ? 0 : 1),
				anno.message());
	}

	@Override
	public void check(DecimalMin anno, Long value) {
		isTrue(value == null
				|| new BigDecimal(value).compareTo(new BigDecimal(anno.value())) >= (anno.inclusive() ? 0 : 1),
				anno.message());
	}

	@Override
	public void check(DecimalMin anno, BigInteger value) {
		isTrue(value == null
				|| new BigDecimal(value).compareTo(new BigDecimal(anno.value())) >= (anno.inclusive() ? 0 : 1),
				anno.message());
	}

	@Override
	public void check(DecimalMin anno, BigDecimal value) {
		isTrue(value == null || value.compareTo(new BigDecimal(anno.value())) >= (anno.inclusive() ? 0 : 1),
				anno.message());
	}

	@Override
	public void check(DecimalMin anno, CharSequence value) {
		try {
			isTrue(value == null || new BigDecimal(String.valueOf(value))
					.compareTo(new BigDecimal(anno.value())) >= (anno.inclusive() ? 0 : 1), anno.message());
		} catch (NumberFormatException nfe) {
			// ignore
		}
	}

	// -----------------------------------------------------------------

	@Override
	public void check(DecimalMax anno, byte value) {
		isTrue(new BigDecimal(value).compareTo(new BigDecimal(anno.value())) <= (anno.inclusive() ? 0 : -1),
				anno.message());
	}

	@Override
	public void check(DecimalMax anno, short value) {
		isTrue(new BigDecimal(value).compareTo(new BigDecimal(anno.value())) <= (anno.inclusive() ? 0 : -1),
				anno.message());
	}

	@Override
	public void check(DecimalMax anno, int value) {
		isTrue(new BigDecimal(value).compareTo(new BigDecimal(anno.value())) <= (anno.inclusive() ? 0 : -1),
				anno.message());
	}

	@Override
	public void check(DecimalMax anno, long value) {
		isTrue(new BigDecimal(value).compareTo(new BigDecimal(anno.value())) <= (anno.inclusive() ? 0 : -1),
				anno.message());
	}

	@Override
	public void check(DecimalMax anno, Byte value) {
		isTrue(value == null
				|| new BigDecimal(value).compareTo(new BigDecimal(anno.value())) <= (anno.inclusive() ? 0 : -1),
				anno.message());
	}

	@Override
	public void check(DecimalMax anno, Short value) {
		isTrue(value == null
				|| new BigDecimal(value).compareTo(new BigDecimal(anno.value())) <= (anno.inclusive() ? 0 : -1),
				anno.message());
	}

	@Override
	public void check(DecimalMax anno, Integer value) {
		isTrue(value == null
				|| new BigDecimal(value).compareTo(new BigDecimal(anno.value())) <= (anno.inclusive() ? 0 : -1),
				anno.message());
	}

	@Override
	public void check(DecimalMax anno, Long value) {
		isTrue(value == null
				|| new BigDecimal(value).compareTo(new BigDecimal(anno.value())) <= (anno.inclusive() ? 0 : -1),
				anno.message());
	}

	@Override
	public void check(DecimalMax anno, BigInteger value) {
		isTrue(value == null
				|| new BigDecimal(value).compareTo(new BigDecimal(anno.value())) <= (anno.inclusive() ? 0 : -1),
				anno.message());
	}

	@Override
	public void check(DecimalMax anno, BigDecimal value) {
		isTrue(value == null || value.compareTo(new BigDecimal(anno.value())) <= (anno.inclusive() ? 0 : -1),
				anno.message());
	}

	@Override
	public void check(DecimalMax anno, CharSequence value) {
		try {
			isTrue(value == null || new BigDecimal(String.valueOf(value))
					.compareTo(new BigDecimal(anno.value())) <= (anno.inclusive() ? 0 : -1), anno.message());
		} catch (NumberFormatException nfe) {
			// ignore
		}
	}

	// -----------------------------------------------------------------

	@Override
	public void check(Digits anno, byte value) {
		int length = max(1, (int) log10(abs(value)) + 1);
		isTrue(length <= anno.integer(), anno.message());
	}

	@Override
	public void check(Digits anno, short value) {
		int length = max(1, (int) log10(abs(value)) + 1);
		isTrue(length <= anno.integer(), anno.message());
	}

	@Override
	public void check(Digits anno, int value) {
		int length = value == Integer.MIN_VALUE ? 10 : max(1, (int) log10(abs(value)) + 1);
		isTrue(length <= anno.integer(), anno.message());
	}

	@Override
	public void check(Digits anno, long value) {
		int length = value == Long.MIN_VALUE ? 19 : max(1, (int) log10(abs(value)) + 1);
		isTrue(length <= anno.integer(), anno.message());
	}

	@Override
	public void check(Digits anno, Byte value) {
		if (value != null) {
			int length = max(1, (int) log10(abs(value)) + 1);
			isTrue(length <= anno.integer(), anno.message());
		}
	}

	@Override
	public void check(Digits anno, Short value) {
		if (value != null) {
			int length = max(1, (int) log10(abs(value)) + 1);
			isTrue(length <= anno.integer(), anno.message());
		}
	}

	@Override
	public void check(Digits anno, Integer value) {
		if (value != null) {
			int length = value == Integer.MIN_VALUE ? 10 : max(1, (int) log10(abs(value)) + 1);
			isTrue(length <= anno.integer(), anno.message());
		}
	}

	@Override
	public void check(Digits anno, Long value) {
		if (value != null) {
			int length = value == Long.MIN_VALUE ? 19 : max(1, (int) log10(abs(value)) + 1);
			isTrue(length <= anno.integer(), anno.message());
		}
	}

	@Override
	public void check(Digits anno, BigInteger value) {
		if (value != null) {
			int length = value.abs().toString().length();
			isTrue(length <= anno.integer(), anno.message());
		}
	}

	@Override
	public void check(Digits anno, BigDecimal value) {
		if (value != null) {
			int integerPartLength = value.precision() - value.scale();
			int fractionPartLength = value.scale() < 0 ? 0 : value.scale();
			isTrue(integerPartLength <= anno.integer() && fractionPartLength <= anno.fraction(), anno.message());
		}
	}

	@Override
	public void check(Digits anno, CharSequence value) {
		if (value != null) {
			try {
				BigDecimal bigNum = new BigDecimal(value.toString());
				int integerPartLength = bigNum.precision() - bigNum.scale();
				int fractionPartLength = bigNum.scale() < 0 ? 0 : bigNum.scale();
				isTrue(integerPartLength <= anno.integer() && fractionPartLength <= anno.fraction(), anno.message());
			} catch (NumberFormatException e) {
				// ignore
			}
		}
	}

	// -----------------------------------------------------------------

	@Override
	public void check(Positive anno, byte value) {
		isTrue(value > 0, anno.message());
	}

	@Override
	public void check(Positive anno, short value) {
		isTrue(value > 0, anno.message());
	}

	@Override
	public void check(Positive anno, int value) {
		isTrue(value > 0, anno.message());
	}

	@Override
	public void check(Positive anno, long value) {
		isTrue(value > 0, anno.message());
	}

	@Override
	public void check(Positive anno, Byte value) {
		isTrue(value == null || value > 0, anno.message());
	}

	@Override
	public void check(Positive anno, Short value) {
		isTrue(value == null || value > 0, anno.message());
	}

	@Override
	public void check(Positive anno, Integer value) {
		isTrue(value == null || value > 0, anno.message());
	}

	@Override
	public void check(Positive anno, Long value) {
		isTrue(value == null || value > 0, anno.message());
	}

	@Override
	public void check(Positive anno, BigInteger value) {
		isTrue(value == null || value.signum() > 0, anno.message());
	}

	@Override
	public void check(Positive anno, BigDecimal value) {
		isTrue(value == null || value.signum() > 0, anno.message());
	}

	// -----------------------------------------------------------------
	@Override
	public void check(PositiveOrZero anno, byte value) {
		isTrue(value >= 0, anno.message());
	}

	@Override
	public void check(PositiveOrZero anno, short value) {
		isTrue(value >= 0, anno.message());
	}

	@Override
	public void check(PositiveOrZero anno, int value) {
		isTrue(value >= 0, anno.message());
	}

	@Override
	public void check(PositiveOrZero anno, long value) {
		isTrue(value >= 0, anno.message());
	}

	@Override
	public void check(PositiveOrZero anno, Byte value) {
		isTrue(value == null || value >= 0, anno.message());
	}

	@Override
	public void check(PositiveOrZero anno, Short value) {
		isTrue(value == null || value >= 0, anno.message());
	}

	@Override
	public void check(PositiveOrZero anno, Integer value) {
		isTrue(value == null || value >= 0, anno.message());
	}

	@Override
	public void check(PositiveOrZero anno, Long value) {
		isTrue(value == null || value >= 0, anno.message());
	}

	@Override
	public void check(PositiveOrZero anno, BigInteger value) {
		isTrue(value == null || value.signum() >= 0, anno.message());
	}

	@Override
	public void check(PositiveOrZero anno, BigDecimal value) {
		isTrue(value == null || value.signum() >= 0, anno.message());
	}

	// -----------------------------------------------------------------

	@Override
	public void check(Negative anno, byte value) {
		isTrue(value < 0, anno.message());
	}

	@Override
	public void check(Negative anno, short value) {
		isTrue(value < 0, anno.message());
	}

	@Override
	public void check(Negative anno, int value) {
		isTrue(value < 0, anno.message());
	}

	@Override
	public void check(Negative anno, long value) {
		isTrue(value < 0, anno.message());
	}

	@Override
	public void check(Negative anno, Byte value) {
		isTrue(value == null || value < 0, anno.message());
	}

	@Override
	public void check(Negative anno, Short value) {
		isTrue(value == null || value < 0, anno.message());
	}

	@Override
	public void check(Negative anno, Integer value) {
		isTrue(value == null || value < 0, anno.message());
	}

	@Override
	public void check(Negative anno, Long value) {
		isTrue(value == null || value < 0, anno.message());
	}

	@Override
	public void check(Negative anno, BigInteger value) {
		isTrue(value == null || value.signum() < 0, anno.message());
	}

	@Override
	public void check(Negative anno, BigDecimal value) {
		isTrue(value == null || value.signum() < 0, anno.message());
	}

	// -----------------------------------------------------------------

	@Override
	public void check(NegativeOrZero anno, byte value) {
		isTrue(value <= 0, anno.message());
	}

	@Override
	public void check(NegativeOrZero anno, short value) {
		isTrue(value <= 0, anno.message());
	}

	@Override
	public void check(NegativeOrZero anno, int value) {
		isTrue(value <= 0, anno.message());
	}

	@Override
	public void check(NegativeOrZero anno, long value) {
		isTrue(value <= 0, anno.message());
	}

	@Override
	public void check(NegativeOrZero anno, Byte value) {
		isTrue(value == null || value <= 0, anno.message());
	}

	@Override
	public void check(NegativeOrZero anno, Short value) {
		isTrue(value == null || value <= 0, anno.message());
	}

	@Override
	public void check(NegativeOrZero anno, Integer value) {
		isTrue(value == null || value <= 0, anno.message());
	}

	@Override
	public void check(NegativeOrZero anno, Long value) {
		isTrue(value == null || value <= 0, anno.message());
	}

	@Override
	public void check(NegativeOrZero anno, BigInteger value) {
		isTrue(value == null || value.signum() <= 0, anno.message());
	}

	@Override
	public void check(NegativeOrZero anno, BigDecimal value) {
		isTrue(value == null || value.signum() <= 0, anno.message());
	}

	// -----------------------------------------------------------------

	@Override
	public void check(Past anno, Date value) {
		isTrue(value == null || value.getTime() < System.currentTimeMillis(), anno.message());
	}

	@Override
	public void check(Past anno, Calendar value) {
		isTrue(value == null || value.getTimeInMillis() < System.currentTimeMillis(), anno.message());
	}

	@Override
	public void check(Past anno, Instant value) {
		isTrue(value == null || value.isBefore(Instant.now()), anno.message());
	}

	@Override
	public void check(Past anno, LocalDate value) {
		isTrue(value == null || value.isBefore(LocalDate.now()), anno.message());
	}

	@Override
	public void check(Past anno, LocalDateTime value) {
		isTrue(value == null || value.isBefore(LocalDateTime.now()), anno.message());
	}

	@Override
	public void check(Past anno, LocalTime value) {
		isTrue(value == null || value.isBefore(LocalTime.now()), anno.message());
	}

	@Override
	public void check(Past anno, MonthDay value) {
		isTrue(value == null || value.isBefore(MonthDay.now()), anno.message());
	}

	@Override
	public void check(Past anno, OffsetDateTime value) {
		isTrue(value == null || value.isBefore(OffsetDateTime.now()), anno.message());
	}

	@Override
	public void check(Past anno, Year value) {
		isTrue(value == null || value.isBefore(Year.now()), anno.message());
	}

	@Override
	public void check(Past anno, YearMonth value) {
		isTrue(value == null || value.isBefore(YearMonth.now()), anno.message());
	}

	@Override
	public void check(Past anno, ZonedDateTime value) {
		isTrue(value == null || value.isBefore(ZonedDateTime.now()), anno.message());
	}

	@Override
	public void check(Past anno, HijrahDate value) {
		isTrue(value == null || value.isBefore(HijrahDate.now()), anno.message());
	}

	@Override
	public void check(Past anno, JapaneseDate value) {
		isTrue(value == null || value.isBefore(JapaneseDate.now()), anno.message());
	}

	@Override
	public void check(Past anno, MinguoDate value) {
		isTrue(value == null || value.isBefore(MinguoDate.now()), anno.message());
	}

	@Override
	public void check(Past anno, ThaiBuddhistDate value) {
		isTrue(value == null || value.isBefore(ThaiBuddhistDate.now()), anno.message());
	}

	// -----------------------------------------------------------------

	@Override
	public void check(PastOrPresent anno, Date value) {
		isTrue(value == null || value.getTime() <= System.currentTimeMillis(), anno.message());
	}

	@Override
	public void check(PastOrPresent anno, Calendar value) {
		isTrue(value == null || value.getTimeInMillis() <= System.currentTimeMillis(), anno.message());
	}

	@Override
	public void check(PastOrPresent anno, Instant value) {
		isTrue(value == null || !value.isAfter(Instant.now()), anno.message());
	}

	@Override
	public void check(PastOrPresent anno, LocalDate value) {
		isTrue(value == null || !value.isAfter(LocalDate.now()), anno.message());
	}

	@Override
	public void check(PastOrPresent anno, LocalDateTime value) {
		isTrue(value == null || !value.isAfter(LocalDateTime.now()), anno.message());
	}

	@Override
	public void check(PastOrPresent anno, LocalTime value) {
		isTrue(value == null || !value.isAfter(LocalTime.now()), anno.message());
	}

	@Override
	public void check(PastOrPresent anno, MonthDay value) {
		isTrue(value == null || !value.isAfter(MonthDay.now()), anno.message());
	}

	@Override
	public void check(PastOrPresent anno, OffsetDateTime value) {
		isTrue(value == null || !value.isAfter(OffsetDateTime.now()), anno.message());
	}

	@Override
	public void check(PastOrPresent anno, Year value) {
		isTrue(value == null || !value.isAfter(Year.now()), anno.message());
	}

	@Override
	public void check(PastOrPresent anno, YearMonth value) {
		isTrue(value == null || !value.isAfter(YearMonth.now()), anno.message());

	}

	@Override
	public void check(PastOrPresent anno, ZonedDateTime value) {
		isTrue(value == null || !value.isAfter(ZonedDateTime.now()), anno.message());

	}

	@Override
	public void check(PastOrPresent anno, HijrahDate value) {
		isTrue(value == null || !value.isAfter(HijrahDate.now()), anno.message());

	}

	@Override
	public void check(PastOrPresent anno, JapaneseDate value) {
		isTrue(value == null || !value.isAfter(JapaneseDate.now()), anno.message());

	}

	@Override
	public void check(PastOrPresent anno, MinguoDate value) {
		isTrue(value == null || !value.isAfter(MinguoDate.now()), anno.message());

	}

	@Override
	public void check(PastOrPresent anno, ThaiBuddhistDate value) {
		isTrue(value == null || !value.isAfter(ThaiBuddhistDate.now()), anno.message());

	}

	// -----------------------------------------------------------------

	@Override
	public void check(Future anno, Date value) {
		isTrue(value == null || value.getTime() > System.currentTimeMillis(), anno.message());

	}

	@Override
	public void check(Future anno, Calendar value) {
		isTrue(value == null || value.getTimeInMillis() > System.currentTimeMillis(), anno.message());

	}

	@Override
	public void check(Future anno, Instant value) {
		isTrue(value == null || value.isAfter(Instant.now()), anno.message());

	}

	@Override
	public void check(Future anno, LocalDate value) {
		isTrue(value == null || value.isAfter(LocalDate.now()), anno.message());

	}

	@Override
	public void check(Future anno, LocalDateTime value) {
		isTrue(value == null || value.isAfter(LocalDateTime.now()), anno.message());

	}

	@Override
	public void check(Future anno, LocalTime value) {
		isTrue(value == null || value.isAfter(LocalTime.now()), anno.message());

	}

	@Override
	public void check(Future anno, MonthDay value) {
		isTrue(value == null || value.isAfter(MonthDay.now()), anno.message());

	}

	@Override
	public void check(Future anno, OffsetDateTime value) {
		isTrue(value == null || value.isAfter(OffsetDateTime.now()), anno.message());

	}

	@Override
	public void check(Future anno, Year value) {
		isTrue(value == null || value.isAfter(Year.now()), anno.message());

	}

	@Override
	public void check(Future anno, YearMonth value) {
		isTrue(value == null || value.isAfter(YearMonth.now()), anno.message());

	}

	@Override
	public void check(Future anno, ZonedDateTime value) {
		isTrue(value == null || value.isAfter(ZonedDateTime.now()), anno.message());

	}

	@Override
	public void check(Future anno, HijrahDate value) {
		isTrue(value == null || value.isAfter(HijrahDate.now()), anno.message());

	}

	@Override
	public void check(Future anno, JapaneseDate value) {
		isTrue(value == null || value.isAfter(JapaneseDate.now()), anno.message());

	}

	@Override
	public void check(Future anno, MinguoDate value) {
		isTrue(value == null || value.isAfter(MinguoDate.now()), anno.message());

	}

	@Override
	public void check(Future anno, ThaiBuddhistDate value) {
		isTrue(value == null || value.isAfter(ThaiBuddhistDate.now()), anno.message());

	}

	// -----------------------------------------------------------------

	@Override
	public void check(FutureOrPresent anno, Date value) {
		isTrue(value == null || value.getTime() >= System.currentTimeMillis(), anno.message());

	}

	@Override
	public void check(FutureOrPresent anno, Calendar value) {
		isTrue(value == null || value.getTimeInMillis() >= System.currentTimeMillis(), anno.message());

	}

	@Override
	public void check(FutureOrPresent anno, Instant value) {
		isTrue(value == null || !value.isBefore(Instant.now()), anno.message());

	}

	@Override
	public void check(FutureOrPresent anno, LocalDate value) {
		isTrue(value == null || !value.isBefore(LocalDate.now()), anno.message());

	}

	@Override
	public void check(FutureOrPresent anno, LocalDateTime value) {
		isTrue(value == null || !value.isBefore(LocalDateTime.now()), anno.message());

	}

	@Override
	public void check(FutureOrPresent anno, LocalTime value) {
		isTrue(value == null || !value.isBefore(LocalTime.now()), anno.message());

	}

	@Override
	public void check(FutureOrPresent anno, MonthDay value) {
		isTrue(value == null || !value.isBefore(MonthDay.now()), anno.message());

	}

	@Override
	public void check(FutureOrPresent anno, OffsetDateTime value) {
		isTrue(value == null || !value.isBefore(OffsetDateTime.now()), anno.message());

	}

	@Override
	public void check(FutureOrPresent anno, Year value) {
		isTrue(value == null || !value.isBefore(Year.now()), anno.message());

	}

	@Override
	public void check(FutureOrPresent anno, YearMonth value) {
		isTrue(value == null || !value.isBefore(YearMonth.now()), anno.message());

	}

	@Override
	public void check(FutureOrPresent anno, ZonedDateTime value) {
		isTrue(value == null || !value.isBefore(ZonedDateTime.now()), anno.message());

	}

	@Override
	public void check(FutureOrPresent anno, HijrahDate value) {
		isTrue(value == null || !value.isBefore(HijrahDate.now()), anno.message());

	}

	@Override
	public void check(FutureOrPresent anno, JapaneseDate value) {
		isTrue(value == null || !value.isBefore(JapaneseDate.now()), anno.message());

	}

	@Override
	public void check(FutureOrPresent anno, MinguoDate value) {
		isTrue(value == null || !value.isBefore(MinguoDate.now()), anno.message());

	}

	@Override
	public void check(FutureOrPresent anno, ThaiBuddhistDate value) {
		isTrue(value == null || !value.isBefore(ThaiBuddhistDate.now()), anno.message());
	}

}