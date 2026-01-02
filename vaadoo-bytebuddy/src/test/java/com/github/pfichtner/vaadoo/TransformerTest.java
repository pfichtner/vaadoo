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

import static com.github.pfichtner.vaadoo.Buildable.a;
import static com.github.pfichtner.vaadoo.TestClassBuilder.testClass;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.github.pfichtner.vaadoo.TestClassBuilder.ConstructorDefinition;
import com.github.pfichtner.vaadoo.TestClassBuilder.DefaultParameterDefinition;

import jakarta.validation.constraints.NotNull;

class TransformerTest {

	Transformer transformer = new Transformer();

	@Test
	void newInstanceShouldFailWhenClassRefersToJsr380Annotation() throws Exception {
		// Use the JSR-380 annotation type itself as the constructor parameter type
		// so the generated class still references the forbidden package at load time.
		var ctor = new ConstructorDefinition(new DefaultParameterDefinition(NotNull.class));
		var unloaded = a(testClass("com.example.RefersJsr380").withConstructor(ctor));
		var transformedClass = transformer.transform(unloaded);
		assertThrows(NoClassDefFoundError.class,
				() -> Transformer.newInstance(transformedClass, new Object[] { null }));
	}

}
