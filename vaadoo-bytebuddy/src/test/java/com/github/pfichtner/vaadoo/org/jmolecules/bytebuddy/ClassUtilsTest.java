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
package com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ClassUtilsTest {

	@Nested
	class ForName {

		@Test
		void resolvesPrimitiveStyleArray() throws Exception {
			assertThat(ClassUtils.forName("java.lang.String[]", null)).isEqualTo(String[].class);
		}

		@Test
		void resolvesInternalNonPrimitiveArray() throws Exception {
			assertThat(ClassUtils.forName("[Ljava.lang.String;", null)).isEqualTo(String[].class);
		}

		@Test
		void resolvesSimpleType() throws Exception {
			assertThat(ClassUtils.forName("java.lang.String", null)).isEqualTo(String.class);
		}

		@Test
		void resolvesNestedClassSourceStyle() throws Exception {
			assertThat(ClassUtils.forName("java.lang.Thread.State", null)).isEqualTo(Thread.State.class);
		}

		@Test
		void rethrowsWhenNotFound() {
			assertThatThrownBy(() -> ClassUtils.forName("com.example.DoesNotExist", null))
					.isInstanceOf(ClassNotFoundException.class);
		}
	}

	@Nested
	class IsPresent {

		@Test
		void returnsTrueWhenPresent() {
			assertThat(ClassUtils.isPresent("java.lang.String", null)).isTrue();
		}

		@Test
		void returnsFalseWhenAbsent() {
			assertThat(ClassUtils.isPresent("com.example.DoesNotExist", null)).isFalse();
		}

		@Test
		void returnsFalseForPrimitiveWithDefaultLoader() {
			assertThat(ClassUtils.isPresent("com.example.DoesNotExist", getClass().getClassLoader())).isFalse();
		}
	}

}
