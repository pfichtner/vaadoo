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
package com.github.pfichtner.vaadoo;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.github.pfichtner.vaadoo.testclasses.AnnotationDoesNotSupportType;
import com.github.pfichtner.vaadoo.testclasses.ClassWithAttribute;
import com.github.pfichtner.vaadoo.testclasses.ClassWithNotNullAttribute;
import com.github.pfichtner.vaadoo.testclasses.EmptyClass;
import com.github.pfichtner.vaadoo.testclasses.TwoConstructorsValueObject;
import com.github.pfichtner.vaadoo.testclasses.ValueObjectWithAttribute;
import com.github.pfichtner.vaadoo.testclasses.ValueObjectWithRegexAttribute;
import com.github.pfichtner.vaadoo.testclasses.ValueObjectWithRepeatableRegexAttribute;
import com.github.pfichtner.vaadoo.testclasses.ValueObjectWithRepeatableSizeAttribute;
import com.github.pfichtner.vaadoo.testclasses.custom.CustomExample;
import com.github.pfichtner.vaadoo.testclasses.custom.CustomExampleWithCustomMessage;

class JMoleculesVaadooPluginTests {

	private static final Class<? extends RuntimeException> JMOLESCULES_NULL_EXCEPTION_TYPE = IllegalArgumentException.class;

	static String validateMethodName = "validate";
	static Transformer transformer = new Transformer();

	@ParameterizedTest
	@ValueSource(classes = { EmptyClass.class, ClassWithAttribute.class, ClassWithNotNullAttribute.class })
	void doesNotAddValidateMethod(Class<?> clazz) throws Exception {
		assertThat(methodNames(transformer.transform(clazz))).doesNotContain(validateMethodName);
	}

	@ParameterizedTest
	@ValueSource(classes = { ValueObjectWithAttribute.class, TwoConstructorsValueObject.class,
			ValueObjectWithRegexAttribute.class, CustomExample.class, CustomExampleWithCustomMessage.class })
	void doesAddValidateMethod(Class<?> clazz) throws Exception {
		assertThat(methodNames(transformer.transform(clazz))).contains(validateMethodName);
	}

	@Test
	void emptyClassIsUnchanged() throws Exception {
		var transformed = transformer.transform(EmptyClass.class);
		var stringArgConstructor = transformed.getDeclaredConstructor();
		assertThatNoException().isThrownBy(stringArgConstructor::newInstance);
	}

	@Test
	void classWithAttribute() throws Exception {
		var transformed = transformer.transform(ClassWithAttribute.class);
		var stringArgConstructor = transformed.getDeclaredConstructor(String.class);
		assertThatNoException().isThrownBy(() -> stringArgConstructor.newInstance((String) null));
	}

	@Test
	void classWithNotNullAttribute() throws Exception {
		var transformed = transformer.transform(ClassWithNotNullAttribute.class);
		var stringArgConstructor = transformed.getDeclaredConstructor(String.class);
		assertThatNoException().isThrownBy(() -> stringArgConstructor.newInstance((String) null));
	}

	@Test
	void valueObjectWithAttribute() throws Exception {
		var transformed = transformer.transform(ValueObjectWithAttribute.class);
		var stringArgConstructor = transformed.getDeclaredConstructor(String.class);
		assertThatException().isThrownBy(() -> stringArgConstructor.newInstance((String) null))
				.satisfies(e -> assertThat(e.getCause()).isInstanceOf(JMOLESCULES_NULL_EXCEPTION_TYPE)
						.hasMessage(notNull("someString")));
	}

	@Test
	void valueObjectWithTwoConstructors() throws Exception {
		var transformed = transformer.transform(TwoConstructorsValueObject.class);
		var stringArgConstructor = transformed.getDeclaredConstructor(String.class);
		var stringBooleanArgConstructor = transformed.getDeclaredConstructor(String.class, boolean.class);
		assertSoftly(c -> {
			c.assertThatThrownBy(() -> stringArgConstructor.newInstance((String) null)).satisfies(e -> c
					.assertThat(e.getCause()).isInstanceOf(JMOLESCULES_NULL_EXCEPTION_TYPE).hasMessage(notNull("a")));
			c.assertThatThrownBy(() -> stringBooleanArgConstructor.newInstance(null, true)).satisfies(e -> c
					.assertThat(e.getCause()).isInstanceOf(JMOLESCULES_NULL_EXCEPTION_TYPE).hasMessage(notNull("a")));
		});
	}

	@Test
	void regex() throws Exception {
		var transformed = transformer.transform(ValueObjectWithRegexAttribute.class);
		var constructor = transformed.getDeclaredConstructor(String.class);
		constructor.newInstance("42");
		assertThatException().isThrownBy(() -> constructor.newInstance("4")).satisfies(e -> assertThat(e.getCause())
				.isInstanceOf(IllegalArgumentException.class).hasMessage(mustMatch("someTwoDigits", "\"\\d\\d\"")));
	}

	@Test
	void wrongType() {
		assertThatException().isThrownBy(() -> transformer.transform(AnnotationDoesNotSupportType.class))
				.satisfies(e -> assertThat(e).isInstanceOf(IllegalStateException.class)
						.hasMessage("Annotation" + " " + "jakarta.validation.constraints.NotEmpty"
								+ " on type java.lang.Integer not allowed, " + "allowed only on types: "
								+ "[java.lang.CharSequence, java.util.Collection, java.util.Map, java.lang.Object[]]"));
	}

	@ParameterizedTest
	@MethodSource("customExampleSource")
	void customExample(Constructor<?> constructor, List<Object> validArgs, List<Object> invalidArgs, String message)
			throws Exception {
		constructor.newInstance(validArgs.toArray());
		assertThatException().isThrownBy(() -> constructor.newInstance(invalidArgs.toArray())).satisfies(
				e -> assertThat(e.getCause()).isInstanceOf(IllegalArgumentException.class).hasMessage(message));
	}

	@Test
	void repeatableAnnotations() throws Exception {
		var transformed = transformer.transform(ValueObjectWithRepeatableRegexAttribute.class);
		var stringArgConstructor = transformed.getDeclaredConstructor(String.class);

		assertThatNoException().isThrownBy(() -> stringArgConstructor.newInstance("Abc1!"));
		assertSoftly(c -> {
			c.assertThatThrownBy(() -> stringArgConstructor.newInstance("abc1!"))
					.hasCauseInstanceOf(IllegalArgumentException.class)
					.hasRootCauseMessage("Password must contain at least one uppercase letter");
			c.assertThatThrownBy(() -> stringArgConstructor.newInstance("Abc!"))
					.hasCauseInstanceOf(IllegalArgumentException.class)
					.hasRootCauseMessage("Password must contain at least one digit");
			c.assertThatThrownBy(() -> stringArgConstructor.newInstance("Abc1"))
					.hasCauseInstanceOf(IllegalArgumentException.class)
					.hasRootCauseMessage("Password must contain at least one special character");
		});
	}

	@Test
	void repeatableSizeAnnotations() throws Exception {
		var transformed = transformer.transform(ValueObjectWithRepeatableSizeAttribute.class);
		var stringArgConstructor = transformed.getDeclaredConstructor(String.class);

		assertThatNoException().isThrownBy(() -> stringArgConstructor.newInstance("ab"));
		assertThatNoException().isThrownBy(() -> stringArgConstructor.newInstance("abc"));
		assertThatNoException().isThrownBy(() -> stringArgConstructor.newInstance("abcdefghij"));
		assertSoftly(c -> {
			c.assertThatThrownBy(() -> stringArgConstructor.newInstance("a"))
					.hasCauseInstanceOf(IllegalArgumentException.class)
					.hasRootCauseMessage("must have at least 2 characters");
			c.assertThatThrownBy(() -> stringArgConstructor.newInstance("abcdefghijk"))
					.hasCauseInstanceOf(IllegalArgumentException.class)
					.hasRootCauseMessage("must have at most 10 characters");
		});
	}

	static List<Arguments> customExampleSource() throws Exception {
		var transformed1 = transformer.transform(CustomExample.class);
		var transformed2 = transformer.transform(CustomExampleWithCustomMessage.class);
		String validIban = "DE02 6005 0101 0002 0343 04";
		String invalidIban = "DE02";
		return List.of( //
				arguments( //
						transformed1.getDeclaredConstructor(String.class), //
						List.of(validIban), //
						List.of(invalidIban), //
						"iban not valid"), //
				arguments( //
						transformed2.getDeclaredConstructor(String.class, String.class), //
						List.of(validIban, ""), //
						List.of(invalidIban, ""), //
						"iban not a valid IBAN (this is a custum message from resourcebundle)"), //
				arguments( //
						transformed2.getDeclaredConstructor(String.class, boolean.class), //
						List.of(validIban, true), //
						List.of(invalidIban, true), //
						"a custom message") //
		);
	}

	private List<String> methodNames(Class<?> transformed) {
		return Arrays.stream(transformed.getDeclaredMethods()).map(Method::getName).collect(toList());
	}

	static String notNull(String paramName) {
		return format("%s must not be null", paramName);
	}

	static String mustMatch(String paramName, String regexp) {
		return format("%s must match %s", paramName, regexp);
	}

}
