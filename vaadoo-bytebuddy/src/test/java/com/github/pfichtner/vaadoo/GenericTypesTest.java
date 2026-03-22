package com.github.pfichtner.vaadoo;

import static com.github.pfichtner.vaadoo.Buildable.a;
import static com.github.pfichtner.vaadoo.TestClassBuilder.testClass;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.github.pfichtner.vaadoo.TestClassBuilder.AnnotationDefinition;
import com.github.pfichtner.vaadoo.TestClassBuilder.ConstructorDefinition;
import com.github.pfichtner.vaadoo.TestClassBuilder.DefaultParameterDefinition;
import com.github.pfichtner.vaadoo.TestClassBuilder.TypeDefinition;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

class GenericTypesTest {

	TestClassBuilder baseTestClass = testClass("com.example.GenericGenerated");
	Transformer transformer = new Transformer();

	@Test
	void arrayWithAnnotatedElements() throws Exception {
		var arrayOfNotBlankStrings = TypeDefinition.of(String[].class, String.class,
				AnnotationDefinition.of(NotBlank.class));
		var constructor = ConstructorDefinition.of(
				DefaultParameterDefinition.of(arrayOfNotBlankStrings, AnnotationDefinition.of(NotNull.class))
				);
		var unloaded = a(baseTestClass.thatImplementsValueObject().withConstructor(constructor));
		new Approver(new Transformer()).approveTransformed("arrayWithAnnotatedElements", constructor.params(), unloaded);
	}

	@Test
	void primitiveArrayWithAnnotatedElements() throws Exception {
		var arrayOfInts = TypeDefinition.of(int[].class, int.class,
				AnnotationDefinition.of(jakarta.validation.constraints.Min.class, Map.of("value", 1L)));
		var constructor = ConstructorDefinition.of(
				DefaultParameterDefinition.of(arrayOfInts, AnnotationDefinition.of(NotNull.class))
				);
		var unloaded = a(baseTestClass.thatImplementsValueObject().withConstructor(constructor));
		new Approver(new Transformer()).approveTransformed("primitiveArrayWithAnnotatedElements", constructor.params(), unloaded);
	}

	@Test
	void mapWithAnnotatedKeysAndValues() throws Exception {
		var mapOfNotBlankStringsToNotNullIntegers = TypeDefinition.of(Map.class, 
				List.of(String.class, Integer.class),
				List.of(
						List.of(AnnotationDefinition.of(NotBlank.class)),
						List.of(AnnotationDefinition.of(NotNull.class))
				));
		var constructor = ConstructorDefinition.of(
				DefaultParameterDefinition.of(mapOfNotBlankStringsToNotNullIntegers, AnnotationDefinition.of(NotNull.class))
				);
		var unloaded = a(baseTestClass.thatImplementsValueObject().withConstructor(constructor));
		new Approver(new Transformer()).approveTransformed("mapWithAnnotatedKeysAndValues", constructor.params(), unloaded);
	}

}
