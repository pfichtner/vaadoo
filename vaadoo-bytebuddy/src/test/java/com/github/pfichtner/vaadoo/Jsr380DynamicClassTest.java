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
import static com.github.pfichtner.vaadoo.Transformer.newInstance;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.github.pfichtner.vaadoo.TestClassBuilder.AnnotationDefinition;
import com.github.pfichtner.vaadoo.TestClassBuilder.ConstructorDefinition;
import com.github.pfichtner.vaadoo.TestClassBuilder.DefaultParameterDefinition;
import com.github.pfichtner.vaadoo.TestClassBuilder.MethodDefinition;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

class Jsr380DynamicClassTest {

	TestClassBuilder baseTestClass = testClass("com.example.Generated");
	TestClassBuilder classAnnotatedByValueObject = baseTestClass.annotatedByValueObject();
	TestClassBuilder classThatImplementsValueObject = baseTestClass.thatImplementsValueObject();

	ConstructorDefinition notNullObjectConstructor = new ConstructorDefinition(
			new DefaultParameterDefinition(Object.class, AnnotationDefinition.of(NotNull.class)));
	Object[] nullArg = new Object[] { null };

	Transformer transformer = new Transformer();

	@Test
	void noArg() throws Exception {
		var noArgsConstructor = new ConstructorDefinition(emptyList());
		var unloaded = a(baseTestClass.thatImplementsValueObject().withConstructor(noArgsConstructor));
		new Approver(new Transformer()).approveTransformed("noArg", noArgsConstructor.params(), unloaded);
	}

	@Test
	void namedArg() throws Exception {
		var constructor = new ConstructorDefinition(
				new DefaultParameterDefinition(Object.class, AnnotationDefinition.of(NotNull.class))
						.withName("aNamedArgument"));
		var unloaded = a(baseTestClass.thatImplementsValueObject().withConstructor(constructor));
		new Approver(new Transformer()).approveTransformed("namedArg", constructor.params(), unloaded);
	}

	@Test
	void patternArg() throws Exception {
		var constructor = new ConstructorDefinition(new DefaultParameterDefinition(String.class,
				AnnotationDefinition.of(Pattern.class, Map.of("regexp", "\\d*"))));
		var unloaded = a(baseTestClass.thatImplementsValueObject().withConstructor(constructor));
		new Approver(new Transformer()).approveTransformed("regexp", constructor.params(), unloaded);
	}

	@Test
	void implementingValueObjectAndAnnotatedByValueObjectIsTheSame() throws Exception {
		var transformed1 = transformer
				.transform(a(classThatImplementsValueObject.withConstructor(notNullObjectConstructor)));
		var transformed2 = transformer
				.transform(a(classAnnotatedByValueObject.withConstructor(notNullObjectConstructor)));
		var e1 = assertThrows(RuntimeException.class, () -> newInstance(transformed1, nullArg));
		var e2 = assertThrows(RuntimeException.class, () -> newInstance(transformed2, nullArg));
		assertThat(e1).isExactlyInstanceOf(e2.getClass()).hasMessage(e2.getMessage());
	}

	@Test
	void implementingEntityDoesNotAddBytecode() throws Exception {
		var transformed = transformer.transform(a(baseTestClass.withInterface(org.jmolecules.ddd.types.Entity.class) //
				.withConstructor(notNullObjectConstructor)));
		newInstance(transformed, nullArg);
	}

	@Test
	void alreadyHasValidateMethod() throws Exception {
		var unloaded = a(baseTestClass.thatImplementsValueObject().withConstructor(notNullObjectConstructor)
				.withMethod(new MethodDefinition("validate", emptyList())));
		new Approver(new Transformer()).approveTransformed("Class already defines #validate method",
				notNullObjectConstructor.params(), unloaded);
	}

	@Test
	@Disabled
	void genericAnnotatedType() throws Exception {
		var listOfNotBlankStrings = TypeDefinition.of(List.class, String.class,
				List.of(AnnotationDefinition.of(NotBlank.class)));
		var constructor = ConstructorDefinition
				.of(DefaultParameterDefinition.of(listOfNotBlankStrings, AnnotationDefinition.of(NotNull.class)));
		var unloaded = a(baseTestClass.thatImplementsValueObject().withConstructor(constructor));
		new Approver(new Transformer()).approveTransformed("genericAnnotatedType", constructor.params(), unloaded);
	}

}
