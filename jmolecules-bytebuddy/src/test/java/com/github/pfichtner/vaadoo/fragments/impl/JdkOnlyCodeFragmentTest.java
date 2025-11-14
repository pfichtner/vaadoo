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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.github.pfichtner.vaadoo.fragments.Jsr380CodeFragment;

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
import lombok.Value;

class JdkOnlyCodeFragmentTest {

	@Value
	static class TestFixture {

		Jsr380CodeFragment sut;
		Annotation anno;

		public static TestFixture of(Jsr380CodeFragment sut, Class<?> argType, Class<? extends Annotation> anno) {
			return of(sut, anno, emptyMap());
		}

		private static TestFixture of(Jsr380CodeFragment sut, Class<? extends Annotation> anno,
				Map<String, Object> data) {
			return new TestFixture(sut, anno(anno, data));
		}

		private static <T> T only(Stream<T> stream) {
			return stream.reduce((_ign1, _ign2) -> {
				throw new IllegalStateException("multiple elements");
			}).orElseThrow(() -> new IllegalStateException("no match"));
		}

		private void assertThrows(Object value, Class<? extends Exception> excpetionType) {
			assertThrows(value, excpetionType, value.getClass());
		}

		private void assertThrows(Object value, Class<? extends Exception> excpetionType, Class<?>... param1Types) {
			for (Class<?> param1Type : param1Types) {
				assertThatThrownBy(() -> accept(value, param1Type))
						.isInstanceOf((Class<? extends Exception>) excpetionType).hasMessage("theMessage");
			}
		}

		private void assertThrows(Object value, Class<? extends Exception> excpetionType, List<Class<?>> param1Types) {
			assertThrows(value, excpetionType, param1Types);
		}

		private void assertThrowsNothing(Object value) {
			assertThrowsNothing(value, value.getClass());
		}

		private void assertThrowsNothing(Object value, Class<?>... param1Types) {
			assertThrowsNothing(value, List.of(param1Types));
		}

		private void assertThrowsNothing(Object value, List<Class<?>> param1Types) {
			for (Class<?> param1Type : param1Types) {
				accept(value, param1Type);
			}
		}

		private void accept(Object value, Class<?> param1Type) {
			Method method = only(Arrays.stream(sut.getClass().getMethods()).filter(m -> m.getName().equals("check"))
					.filter(m -> m.getParameterTypes().length == 2 && m.getParameterTypes()[0].isInstance(anno)
							&& m.getParameterTypes()[1].isAssignableFrom(param1Type)));
			try {
				method.invoke(sut, anno, convert(param1Type, value));
			} catch (IllegalAccessException | IllegalArgumentException e) {
				throw new RuntimeException(e);
			} catch (InvocationTargetException e) {
				Throwable cause = e.getCause();
				throw cause instanceof RuntimeException ? (RuntimeException) cause : new RuntimeException(e);
			}
		}

		private static Object convert(Class<?> target, Object value) {
			if (value == null)
				return null;

			if (target.isPrimitive()) {
				if (target == byte.class)
					return ((Number) value).byteValue();
				if (target == short.class)
					return ((Number) value).shortValue();
				if (target == int.class)
					return ((Number) value).intValue();
				if (target == long.class)
					return ((Number) value).longValue();
				if (target == float.class)
					return ((Number) value).floatValue();
				if (target == double.class)
					return ((Number) value).doubleValue();
				if (target == char.class)
					return (char) ((Number) value).intValue();
				if (target == boolean.class)
					return value;
			}

			// wrapper types
			if (Number.class.isAssignableFrom(target) && value instanceof Number) {
				Number n = (Number) value;
				if (target == Byte.class)
					return n.byteValue();
				if (target == Short.class)
					return n.shortValue();
				if (target == Integer.class)
					return n.intValue();
				if (target == Long.class)
					return n.longValue();
				if (target == Float.class)
					return n.floatValue();
				if (target == Double.class)
					return n.doubleValue();
			}

			return target.cast(value);
		}

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
					if (m.getName().equals("toString")) {
						return "$$ann$proxy$$";
					}
					Object object = values.get(m.getName());
					return (object == null) ? m.getDefaultValue() : object;
				}));
	}

	JdkOnlyCodeFragment sutClass = new JdkOnlyCodeFragment();

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
		var fixture = TestFixture.of(sutClass, Object.class, Null.class);
		fixture.assertThrowsNothing(nullValue, Object.class);
		fixture.assertThrows(nonNullValue, IllegalArgumentException.class);
	}

	@Test
	void checkNotNull() {
		var fixture = TestFixture.of(sutClass, Object.class, NotNull.class);
		fixture.assertThrowsNothing(nonNullValue);
		fixture.assertThrows(nullValue, NullPointerException.class, Object.class);
	}

	@Test
	void checkNotBlank() {
		var fixture = TestFixture.of(sutClass, CharSequence.class, NotBlank.class);
		fixture.assertThrowsNothing(notBlankString);
		fixture.assertThrows(blankString, IllegalArgumentException.class);
	}

	@Test
	void checkPattern() {
		var fixture = TestFixture.of(sutClass, Pattern.class, Map.of("regexp", "[24]{2}"));
		fixture.assertThrowsNothing("42");
		fixture.assertThrows("21", IllegalArgumentException.class);
	}

	@Test
	void checkEmail() {
		var fixture = TestFixture.of(sutClass, CharSequence.class, Email.class);
		fixture.assertThrowsNothing("john.doe@example.com");
		fixture.assertThrows("missing-at-symbol.com", IllegalArgumentException.class);
	}

	@Test
	void checkNotEmpty() {
		{
			var fixture = TestFixture.of(sutClass, CharSequence.class, NotEmpty.class);
			fixture.assertThrowsNothing(notBlankString);
			fixture.assertThrows(emptyString, IllegalArgumentException.class);
		}

		{
			var fixture = TestFixture.of(sutClass, Collection.class, NotEmpty.class);
			fixture.assertThrowsNothing(notEmptyList);
			fixture.assertThrows(emptyList, IllegalArgumentException.class);
		}

		{
			var fixture = TestFixture.of(sutClass, Map.class, NotEmpty.class);
			fixture.assertThrowsNothing(notEmptyMap);
			fixture.assertThrows(emptyMap, IllegalArgumentException.class);
		}

		{
			var fixture = TestFixture.of(sutClass, Object[].class, NotEmpty.class);
			fixture.assertThrowsNothing(notEmptyArray);
			fixture.assertThrows(emptyArray, IllegalArgumentException.class);
		}
	}

	@Test
	void checkSize() {
		{
			var fixture = TestFixture.of(sutClass, Size.class, Map.of("min", 2, "max", 4));
			fixture.assertThrowsNothing("ab");
			fixture.assertThrows("a", IllegalArgumentException.class);
		}

		{
			var fixture = TestFixture.of(sutClass, Size.class, Map.of("min", 2, "max", 4));
			fixture.assertThrowsNothing(List.of("x", "y"));
			fixture.assertThrows(List.of("x"), IllegalArgumentException.class);
		}

		{
			var fixture = TestFixture.of(sutClass, Size.class, Map.of("min", 2, "max", 4));
			fixture.assertThrowsNothing(Map.of("a", 1, "b", 2));
			fixture.assertThrows(Map.of("a", 1), IllegalArgumentException.class);
		}

		{
			var fixture = TestFixture.of(sutClass, Size.class, Map.of("min", 2, "max", 4));
			fixture.assertThrowsNothing(new Object[] { "x", "y" });
			fixture.assertThrows(new Object[] { "x" }, IllegalArgumentException.class);
		}
	}

	@Test
	void checkAssertTrueFalse() {
		var fixture = TestFixture.of(sutClass, boolean.class, AssertTrue.class);
		var types = new Class<?>[] { boolean.class, Boolean.class };
		fixture.assertThrowsNothing(true, types);
		fixture.assertThrows(false, IllegalArgumentException.class, types);
	}

	void checkAssertFalse() {
		var fixture = TestFixture.of(sutClass, boolean.class, AssertFalse.class);
		var types = new Class<?>[] { boolean.class, Boolean.class };
		fixture.assertThrowsNothing(false, types);
		fixture.assertThrows(true, IllegalArgumentException.class, types);
	}

	@Test
	void checkMin() {
		var fixture = TestFixture.of(sutClass, Min.class, Map.of("value", 10L));
		var types = new Class<?>[] { byte.class, short.class, int.class, long.class, Byte.class, Short.class,
				Integer.class, Long.class };
		fixture.assertThrows(9, IllegalArgumentException.class, types);
		fixture.assertThrowsNothing(10, types);
	}

	@Test
	void checkMax() {
		var fixture = TestFixture.of(sutClass, Max.class, Map.of("value", 10L));
		var types = new Class<?>[] { byte.class, short.class, int.class, long.class, Byte.class, Short.class,
				Integer.class, Long.class };
		fixture.assertThrowsNothing(10, types);
		fixture.assertThrows((long) 11, IllegalArgumentException.class, types);
	}

	@Test
	void checkDecimalMin() {
		// TODO add missing types
		var fixture = TestFixture.of(sutClass, DecimalMin.class, Map.of("value", "5"));
		fixture.assertThrowsNothing("5");
		fixture.assertThrows("4", IllegalArgumentException.class);
	}

	@Test
	void checkDecimalMax() {
		// TODO add missing types
		var fixture = TestFixture.of(sutClass, DecimalMax.class, Map.of("value", "5"));
		fixture.assertThrowsNothing("5");
		fixture.assertThrows("6", IllegalArgumentException.class);
	}

	@Test
	void checkDigitsAndSign() {
		// TODO add missing types
		var fixture = TestFixture.of(sutClass, Digits.class, Map.of("integer", 2, "fraction", 0));
		fixture.assertThrowsNothing(12);
		fixture.assertThrows(123, IllegalArgumentException.class);
	}

	@Test
	void checkPositive() {
		// TODO add missing types
		var fixture = TestFixture.of(sutClass, int.class, Positive.class);
		fixture.assertThrowsNothing(1);
		fixture.assertThrows(0, IllegalArgumentException.class);
	}

	@Test
	void checkPositiveOrZero() {
		// TODO add missing types
		var fixture = TestFixture.of(sutClass, int.class, PositiveOrZero.class);
		fixture.assertThrowsNothing(0);
		fixture.assertThrows(-1, IllegalArgumentException.class);
	}

	@Test
	void checkNegative() {
		// TODO add missing types
		var fixture = TestFixture.of(sutClass, int.class, Negative.class);
		fixture.assertThrowsNothing(-1);
		fixture.assertThrows(0, IllegalArgumentException.class);
	}

	@Test
	void checkNegativeOrZero() {
		// TODO add missing types
		var fixture = TestFixture.of(sutClass, int.class, NegativeOrZero.class);
		fixture.assertThrowsNothing(0);
		fixture.assertThrows(1, IllegalArgumentException.class);
	}

	@Test
	void checkPast() {
		{
			var fixture = TestFixture.of(sutClass, java.util.Date.class, Past.class);
			fixture.assertThrowsNothing(new java.util.Date(System.currentTimeMillis() - 1_000L));
			fixture.assertThrows(new java.util.Date(System.currentTimeMillis() + 100_000L),
					IllegalArgumentException.class);
		}

		{
			var fixture = TestFixture.of(sutClass, java.time.LocalDate.class, Past.class);
			fixture.assertThrowsNothing(java.time.LocalDate.now().minusDays(1));
			fixture.assertThrows(java.time.LocalDate.now(), IllegalArgumentException.class);
		}
	}

	@Test
	void checkFuture() {
		{
			var fixture = TestFixture.of(sutClass, java.util.Date.class, Future.class);
			fixture.assertThrowsNothing(new java.util.Date(System.currentTimeMillis() + 100_000L));
			fixture.assertThrows(new java.util.Date(System.currentTimeMillis() - 1_000L),
					IllegalArgumentException.class);
		}

		{
			var fixture = TestFixture.of(sutClass, java.time.LocalDate.class, Future.class);
			fixture.assertThrowsNothing(java.time.LocalDate.now().plusDays(1));
			fixture.assertThrows(java.time.LocalDate.now(), IllegalArgumentException.class);
		}
	}

}
