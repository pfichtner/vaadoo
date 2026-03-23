package com.github.pfichtner.vaadoo;

import static com.github.pfichtner.vaadoo.Buildable.a;
import static com.github.pfichtner.vaadoo.TestClassBuilder.testClass;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
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

	@Test
	void mapWithAnnotatedKeysAndValuesExceptionMessage() throws Exception {
		var mapOfNotBlankStringsToNotNullIntegers = TypeDefinition.of(Map.class, 
				List.of(String.class, Integer.class),
				List.of(
						List.of(AnnotationDefinition.of(NotBlank.class)),
						List.of(AnnotationDefinition.of(NotNull.class))
				));
		var constructor = ConstructorDefinition.of(
				DefaultParameterDefinition.of(mapOfNotBlankStringsToNotNullIntegers, AnnotationDefinition.of(NotNull.class))
				.withName("myMap")
				);
		var transformed = transformer.transform(a(baseTestClass.thatImplementsValueObject().withConstructor(constructor)));

		// Validation of key: " " is blank
		Exception e1 = assertThrows(Exception.class, () -> Transformer.newInstance(transformed, new Object[] { Map.of(" ", 1) }));
		assertThat(e1).hasMessageContaining("myMap[key= ] must not be blank");

		// Validation of value: null is not allowed
		Map<String, Integer> mapWithValueNull = new HashMap<>();
		mapWithValueNull.put("key", null);
		Exception e2 = assertThrows(Exception.class, () -> Transformer.newInstance(transformed, new Object[] { mapWithValueNull }));
		assertThat(e2).hasMessageContaining("myMap[value for key=key] must not be null");
	}

	@Test
	void arrayWithAnnotatedElementsExceptionMessage() throws Exception {
		var arrayOfNotBlankStrings = TypeDefinition.of(String[].class, String.class,
				AnnotationDefinition.of(NotBlank.class));
		var constructor = ConstructorDefinition.of(
				DefaultParameterDefinition.of(arrayOfNotBlankStrings, AnnotationDefinition.of(NotNull.class))
				.withName("myArray")
				);
		var transformed = transformer.transform(a(baseTestClass.thatImplementsValueObject().withConstructor(constructor)));

		// Validation of element at index 0: " " is blank
		Exception e1 = assertThrows(Exception.class, () -> Transformer.newInstance(transformed, new Object[] { new String[] { " " } }));
		assertThat(e1).hasMessageContaining("myArray[0] must not be blank");

		// Validation of element at index 1: " " is blank
		Exception e2 = assertThrows(Exception.class, () -> Transformer.newInstance(transformed, new Object[] { new String[] { "valid", " " } }));
		assertThat(e2).hasMessageContaining("myArray[1] must not be blank");
	}

	@Test
	void arrayWithPatternAnnotatedElements() throws Exception {
		var arrayOfPatternStrings = TypeDefinition.of(String[].class, String.class,
				AnnotationDefinition.of(jakarta.validation.constraints.Pattern.class, Map.of("regexp", "\\d*")));
		var constructor = ConstructorDefinition.of(
				DefaultParameterDefinition.of(arrayOfPatternStrings, AnnotationDefinition.of(NotNull.class))
				);
		var unloaded = a(baseTestClass.thatImplementsValueObject().withConstructor(constructor));
		new Approver(new Transformer()).approveTransformed("arrayWithPatternAnnotatedElements", constructor.params(),
				unloaded);
	}

	@Test
	void mapWithPatternAnnotatedKeysAndValues() throws Exception {
		var mapOfPatternStringsToPatternStrings = TypeDefinition.of(Map.class, List.of(String.class, String.class),
				List.of(List.of(AnnotationDefinition.of(jakarta.validation.constraints.Pattern.class, Map.of("regexp", "K\\d*"))),
						List.of(AnnotationDefinition.of(jakarta.validation.constraints.Pattern.class, Map.of("regexp", "V\\d*")))));
		var constructor = ConstructorDefinition.of(DefaultParameterDefinition
				.of(mapOfPatternStringsToPatternStrings, AnnotationDefinition.of(NotNull.class)));
		var unloaded = a(baseTestClass.thatImplementsValueObject().withConstructor(constructor));
		new Approver(new Transformer()).approveTransformed("mapWithPatternAnnotatedKeysAndValues", constructor.params(),
				unloaded);
	}

}
