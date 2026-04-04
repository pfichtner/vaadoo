package com.github.pfichtner.vaadoo.fragments;

import java.math.BigDecimal;
import java.math.BigInteger;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Negative;
import jakarta.validation.constraints.NegativeOrZero;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public interface Jsr380CodeNumberFragment {

	void check(Min anno, byte value, Object[] args);

	void check(Min anno, short value, Object[] args);

	void check(Min anno, int value, Object[] args);

	void check(Min anno, long value, Object[] args);

	void check(Min anno, Byte value, Object[] args);

	void check(Min anno, Short value, Object[] args);

	void check(Min anno, Integer value, Object[] args);

	void check(Min anno, Long value, Object[] args);

	void check(Min anno, BigInteger value, Object[] args);

	void check(Min anno, BigDecimal value, Object[] args);

	// -------------------------------------

	void check(Max anno, byte value, Object[] args);

	void check(Max anno, short value, Object[] args);

	void check(Max anno, int value, Object[] args);

	void check(Max anno, long value, Object[] args);

	void check(Max anno, Byte value, Object[] args);

	void check(Max anno, Short value, Object[] args);

	void check(Max anno, Integer value, Object[] args);

	void check(Max anno, Long value, Object[] args);

	void check(Max anno, BigInteger value, Object[] args);

	void check(Max anno, BigDecimal value, Object[] args);

	// -------------------------------------

	void check(DecimalMin anno, byte value, Object[] args);

	void check(DecimalMin anno, short value, Object[] args);

	void check(DecimalMin anno, int value, Object[] args);

	void check(DecimalMin anno, long value, Object[] args);

	void check(DecimalMin anno, Byte value, Object[] args);

	void check(DecimalMin anno, Short value, Object[] args);

	void check(DecimalMin anno, Integer value, Object[] args);

	void check(DecimalMin anno, Long value, Object[] args);

	void check(DecimalMin anno, BigInteger value, Object[] args);

	void check(DecimalMin anno, BigDecimal value, Object[] args);

	void check(DecimalMin anno, CharSequence value, Object[] args);

	// -------------------------------------

	void check(DecimalMax anno, byte value, Object[] args);

	void check(DecimalMax anno, short value, Object[] args);

	void check(DecimalMax anno, int value, Object[] args);

	void check(DecimalMax anno, long value, Object[] args);

	void check(DecimalMax anno, Byte value, Object[] args);

	void check(DecimalMax anno, Short value, Object[] args);

	void check(DecimalMax anno, Integer value, Object[] args);

	void check(DecimalMax anno, Long value, Object[] args);

	void check(DecimalMax anno, BigInteger value, Object[] args);

	void check(DecimalMax anno, BigDecimal value, Object[] args);

	void check(DecimalMax anno, CharSequence value, Object[] args);

	// -------------------------------------

	void check(Digits anno, byte value, Object[] args);

	void check(Digits anno, short value, Object[] args);

	void check(Digits anno, int value, Object[] args);

	void check(Digits anno, long value, Object[] args);

	void check(Digits anno, Byte value, Object[] args);

	void check(Digits anno, Short value, Object[] args);

	void check(Digits anno, Integer value, Object[] args);

	void check(Digits anno, Long value, Object[] args);

	void check(Digits anno, BigInteger value, Object[] args);

	void check(Digits anno, BigDecimal value, Object[] args);

	void check(Digits anno, CharSequence value, Object[] args);

	// -------------------------------------

	void check(Positive anno, byte value, Object[] args);

	void check(Positive anno, short value, Object[] args);

	void check(Positive anno, int value, Object[] args);

	void check(Positive anno, long value, Object[] args);

	void check(Positive anno, Byte value, Object[] args);

	void check(Positive anno, Short value, Object[] args);

	void check(Positive anno, Integer value, Object[] args);

	void check(Positive anno, Long value, Object[] args);

	void check(Positive anno, BigInteger value, Object[] args);

	void check(Positive anno, BigDecimal value, Object[] args);

	// -------------------------------------

	void check(PositiveOrZero anno, byte value, Object[] args);

	void check(PositiveOrZero anno, short value, Object[] args);

	void check(PositiveOrZero anno, int value, Object[] args);

	void check(PositiveOrZero anno, long value, Object[] args);

	void check(PositiveOrZero anno, Byte value, Object[] args);

	void check(PositiveOrZero anno, Short value, Object[] args);

	void check(PositiveOrZero anno, Integer value, Object[] args);

	void check(PositiveOrZero anno, Long value, Object[] args);

	void check(PositiveOrZero anno, BigInteger value, Object[] args);

	void check(PositiveOrZero anno, BigDecimal value, Object[] args);

	// -------------------------------------

	void check(Negative anno, byte value, Object[] args);

	void check(Negative anno, short value, Object[] args);

	void check(Negative anno, int value, Object[] args);

	void check(Negative anno, long value, Object[] args);

	void check(Negative anno, Byte value, Object[] args);

	void check(Negative anno, Short value, Object[] args);

	void check(Negative anno, Integer value, Object[] args);

	void check(Negative anno, Long value, Object[] args);

	void check(Negative anno, BigInteger value, Object[] args);

	void check(Negative anno, BigDecimal value, Object[] args);

	// -------------------------------------

	void check(NegativeOrZero anno, byte value, Object[] args);

	void check(NegativeOrZero anno, short value, Object[] args);

	void check(NegativeOrZero anno, int value, Object[] args);

	void check(NegativeOrZero anno, long value, Object[] args);

	void check(NegativeOrZero anno, Byte value, Object[] args);

	void check(NegativeOrZero anno, Short value, Object[] args);

	void check(NegativeOrZero anno, Integer value, Object[] args);

	void check(NegativeOrZero anno, Long value, Object[] args);

	void check(NegativeOrZero anno, BigInteger value, Object[] args);

	void check(NegativeOrZero anno, BigDecimal value, Object[] args);

}
