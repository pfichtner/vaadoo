package com.github.pfichtner.vaadoo.fragments;

import static com.google.common.base.CharMatcher.whitespace;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Map;

import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;

public class GuavaCodeFragment implements Jsr380CodeFragment {

	@Override
	public void check(Null nullAnno, Object ref) {
		checkArgument(ref == null, nullAnno.message());
	}

	@Override
	public void check(NotNull notNull, Object ref) {
		checkNotNull(ref, notNull.message());
	}

	@Override
	public void check(NotBlank notBlank, CharSequence charSequence) {
		checkArgument(whitespace().negate().countIn(checkNotNull(charSequence, notBlank.message())) > 0,
				notBlank.message());
	}

	@Override
	public void check(NotEmpty notEmpty, CharSequence charSequence) {
		checkArgument(checkNotNull(charSequence, notEmpty.message()).length() > 0, notEmpty.message());
	}

	public void check(NotEmpty notEmpty, Collection<?> collection) {
		checkArgument(checkNotNull(collection, notEmpty.message()).size() > 0, notEmpty.message());
	}

	@Override
	public void check(NotEmpty notEmpty, Map<?, ?> map) {
		checkArgument(checkNotNull(map, notEmpty.message()).size() > 0, notEmpty.message());
	}

	@Override
	public void check(NotEmpty notEmpty, Object[] objects) {
		checkArgument(checkNotNull(objects, notEmpty.message()).length > 0, notEmpty.message());
	}

	@Override
	public void check(AssertTrue assertTrue, boolean value) {
		checkArgument(value, assertTrue.message());
	}

	@Override
	public void check(AssertTrue assertTrue, Boolean value) {
		checkArgument(value == null || value, assertTrue.message());
	}

	@Override
	public void check(AssertFalse assertFalse, boolean value) {
		checkArgument(!value, assertFalse.message());
	}

	@Override
	public void check(AssertFalse assertFalse, Boolean value) {
		checkArgument(value == null || !value, assertFalse.message());
	}

	@Override
	public void check(Min min, byte value) {
		checkArgument(value >= min.value(), min.message());
	}

	@Override
	public void check(Min min, short value) {
		checkArgument(value >= min.value(), min.message());
	}

	@Override
	public void check(Min min, int value) {
		checkArgument(value >= min.value(), min.message());
	}

	@Override
	public void check(Min min, long value) {
		checkArgument(value >= min.value(), min.message());
	}

	@Override
	public void check(Min min, Byte value) {
		checkArgument(value == null || value >= min.value(), min.message());
	}

	@Override
	public void check(Min min, Short value) {
		checkArgument(value == null || value >= min.value(), min.message());
	}

	@Override
	public void check(Min min, Integer value) {
		checkArgument(value == null || value >= min.value(), min.message());
	}

	@Override
	public void check(Min min, Long value) {
		checkArgument(value == null || value >= min.value(), min.message());
	}

	@Override
	public void check(Min min, BigInteger value) {
		checkArgument(value == null || value.compareTo(BigInteger.valueOf(min.value())) >= 0, min.message());
	}

	@Override
	public void check(Min min, BigDecimal value) {
		checkArgument(value == null || value.compareTo(BigDecimal.valueOf(min.value())) >= 0, min.message());
	}

}
