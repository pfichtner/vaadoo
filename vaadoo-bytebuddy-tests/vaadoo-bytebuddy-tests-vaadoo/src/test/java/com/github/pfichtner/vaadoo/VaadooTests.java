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

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatRuntimeException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import example.vaadoo.SampleValueObject;
import example.vaadoo.SampleValueObjectWithSideEffect;

class VaadooTests {

	@Test
	void defaultsForSampleValueObject() {
		List<List<Class<?>>> methodParams = Stream.of(SampleValueObject.class.getDeclaredMethods())
				.filter(m -> m.getName().equals("validate")).map(Method::getParameterTypes).map(Arrays::asList)
				.collect(toList());
		assertThat(methodParams).containsExactly(List.of(String.class));
	}

	@Test
	void throwsExceptionOnNullValue() {
		assertThatRuntimeException().isThrownBy(() -> new SampleValueObject(null))
				.withMessage("value must not be null");
	}

	@Test
	void throwsExceptionOnEmptyValue() {
		assertThatRuntimeException().isThrownBy(() -> new SampleValueObject("")).withMessage("value must not be empty");
	}

	@Test
	void doesNotThrowExceptionOnNonNullValueAndAssignsValues() {
		String value = "not null and not empty";
		assertThat(new SampleValueObject(value).getValue()).isEqualTo(value);
	}

	@Test
	void mustNotCallAddOnListWithNull() {
		@SuppressWarnings("unchecked")
		List<String> listMock = mock(List.class);
		assertThatRuntimeException().isThrownBy(() -> new SampleValueObjectWithSideEffect(listMock, null))
				.withMessage("toAdd must not be null");
		verifyNoInteractions(listMock);
	}

}
