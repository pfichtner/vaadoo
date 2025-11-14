/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.pfichtner.vaadoo.fragments.impl;

import static java.lang.reflect.Proxy.newProxyInstance;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Negative;
import jakarta.validation.constraints.NegativeOrZero;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.Value;

class JdkOnlyCodeFragmentTest {

	@Value
	@RequiredArgsConstructor(staticName = "of")
	static class TestFixture<T extends Annotation, S> implements Consumer<S> {

		BiConsumer<T, S> sut;
		Class<S> type;
		T anno;

		@Override
		public void accept(S value) {
			sut.accept(anno, value);
		}

		void assertThrowsNothing(S value) {
			accept(value);
		}

		void assertThrows(S value, Class<? extends Exception> excpetionType) {
			assertThatThrownBy(() -> accept(value)).isInstanceOf(excpetionType).hasMessage("theMessage");
		}

	}

	private static <A extends Annotation> A anno(Class<A> annotationType) {
		return anno(annotationType, emptyMap());

	}

	private static <A extends Annotation> A anno(Class<A> annotationType, Map<String, Object> values) {
		return annotationType.cast(newProxyInstance(annotationType.getClassLoader(), new Class<?>[] { annotationType },
				(InvocationHandler) (p, m, a) -> {
					if (m.getName().equals("annotationType")) {
						return annotationType;
					}
					if (m.getName().equals("message")) {
						return "theMessage";
					}
					Object object = values.get(m.getName());
					return (object == null) ? m.getDefaultValue() : object;
				}));
	}

	JdkOnlyCodeFragment sut = new JdkOnlyCodeFragment();

	Object nullValue = null;
	Object nonNullValue = new Object();
	String blankString = "   ";
	String emptyString = "";
	String notBlankString = "X";
	List<Object> notEmptyList = List.of("a");
	List<Object> emptyList = emptyList();
	Map<Object, Object> notEmptyMap = Map.of("a", 1);
	Map<Object, Object> emptyMap = emptyMap();
	Object[] notEmptyArray = new Object[1];
	Object[] emptyArray = new Object[0];

	@Test
	void checkNull() {
		var fixture = TestFixture.of(sut::check, Object.class, anno(Null.class));
		fixture.assertThrowsNothing(nullValue);
		fixture.assertThrows(nonNullValue, IllegalArgumentException.class);
	}

	@Test
	void checkNotNull() {
		var fixture = TestFixture.of(sut::check, Object.class, anno(NotNull.class));
		fixture.assertThrowsNothing(nonNullValue);
		fixture.assertThrows(nullValue, NullPointerException.class);
	}

	@Test
	void checkNotBlank() {
		var fixture = TestFixture.of(sut::check, CharSequence.class, anno(NotBlank.class));
		fixture.assertThrowsNothing(notBlankString);
		fixture.assertThrows(blankString, IllegalArgumentException.class);
	}

	@Test
	void checkPattern() {
		var fixture = TestFixture.of(sut::check, CharSequence.class, anno(Pattern.class, Map.of("regexp", "[24]{2}")));
		fixture.assertThrowsNothing("42");
		fixture.assertThrows("21", IllegalArgumentException.class);
	}

	@Test
	void checkEmail() {
		var fixture = TestFixture.of(sut::check, CharSequence.class, anno(Email.class));
		fixture.assertThrowsNothing("john.doe@example.com");
		fixture.assertThrows("missing-at-symbol.com", IllegalArgumentException.class);
	}

	@Test
	void checkNotEmpty() {
		{
			var fixture = TestFixture.of(sut::check, CharSequence.class, anno(NotEmpty.class));
			fixture.assertThrowsNothing(notBlankString);
			fixture.assertThrows(emptyString, IllegalArgumentException.class);
		}

		{
			var fixture = TestFixture.of(sut::check, Collection.class, anno(NotEmpty.class));
			fixture.assertThrowsNothing(notEmptyList);
			fixture.assertThrows(emptyList, IllegalArgumentException.class);
		}

		{
			var fixture = TestFixture.of(sut::check, Map.class, anno(NotEmpty.class));
			fixture.assertThrowsNothing(notEmptyMap);
			fixture.assertThrows(emptyMap, IllegalArgumentException.class);
		}

		{
			var fixture = TestFixture.of(sut::check, Object[].class, anno(NotEmpty.class));
			fixture.assertThrowsNothing(notEmptyArray);
			fixture.assertThrows(emptyArray, IllegalArgumentException.class);
		}
	}

	@Test
	void checkSize() {
		{
			var fixture = TestFixture.of(sut::check, CharSequence.class, anno(Size.class, Map.of("min", 2, "max", 4)));
			fixture.assertThrowsNothing("abc");
			fixture.assertThrows("a", IllegalArgumentException.class);
		}

		{
			var fixture = TestFixture.of(sut::check, Collection.class, anno(Size.class, Map.of("min", 1, "max", 2)));
			fixture.assertThrowsNothing(List.of("x"));
			fixture.assertThrows(List.of(), IllegalArgumentException.class);
		}

		{
			var fixture = TestFixture.of(sut::check, Map.class, anno(Size.class, Map.of("min", 1, "max", 2)));
			fixture.assertThrowsNothing(Map.of("a", 1));
			fixture.assertThrows(Map.of(), IllegalArgumentException.class);
		}

		{
			var fixture = TestFixture.of(sut::check, Object[].class, anno(Size.class, Map.of("min", 1, "max", 2)));
			fixture.assertThrowsNothing(new Object[] { "x" });
			fixture.assertThrows(new Object[] {}, IllegalArgumentException.class);
		}
	}

	@Test
	void checkAssertTrueFalse() {
		{
			var fixture = TestFixture.of(sut::check, boolean.class, anno(AssertTrue.class));
			fixture.assertThrowsNothing(true);
			fixture.assertThrows(false, IllegalArgumentException.class);
		}

		{
			var fixture = TestFixture.of(sut::check, Boolean.class, anno(AssertTrue.class));
			fixture.assertThrowsNothing(Boolean.TRUE);
			fixture.assertThrows(Boolean.FALSE, IllegalArgumentException.class);
		}

	}

	void checkAssertFalse() {
		{
			var fixture = TestFixture.of(sut::check, boolean.class, anno(AssertFalse.class));
			fixture.assertThrowsNothing(false);
			fixture.assertThrows(true, IllegalArgumentException.class);
		}

		{
			var fixture = TestFixture.of(sut::check, Boolean.class, anno(AssertFalse.class));
			fixture.assertThrowsNothing(Boolean.FALSE);
			fixture.assertThrows(Boolean.TRUE, IllegalArgumentException.class);
		}
	}

	@Test
	void checkMin() {
		// TODO add checks for primitives
		{
			var fixture = TestFixture.of(sut::check, byte.class, anno(Min.class, Map.of("value", 10L)));
			fixture.assertThrows((byte) 9, IllegalArgumentException.class);
			fixture.assertThrowsNothing((byte) 10);
		}

		{
			var fixture = TestFixture.of(sut::check, short.class, anno(Min.class, Map.of("value", 10L)));
			fixture.assertThrows((short) 9, IllegalArgumentException.class);
			fixture.assertThrowsNothing((short) 10);
		}

		{
			var fixture = TestFixture.of(sut::check, int.class, anno(Min.class, Map.of("value", 10L)));
			fixture.assertThrows((int) 9, IllegalArgumentException.class);
			fixture.assertThrowsNothing((int) 10);
		}

		{
			var fixture = TestFixture.of(sut::check, long.class, anno(Min.class, Map.of("value", 10L)));
			fixture.assertThrows((long) 9, IllegalArgumentException.class);
			fixture.assertThrowsNothing((long) 10);
		}
	}

	@Test
	void checkMax() {
		// TODO add checks for primitives
		{
			var fixture = TestFixture.of(sut::check, byte.class, anno(Max.class, Map.of("value", 10L)));
			fixture.assertThrowsNothing((byte) 10);
			fixture.assertThrows((byte) 11, IllegalArgumentException.class);
		}

		{
			var fixture = TestFixture.of(sut::check, short.class, anno(Max.class, Map.of("value", 10L)));
			fixture.assertThrowsNothing((short) 10);
			fixture.assertThrows((short) 11, IllegalArgumentException.class);
		}

		{
			var fixture = TestFixture.of(sut::check, int.class, anno(Max.class, Map.of("value", 10L)));
			fixture.assertThrowsNothing((int) 10);
			fixture.assertThrows((int) 11, IllegalArgumentException.class);
		}

		{
			var fixture = TestFixture.of(sut::check, long.class, anno(Max.class, Map.of("value", 10L)));
			fixture.assertThrowsNothing((long) 10);
			fixture.assertThrows((long) 11, IllegalArgumentException.class);
		}
	}

	@Test
	void checkDecimalMin() {
		// TODO add missing types
		var fixture = TestFixture.of(sut::check, CharSequence.class, anno(DecimalMin.class, Map.of("value", "2")));
		fixture.assertThrowsNothing("2");
		fixture.assertThrows("1", IllegalArgumentException.class);
	}

	@Test
	void checkDecimalMax() {
		// TODO add missing types
		var fixture = TestFixture.of(sut::check, CharSequence.class, anno(DecimalMax.class, Map.of("value", "5")));
		fixture.assertThrowsNothing("5");
		fixture.assertThrows("6", IllegalArgumentException.class);
	}

	@Test
	void checkDigitsAndSign() {
		// TODO add missing types
		var fixture = TestFixture.of(sut::check, int.class, anno(Digits.class, Map.of("integer", 2, "fraction", 0)));
		fixture.assertThrowsNothing(12);
		fixture.assertThrows(123, IllegalArgumentException.class);
	}

	@Test
	void checkPositive() {
		// TODO add missing types
		var fixture = TestFixture.of(sut::check, int.class, anno(Positive.class));
		fixture.assertThrowsNothing(1);
		fixture.assertThrows(0, IllegalArgumentException.class);
	}

	@Test
	void checkPositiveOrZero() {
		// TODO add missing types
		var fixture = TestFixture.of(sut::check, int.class, anno(PositiveOrZero.class));
		fixture.assertThrowsNothing(0);
		fixture.assertThrows(-1, IllegalArgumentException.class);
	}

	@Test
	void checkNegative() {
		// TODO add missing types
		var fixture = TestFixture.of(sut::check, int.class, anno(Negative.class));
		fixture.assertThrowsNothing(-1);
		fixture.assertThrows(0, IllegalArgumentException.class);
	}

	@Test
	void checkNegativeOrZero() {
		// TODO add missing types
		var fixture = TestFixture.of(sut::check, int.class, anno(NegativeOrZero.class));
		fixture.assertThrowsNothing(0);
		fixture.assertThrows(1, IllegalArgumentException.class);
	}

	@Test
	void checkPast() {
		{
			var fixture = TestFixture.of(sut::check, java.util.Date.class, anno(Past.class));
			fixture.assertThrowsNothing(new java.util.Date(System.currentTimeMillis() - 1_000L));
			fixture.assertThrows(new java.util.Date(System.currentTimeMillis() + 100_000L),
					IllegalArgumentException.class);
		}

		{
			var fixture = TestFixture.of(sut::check, java.time.LocalDate.class, anno(Past.class));
			fixture.assertThrowsNothing(java.time.LocalDate.now().minusDays(1));
			fixture.assertThrows(java.time.LocalDate.now(), IllegalArgumentException.class);
		}
	}

	@Test
	void checkFuture() {
		{
			var fixture = TestFixture.of(sut::check, java.util.Date.class, anno(Future.class));
			fixture.assertThrowsNothing(new java.util.Date(System.currentTimeMillis() + 100_000L));
			fixture.assertThrows(new java.util.Date(System.currentTimeMillis() - 1_000L),
					IllegalArgumentException.class);
		}

		{
			var fixture = TestFixture.of(sut::check, java.time.LocalDate.class, anno(Future.class));
			fixture.assertThrowsNothing(java.time.LocalDate.now().plusDays(1));
			fixture.assertThrows(java.time.LocalDate.now(), IllegalArgumentException.class);
		}
	}

}
