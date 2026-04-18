package com.github.pfichtner.vaadoo.fragments.impl;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.IntStream.range;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Provide;
import net.jqwik.time.api.Dates;

@SuppressWarnings("null")
class ConstraintArbitraries {

	@Provide
	public Arbitrary<String> nonNullStrings() {
		return Arbitraries.strings().ofMinLength(0).ofMaxLength(20);
	}

	@Provide
	public Arbitrary<String> blankStrings() {
		return Arbitraries.strings().withChars(' ', '\t', '\n', '\r', '\f', '\u1680').ofMinLength(0).ofMaxLength(20)
				.filter(s -> s.chars().allMatch(Character::isWhitespace));
	}

	@Provide
	public Arbitrary<String> nonBlankStrings() {
		Arbitrary<String> normal = Arbitraries.strings().ofMinLength(0).ofMaxLength(20)
				.filter(s -> s.chars().anyMatch(c -> !Character.isWhitespace(c)));
		Arbitrary<String> edgeCases = Arbitraries.of("-", "\u2007", "\u00A0", "x\u1680 ", "a ", "  b");
		return Arbitraries.oneOf(normal, edgeCases);
	}

	@Provide
	public Arbitrary<String> twoDigits() {
		return Arbitraries.integers().between(0, 99).map(i -> String.format("%02d", i));
	}

	@Provide
	public Arbitrary<String> sizeStrings() {
		return Arbitraries.strings().ofMinLength(2).ofMaxLength(4);
	}

	@Provide
	public Arbitrary<String> shortStrings() {
		return Arbitraries.strings().ofMaxLength(1);
	}

	@Provide
	public Arbitrary<List<String>> nonEmptyLists() {
		return Arbitraries.strings().ofMinLength(1).list().ofMinSize(1).ofMaxSize(5);
	}

	@Provide
	public Arbitrary<Map<String, Integer>> nonEmptyMaps() {
		return nonEmptyLists().map(l -> Map.of(l.get(0), 1));
	}

	@Provide
	public Arbitrary<Object> emptyArray() {
		return Arbitraries.of(Object.class, String.class, Number.class, Boolean.class, Byte.class, Short.class,
				Integer.class, Long.class, Double.class, Float.class, BigInteger.class, BigDecimal.class,
				Collection.class, List.class, Set.class, Map.class).map(component -> Array.newInstance(component, 0));
	}

	@Provide
	public Arbitrary<LocalDate> pastDates() {
		return Dates.dates().atTheEarliest(LocalDate.of(1970, 1, 1)).atTheLatest(LocalDate.now().minusDays(1));
	}

	@Provide
	public Arbitrary<LocalDate> futureDates() {
		return Dates.dates().atTheEarliest(LocalDate.now().plusDays(1)).atTheLatest(LocalDate.now().plusYears(10));
	}

	@Provide
	public Arbitrary<List<String>> sizeLists() {
		return Arbitraries.strings().list().ofMinSize(2).ofMaxSize(4);
	}

	@Provide
	public Arbitrary<List<String>> shortLists() {
		return Arbitraries.strings().list().ofMaxSize(1);
	}

	@Provide
	public Arbitrary<Map<String, Integer>> sizeMaps() {
		return sizeLists().map(l -> range(0, l.size()).boxed().collect(toMap(i -> "k" + i, identity())));
	}

	@Provide
	public Arbitrary<Map<String, Integer>> shortMaps() {
		return shortLists().map(l -> l.isEmpty() ? Map.of() : Map.of(l.get(0), 1));
	}

	@Provide
	public Arbitrary<Object[]> sizeArrays() {
		return sizeLists().map(l -> l.toArray(new Object[0]));
	}

	@Provide
	public Arbitrary<Object[]> shortArrays() {
		return shortLists().map(l -> l.toArray(new Object[0]));
	}

	@Provide
	public Arbitrary<LocalDate> futureOrPresentDates() {
		return Dates.dates().atTheEarliest(LocalDate.now()).atTheLatest(LocalDate.now().plusYears(10));
	}

	@Provide
	public Arbitrary<LocalDate> pastOrPresentDates() {
		return Dates.dates().atTheEarliest(LocalDate.of(1970, 1, 1)).atTheLatest(LocalDate.now());
	}

}
