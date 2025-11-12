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
package com.github.pfichtner.vaadoo;

import static com.github.pfichtner.vaadoo.ApprovalUtil.approveTransformed;
import static com.github.pfichtner.vaadoo.Buildable.a;
import static com.github.pfichtner.vaadoo.TestClassBuilder.testClass;
import static com.github.pfichtner.vaadoo.Transformer.newInstance;
import static com.github.pfichtner.vaadoo.Transformer.transformClass;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.github.pfichtner.vaadoo.TestClassBuilder.ConstructorDefinition;
import com.github.pfichtner.vaadoo.TestClassBuilder.MethodDefinition;
import com.github.pfichtner.vaadoo.TestClassBuilder.ParameterDefinition;

import jakarta.validation.constraints.NotNull;

class Jsr380DynamicClassTest {

	TestClassBuilder baseTestClass = testClass("com.example.Generated");
	TestClassBuilder classAnnotatedByValueObject = baseTestClass.annotatedByValueObject();
	TestClassBuilder classThatImplementsValueObject = baseTestClass.thatImplementsValueObject();

	ConstructorDefinition notNullObject = new ConstructorDefinition(
			List.of(new ParameterDefinition(Object.class, List.of(NotNull.class))));
	Object[] nullArg = new Object[] { null };

	@Test
	void noArg() throws Exception {
		var constructor = new ConstructorDefinition(emptyList());
		var unloaded = a(baseTestClass.thatImplementsValueObject().withConstructor(constructor));
		approveTransformed(constructor.params(), unloaded);
	}

	@Test
	void implementingValueObjectAndAnnotatedByValueObjectIsTheSame() throws Exception {
		var transformed1 = transformClass(a(classThatImplementsValueObject.withConstructor(notNullObject)));
		var transformed2 = transformClass(a(classAnnotatedByValueObject.withConstructor(notNullObject)));
		var e1 = assertThrows(RuntimeException.class, () -> newInstance(transformed1, nullArg));
		var e2 = assertThrows(RuntimeException.class, () -> newInstance(transformed2, nullArg));
		assertThat(e1).isExactlyInstanceOf(e2.getClass()).hasMessage(e2.getMessage());
	}

	@Test
	void implementingEntityDoesNotAddBytecode() throws Exception {
		var transformed = transformClass(a(baseTestClass.withInterface(org.jmolecules.ddd.types.Entity.class) //
				.withConstructor(notNullObject)));
		newInstance(transformed, nullArg);
	}

	@Test
	void alreadyHasValidateMethod() throws Exception {
		var unloaded = a(baseTestClass.thatImplementsValueObject().withConstructor(notNullObject)
				.withMethod(new MethodDefinition("validate", emptyList())));
		approveTransformed(notNullObject.params(), unloaded);
	}

}
