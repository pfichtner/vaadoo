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

	void check(Min anno, byte value);

	void check(Min anno, short value);

	void check(Min anno, int value);

	void check(Min anno, long value);

	void check(Min anno, Byte value);

	void check(Min anno, Short value);

	void check(Min anno, Integer value);

	void check(Min anno, Long value);

	void check(Min anno, BigInteger value);

	void check(Min anno, BigDecimal value);

	// -------------------------------------

	void check(Max anno, byte value);

	void check(Max anno, short value);

	void check(Max anno, int value);

	void check(Max anno, long value);

	void check(Max anno, Byte value);

	void check(Max anno, Short value);

	void check(Max anno, Integer value);

	void check(Max anno, Long value);

	void check(Max anno, BigInteger value);

	void check(Max anno, BigDecimal value);

	// -------------------------------------

	void check(DecimalMin anno, byte value);

	void check(DecimalMin anno, short value);

	void check(DecimalMin anno, int value);

	void check(DecimalMin anno, long value);

	void check(DecimalMin anno, Byte value);

	void check(DecimalMin anno, Short value);

	void check(DecimalMin anno, Integer value);

	void check(DecimalMin anno, Long value);

	void check(DecimalMin anno, BigInteger value);

	void check(DecimalMin anno, BigDecimal value);

	void check(DecimalMin anno, CharSequence value);

	// -------------------------------------

	void check(DecimalMax anno, byte value);

	void check(DecimalMax anno, short value);

	void check(DecimalMax anno, int value);

	void check(DecimalMax anno, long value);

	void check(DecimalMax anno, Byte value);

	void check(DecimalMax anno, Short value);

	void check(DecimalMax anno, Integer value);

	void check(DecimalMax anno, Long value);

	void check(DecimalMax anno, BigInteger value);

	void check(DecimalMax anno, BigDecimal value);

	void check(DecimalMax anno, CharSequence value);

	// -------------------------------------

	void check(Digits anno, byte value);

	void check(Digits anno, short value);

	void check(Digits anno, int value);

	void check(Digits anno, long value);

	void check(Digits anno, Byte value);

	void check(Digits anno, Short value);

	void check(Digits anno, Integer value);

	void check(Digits anno, Long value);

	void check(Digits anno, BigInteger value);

	void check(Digits anno, BigDecimal value);

	void check(Digits anno, CharSequence value);

	// -------------------------------------

	void check(Positive anno, byte value);

	void check(Positive anno, short value);

	void check(Positive anno, int value);

	void check(Positive anno, long value);

	void check(Positive anno, Byte value);

	void check(Positive anno, Short value);

	void check(Positive anno, Integer value);

	void check(Positive anno, Long value);

	void check(Positive anno, BigInteger value);

	void check(Positive anno, BigDecimal value);

	// -------------------------------------

	void check(PositiveOrZero anno, byte value);

	void check(PositiveOrZero anno, short value);

	void check(PositiveOrZero anno, int value);

	void check(PositiveOrZero anno, long value);

	void check(PositiveOrZero anno, Byte value);

	void check(PositiveOrZero anno, Short value);

	void check(PositiveOrZero anno, Integer value);

	void check(PositiveOrZero anno, Long value);

	void check(PositiveOrZero anno, BigInteger value);

	void check(PositiveOrZero anno, BigDecimal value);

	// -------------------------------------

	void check(Negative anno, byte value);

	void check(Negative anno, short value);

	void check(Negative anno, int value);

	void check(Negative anno, long value);

	void check(Negative anno, Byte value);

	void check(Negative anno, Short value);

	void check(Negative anno, Integer value);

	void check(Negative anno, Long value);

	void check(Negative anno, BigInteger value);

	void check(Negative anno, BigDecimal value);

	// -------------------------------------

	void check(NegativeOrZero anno, byte value);

	void check(NegativeOrZero anno, short value);

	void check(NegativeOrZero anno, int value);

	void check(NegativeOrZero anno, long value);

	void check(NegativeOrZero anno, Byte value);

	void check(NegativeOrZero anno, Short value);

	void check(NegativeOrZero anno, Integer value);

	void check(NegativeOrZero anno, Long value);

	void check(NegativeOrZero anno, BigInteger value);

	void check(NegativeOrZero anno, BigDecimal value);

}
