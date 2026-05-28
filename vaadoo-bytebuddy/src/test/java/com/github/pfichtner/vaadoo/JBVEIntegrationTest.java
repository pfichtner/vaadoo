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

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.jmolecules.ddd.annotation.ValueObject;
import org.junit.jupiter.api.Test;

import net.andreinc.jbvext.annotations.str.UpperCase;

class JBVEIntegrationTest {

	@ValueObject
	public static class UppercaseExample {
		private final String value;

		public UppercaseExample(@UpperCase String value) {
			this.value = value;
		}

		public String getValue() {
			return value;
		}
	}

	@Test
	void uppercaseValidatorShouldBeInitialized() throws Exception {
		Transformer transformer = new Transformer();
		Class<?> transformed = transformer.transform(UppercaseExample.class);
		var constructor = transformed.getDeclaredConstructor(String.class);

		assertThatNoException().isThrownBy(() -> constructor.newInstance("ABC"));
		assertThatThrownBy(() -> constructor.newInstance("abc")).hasRootCauseInstanceOf(IllegalArgumentException.class);
	}

}
