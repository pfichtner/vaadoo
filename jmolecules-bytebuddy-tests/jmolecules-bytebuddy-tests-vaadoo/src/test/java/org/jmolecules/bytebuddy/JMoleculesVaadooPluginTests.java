/*
 * Copyright 2021-2025 the original author or authors.
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
package org.jmolecules.bytebuddy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatRuntimeException;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import example.SampleValueObject;
import example.SampleValueObjectWithSideEffect;

class JMoleculesVaadooPluginTests {

	@Test
	void defaultsForSampleValueObject() throws Exception {
		assertThat(SampleValueObject.class.getDeclaredMethod("validate")).isNotNull();
	}

	@Test
	void throwsExceptionOnNullValue() throws Exception {
		assertThatRuntimeException().isThrownBy(() -> new SampleValueObject(null)).withMessage("parameter 'value' is null");
	}

	@Test
	void doesNotThrowExceptionOnNonNullValue() throws Exception {
		assertThatNoException().isThrownBy(() -> new SampleValueObject("test"));
	}

	@Test
	void mustNotCallAddOnListWithNull() throws Exception {
		List<String> list = new ArrayList<>();
		assertThatRuntimeException().isThrownBy(() -> new SampleValueObjectWithSideEffect(list, null))
				.withMessage("parameter 'toAdd' is null");
		assertThat(list).isEmpty();
	}

}
