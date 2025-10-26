package org.jmolecules.bytebuddy.vaadoo.fragments;

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

	void check(Past anno, Date value);

	void check(Past anno, Calendar value);

	void check(Past anno, Instant value);

	void check(Past anno, LocalDate value);

	void check(Past anno, LocalDateTime value);

	void check(Past anno, LocalTime value);

	void check(Past anno, MonthDay value);

	void check(Past anno, OffsetDateTime value);

	void check(Past anno, Year value);

	void check(Past anno, YearMonth value);

	void check(Past anno, ZonedDateTime value);

	void check(Past anno, HijrahDate value);

	void check(Past anno, JapaneseDate value);

	void check(Past anno, MinguoDate value);

	void check(Past anno, ThaiBuddhistDate value);

	// -------------------------------------

	void check(PastOrPresent anno, Date value);

	void check(PastOrPresent anno, Calendar value);

	void check(PastOrPresent anno, Instant value);

	void check(PastOrPresent anno, LocalDate value);

	void check(PastOrPresent anno, LocalDateTime value);

	void check(PastOrPresent anno, LocalTime value);

	void check(PastOrPresent anno, MonthDay value);

	void check(PastOrPresent anno, OffsetDateTime value);

	void check(PastOrPresent anno, Year value);

	void check(PastOrPresent anno, YearMonth value);

	void check(PastOrPresent anno, ZonedDateTime value);

	void check(PastOrPresent anno, HijrahDate value);

	void check(PastOrPresent anno, JapaneseDate value);

	void check(PastOrPresent anno, MinguoDate value);

	void check(PastOrPresent anno, ThaiBuddhistDate value);

	// -------------------------------------

	void check(Future anno, Date value);

	void check(Future anno, Calendar value);

	void check(Future anno, Instant value);

	void check(Future anno, LocalDate value);

	void check(Future anno, LocalDateTime value);

	void check(Future anno, LocalTime value);

	void check(Future anno, MonthDay value);

	void check(Future anno, OffsetDateTime value);

	void check(Future anno, Year value);

	void check(Future anno, YearMonth value);

	void check(Future anno, ZonedDateTime value);

	void check(Future anno, HijrahDate value);

	void check(Future anno, JapaneseDate value);

	void check(Future anno, MinguoDate value);

	void check(Future anno, ThaiBuddhistDate value);

	// -------------------------------------

	void check(FutureOrPresent anno, Date value);

	void check(FutureOrPresent anno, Calendar value);

	void check(FutureOrPresent anno, Instant value);

	void check(FutureOrPresent anno, LocalDate value);

	void check(FutureOrPresent anno, LocalDateTime value);

	void check(FutureOrPresent anno, LocalTime value);

	void check(FutureOrPresent anno, MonthDay value);

	void check(FutureOrPresent anno, OffsetDateTime value);

	void check(FutureOrPresent anno, Year value);

	void check(FutureOrPresent anno, YearMonth value);

	void check(FutureOrPresent anno, ZonedDateTime value);

	void check(FutureOrPresent anno, HijrahDate value);

	void check(FutureOrPresent anno, JapaneseDate value);

	void check(FutureOrPresent anno, MinguoDate value);

	void check(FutureOrPresent anno, ThaiBuddhistDate value);

}
