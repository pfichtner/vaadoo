package com.github.pfichtner.vaadoo;

import static com.github.pfichtner.vaadoo.ApprovalUtil.approveTransformed;
import static com.github.pfichtner.vaadoo.Transformer.transformClass;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.github.pfichtner.vaadoo.TestClassBuilder.ConstructorDefinition;
import com.github.pfichtner.vaadoo.TestClassBuilder.MethodDefinition;
import com.github.pfichtner.vaadoo.TestClassBuilder.ParameterDefinition;

import jakarta.validation.constraints.NotNull;
import lombok.Value;
import net.bytebuddy.dynamic.DynamicType.Unloaded;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;

class Jsr380DynamicClassTest {

	private static Object newInstance(Unloaded<?> unloaded, Object[] args) throws Exception {
		Class<?> clazz = unloaded.load(new ClassLoader() {
		}, ClassLoadingStrategy.Default.INJECTION).getLoaded();
		try {
			return clazz.getDeclaredConstructors()[0].newInstance(args);
		} catch (InvocationTargetException e) {
			Throwable cause = e.getCause();
			if (cause instanceof Exception) {
				throw (Exception) cause;
			}
			throw new RuntimeException(e);
		}
	}

	private static Object[] args(List<ParameterDefinition> params) {
		return params.stream().map(ParameterDefinition::getType).map(Jsr380DynamicClassTest::getDefault).toArray();
	}

	// TODO use Arbitrary to generate value, e.g. null, "", "XXX" for CharSequence,
	// String, ...
	private static Object getDefault(Class<?> clazz) {
		if (clazz.isPrimitive()) {
			return Array.get(Array.newInstance(clazz, 1), 0);
		} else if (clazz.isArray()) {
			return Array.newInstance(clazz.getComponentType(), 0);
		} else if (clazz == java.time.LocalDate.class) {
			return java.time.LocalDate.now();
		} else if (clazz == java.time.LocalDateTime.class) {
			return java.time.LocalDateTime.now();
		} else if (clazz == Date.class) {
			return new Date();
		} else if (clazz == List.class || clazz == ArrayList.class) {
			return new ArrayList<>();
		} else if (clazz == Set.class || clazz == HashSet.class) {
			return new HashSet<>();
		} else if (clazz == Map.class || clazz == HashMap.class) {
			return new HashMap<>();
		} else if (clazz == StringBuilder.class) {
			return new StringBuilder();
		} else if (clazz == StringBuffer.class) {
			return new StringBuffer();
		} else if (clazz == String.class) {
			return "";
		} else {
			return null;
		}
	}

	@Value
	private static class Storyboard {
		List<ParameterDefinition> params;
		String source;
		String transformed;

		@Override
		public String toString() {
			String br = "-".repeat(64);
			return String.join("\n", //
					List.of(br, //
							"params annotations\n"
									+ params.stream().map(Object::toString).map("- "::concat).collect(joining("\n")), //
							br, //
							source, //
							br, //
							transformed, //
							br //
					) //
			);
		}
	}

	@Test
	void noArg() throws Exception {
		List<ParameterDefinition> noParams = emptyList();
		Unloaded<Object> testClass = new TestClassBuilder("com.example.Generated").implementsValueObject()
				.constructor(new ConstructorDefinition(noParams)).build();
		approveTransformed(noParams, testClass);
	}

	@Test
	void implementingValueObjectAndAnnotatedByValueObjectIsTheSame() throws Exception {
		var params = List.of(new ParameterDefinition(Object.class, List.of(NotNull.class)));
		var constructor = new ConstructorDefinition(params);
		var transformedClass1 = transformClass(
				new TestClassBuilder("com.example.Generated").implementsValueObject().constructor(constructor).build());
		var transformedClass2 = transformClass(new TestClassBuilder("com.example.Generated").annotatedByValueObject()
				.constructor(constructor).build());
		var e1 = assertThrows(RuntimeException.class, () -> newInstance(transformedClass1, args(params)));
		var e2 = assertThrows(RuntimeException.class, () -> newInstance(transformedClass2, args(params)));
		assertThat(e1).isExactlyInstanceOf(e2.getClass()).hasMessage(e2.getMessage());
	}

	@Test
	void alreadyHasValidateMethod() throws Exception {
		List<ParameterDefinition> params = List.of(new ParameterDefinition(Object.class, List.of(NotNull.class)));
		Unloaded<Object> testClass = new TestClassBuilder("com.example.Generated").implementsValueObject()
				.constructor(new ConstructorDefinition(params)).method(new MethodDefinition("validate", emptyList()))
				.build();
		approveTransformed(params, testClass);
	}


}
