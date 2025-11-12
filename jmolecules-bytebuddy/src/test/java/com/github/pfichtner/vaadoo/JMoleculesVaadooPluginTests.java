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

import static com.github.pfichtner.vaadoo.Transformer.transform;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import org.junit.jupiter.api.Test;

import com.github.pfichtner.vaadoo.testclasses.AnnotationDoesNotSupportType;
import com.github.pfichtner.vaadoo.testclasses.ClassWithAttribute;
import com.github.pfichtner.vaadoo.testclasses.ClassWithNotNullAttribute;
import com.github.pfichtner.vaadoo.testclasses.EmptyClass;
import com.github.pfichtner.vaadoo.testclasses.TwoConstructorsValueObject;
import com.github.pfichtner.vaadoo.testclasses.ValueObjectWithAttribute;
import com.github.pfichtner.vaadoo.testclasses.ValueObjectWithRegexAttribute;
import com.github.pfichtner.vaadoo.testclasses.custom.CustomExample;

class JMoleculesVaadooPluginTests {

	@Test
	void emptyClassIsUnchanged() throws Exception {
		var transformed = transform(EmptyClass.class);
		var stringArgConstructor = transformed.getDeclaredConstructor();
		assertThatNoException().isThrownBy(() -> stringArgConstructor.newInstance());
	}

	@Test
	void classWithAttribute() throws Exception {
		var transformed = transform(ClassWithAttribute.class);
		var stringArgConstructor = transformed.getDeclaredConstructor(String.class);
		assertThatNoException().isThrownBy(() -> stringArgConstructor.newInstance((String) null));
	}

	@Test
	void classWithNotNullAttribute() throws Exception {
		var transformed = transform(ClassWithNotNullAttribute.class);
		var stringArgConstructor = transformed.getDeclaredConstructor(String.class);
		assertThatException().isThrownBy(() -> stringArgConstructor.newInstance((String) null))
				.satisfies(e -> assertThat(e.getCause()).isInstanceOf(NullPointerException.class)
						.hasMessage(notNull("someString")));
	}

	@Test
	void valueObjectWithAttribute() throws Exception {
		var transformed = transform(ValueObjectWithAttribute.class);
		var stringArgConstructor = transformed.getDeclaredConstructor(String.class);
		assertThatException().isThrownBy(() -> stringArgConstructor.newInstance((String) null))
				.satisfies(e -> assertThat(e.getCause()).isInstanceOf(NullPointerException.class)
						.hasMessage(notNull("someString")));
	}

	@Test
	void valueObjectWithTwoConstructors() throws Exception {
		var transformed = transform(TwoConstructorsValueObject.class);
		var stringArgConstructor = transformed.getDeclaredConstructor(String.class);
		var stringBooleanArgConstructor = transformed.getDeclaredConstructor(String.class, boolean.class);
		assertSoftly(c -> {
			c.assertThatException().isThrownBy(() -> stringArgConstructor.newInstance((String) null)).satisfies(
					e -> c.assertThat(e.getCause()).isInstanceOf(NullPointerException.class).hasMessage(notNull("a")));
			c.assertThatException().isThrownBy(() -> stringBooleanArgConstructor.newInstance((String) null, true))
					.satisfies(e -> c.assertThat(e.getCause()).isInstanceOf(NullPointerException.class)
							.hasMessage(notNull("a")));
		});
	}

	@Test
	void regex() throws Exception {
		var transformed = transform(ValueObjectWithRegexAttribute.class);
		var constructor = transformed.getDeclaredConstructor(String.class);
		constructor.newInstance("42");
		assertThatException().isThrownBy(() -> constructor.newInstance("4")).satisfies(e -> assertThat(e.getCause())
				.isInstanceOf(IllegalArgumentException.class).hasMessage(mustMatch("someTwoDigits", "\"\\d\\d\"")));

	}

	@Test
	void wrongType() {
		assertThatException().isThrownBy(() -> transform(AnnotationDoesNotSupportType.class))
				.satisfies(e -> assertThat(e).isInstanceOf(IllegalStateException.class)
						.hasMessage("Annotation" + " " + "jakarta.validation.constraints.NotEmpty"
								+ " on type java.lang.Integer not allowed, " + "allowed only on types: "
								+ "[java.lang.CharSequence, java.util.Collection, java.util.Map, java.lang.Object[]]"));
	}

	@Test
	void customExample() throws Exception {
		var transformed = transform(CustomExample.class);
		var stringConstructor = transformed.getDeclaredConstructor(String.class);
		var stringStringConstructor = transformed.getDeclaredConstructor(String.class, String.class);
		var stringBooleanConstructor = transformed.getDeclaredConstructor(String.class, boolean.class);

		stringConstructor.newInstance("DE02 6005 0101 0002 0343 04");
		stringStringConstructor.newInstance("DE02 6005 0101 0002 0343 04", "");
		stringBooleanConstructor.newInstance("DE02 6005 0101 0002 0343 04", true);

		assertThatException().isThrownBy(() -> stringConstructor.newInstance("DE02"))
				.satisfies(e -> assertThat(e.getCause()).isInstanceOf(IllegalArgumentException.class)
						.hasMessage("iban not valid"));

		assertThatException().isThrownBy(() -> stringStringConstructor.newInstance("DE02", ""))
				.satisfies(e -> assertThat(e.getCause()).isInstanceOf(IllegalArgumentException.class)
						.hasMessage("iban not a valid IBAN (this is a custum message from resourcebundle)"));

		assertThatException().isThrownBy(() -> stringBooleanConstructor.newInstance("DE02", true))
				.satisfies(e -> assertThat(e.getCause()).isInstanceOf(IllegalArgumentException.class)
						.hasMessage("a custom message"));
	}

	static String notNull(String paramName) {
		return format("%s must not be null", paramName);
	}

	static String mustMatch(String paramName, String regexp) {
		return format("%s must match %s", paramName, regexp);
	}

}
