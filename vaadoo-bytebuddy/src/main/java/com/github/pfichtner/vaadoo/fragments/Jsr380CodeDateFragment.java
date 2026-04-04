package com.github.pfichtner.vaadoo.fragments;

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
import java.util.Date;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;

public interface Jsr380CodeDateFragment {

	void check(Past anno, Date value, Object[] args);

	void check(Past anno, Calendar value, Object[] args);

	void check(Past anno, Instant value, Object[] args);

	void check(Past anno, LocalDate value, Object[] args);

	void check(Past anno, LocalDateTime value, Object[] args);

	void check(Past anno, LocalTime value, Object[] args);

	void check(Past anno, MonthDay value, Object[] args);

	void check(Past anno, OffsetDateTime value, Object[] args);

	void check(Past anno, Year value, Object[] args);

	void check(Past anno, YearMonth value, Object[] args);

	void check(Past anno, ZonedDateTime value, Object[] args);

	void check(Past anno, HijrahDate value, Object[] args);

	void check(Past anno, JapaneseDate value, Object[] args);

	void check(Past anno, MinguoDate value, Object[] args);

	void check(Past anno, ThaiBuddhistDate value, Object[] args);

	// -------------------------------------

	void check(PastOrPresent anno, Date value, Object[] args);

	void check(PastOrPresent anno, Calendar value, Object[] args);

	void check(PastOrPresent anno, Instant value, Object[] args);

	void check(PastOrPresent anno, LocalDate value, Object[] args);

	void check(PastOrPresent anno, LocalDateTime value, Object[] args);

	void check(PastOrPresent anno, LocalTime value, Object[] args);

	void check(PastOrPresent anno, MonthDay value, Object[] args);

	void check(PastOrPresent anno, OffsetDateTime value, Object[] args);

	void check(PastOrPresent anno, Year value, Object[] args);

	void check(PastOrPresent anno, YearMonth value, Object[] args);

	void check(PastOrPresent anno, ZonedDateTime value, Object[] args);

	void check(PastOrPresent anno, HijrahDate value, Object[] args);

	void check(PastOrPresent anno, JapaneseDate value, Object[] args);

	void check(PastOrPresent anno, MinguoDate value, Object[] args);

	void check(PastOrPresent anno, ThaiBuddhistDate value, Object[] args);

	// -------------------------------------

	void check(Future anno, Date value, Object[] args);

	void check(Future anno, Calendar value, Object[] args);

	void check(Future anno, Instant value, Object[] args);

	void check(Future anno, LocalDate value, Object[] args);

	void check(Future anno, LocalDateTime value, Object[] args);

	void check(Future anno, LocalTime value, Object[] args);

	void check(Future anno, MonthDay value, Object[] args);

	void check(Future anno, OffsetDateTime value, Object[] args);

	void check(Future anno, Year value, Object[] args);

	void check(Future anno, YearMonth value, Object[] args);

	void check(Future anno, ZonedDateTime value, Object[] args);

	void check(Future anno, HijrahDate value, Object[] args);

	void check(Future anno, JapaneseDate value, Object[] args);

	void check(Future anno, MinguoDate value, Object[] args);

	void check(Future anno, ThaiBuddhistDate value, Object[] args);

	// -------------------------------------

	void check(FutureOrPresent anno, Date value, Object[] args);

	void check(FutureOrPresent anno, Calendar value, Object[] args);

	void check(FutureOrPresent anno, Instant value, Object[] args);

	void check(FutureOrPresent anno, LocalDate value, Object[] args);

	void check(FutureOrPresent anno, LocalDateTime value, Object[] args);

	void check(FutureOrPresent anno, LocalTime value, Object[] args);

	void check(FutureOrPresent anno, MonthDay value, Object[] args);

	void check(FutureOrPresent anno, OffsetDateTime value, Object[] args);

	void check(FutureOrPresent anno, Year value, Object[] args);

	void check(FutureOrPresent anno, YearMonth value, Object[] args);

	void check(FutureOrPresent anno, ZonedDateTime value, Object[] args);

	void check(FutureOrPresent anno, HijrahDate value, Object[] args);

	void check(FutureOrPresent anno, JapaneseDate value, Object[] args);

	void check(FutureOrPresent anno, MinguoDate value, Object[] args);

	void check(FutureOrPresent anno, ThaiBuddhistDate value, Object[] args);

}
