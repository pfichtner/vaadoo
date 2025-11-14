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

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.Value;

class JdkOnlyCodeFragmentTest {

	@Value
	@RequiredArgsConstructor(staticName = "of")
	private static class TestFixture<T extends Annotation, S> implements Consumer<S> {

		BiConsumer<T, S> sut;
		Class<S> type;
		T anno;

		@Override
		public void accept(S value) {
			sut.accept(anno, value);
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
		assertThrowsNothing(fixture, nullValue);
		assertThrows(fixture, nonNullValue, IllegalArgumentException.class);
	}

	@Test
	void checkNotNull() {
		var fixture = TestFixture.of(sut::check, Object.class, anno(NotNull.class));
		assertThrowsNothing(fixture, nonNullValue);
		assertThrows(fixture, nullValue, NullPointerException.class);
	}

	@Test
	void checkNotBlank() {
		var fixture = TestFixture.of(sut::check, CharSequence.class, anno(NotBlank.class));
		assertThrowsNothing(fixture, notBlankString);
		assertThrows(fixture, blankString, IllegalArgumentException.class);
	}

	@Test
	void checkPattern() {
		var fixture = TestFixture.of(sut::check, CharSequence.class, anno(Pattern.class, Map.of("regexp", "[24]{2}")));
		assertThrowsNothing(fixture, "42");
		assertThrows(fixture, "21", IllegalArgumentException.class);
	}

	@Test
	void checkEmail() {
		var fixture = TestFixture.of(sut::check, CharSequence.class, anno(Email.class));
		assertThrowsNothing(fixture, "john.doe@example.com");
		assertThrows(fixture, "missing-at-symbol.com", IllegalArgumentException.class);
	}

	@Test
	void checkNotEmptyCharSequence() {
		var fixture = TestFixture.of(sut::check, CharSequence.class, anno(NotEmpty.class));
		assertThrowsNothing(fixture, notBlankString);
		assertThrows(fixture, emptyString, IllegalArgumentException.class);
	}

	@Test
	void checkNotEmptyCollection() {
		var fixture = TestFixture.of(sut::check, Collection.class, anno(NotEmpty.class));
		assertThrowsNothing(fixture, notEmptyList);
		assertThrows(fixture, emptyList, IllegalArgumentException.class);
	}

	@Test
	void checkNotEmptyMap() {
		var fixture = TestFixture.of(sut::check, Map.class, anno(NotEmpty.class));
		assertThrowsNothing(fixture, notEmptyMap);
		assertThrows(fixture, emptyMap, IllegalArgumentException.class);
	}

	@Test
	void checkNotEmptyArray() {
		var fixture = TestFixture.of(sut::check, Object[].class, anno(NotEmpty.class));
		assertThrowsNothing(fixture, notEmptyArray);
		assertThrows(fixture, emptyArray, IllegalArgumentException.class);
	}

	static <T> void assertThrowsNothing(Consumer<T> consumer, T value) {
		consumer.accept(value);
	}

	static <T> void assertThrows(Consumer<T> consumer, T value, Class<? extends Exception> excpetionType) {
		assertThatThrownBy(() -> consumer.accept(value)).isInstanceOf(excpetionType).hasMessage("theMessage");
	}

}
