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
package example.ignored;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * As {@link SampleValueObject} is not part of the <code>example.vaddoo</code>
 * package it gets ignored by the vaadoo.config, so nothing gets weave in.
 */
class VaadooTests {

	@Test
	void defaultsForSampleValueObject() {
		assertThat(Stream.of(SampleValueObject.class.getDeclaredMethods()).filter(m -> m.getName().equals("validate"))
				.map(Method::getParameterTypes).map(Arrays::asList)).isEmpty();
	}

	@Test
	void doesNotThrowExceptionOnNullValue() {
		new SampleValueObject(null);
	}

	@Test
	void doesNotThrowExceptionOnEmptyValue() {
		new SampleValueObject("");
	}

}
