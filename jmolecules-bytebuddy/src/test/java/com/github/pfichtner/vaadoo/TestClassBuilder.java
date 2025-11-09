package com.github.pfichtner.vaadoo;

import static java.lang.Math.abs;
import static java.util.stream.Collectors.joining;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.modifier.Ownership;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.description.type.TypeDescription.ForLoadedType;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.dynamic.DynamicType.Builder.MethodDefinition.ParameterDefinition.Annotatable;
import net.bytebuddy.dynamic.DynamicType.Builder.MethodDefinition.ParameterDefinition.Initial;
import net.bytebuddy.dynamic.DynamicType.Unloaded;
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy;
import net.bytebuddy.implementation.StubMethod;

public class TestClassBuilder {

	private static class NameMaker {

		private final Set<String> names = new HashSet<>();

		public String makeName(Class<?> clazz) {
			String name = clazz.isArray() ? clazz.getComponentType().getSimpleName() + "Array" : clazz.getSimpleName();
			name = name.substring(0, 1).toLowerCase() + name.substring(1);
			String newName = name;
			for (int cnt = 1; !names.add(newName); cnt++) {
				newName = name + cnt;
			}
			return newName;
		}

	}

	@Value
	@RequiredArgsConstructor
	public static class ParameterConfig {
		Class<?> type;
		List<Class<? extends Annotation>> annotations;

		@SafeVarargs
		public ParameterConfig(Class<?> type, Class<? extends Annotation>... annotations) {
			this(type, List.of(annotations));
		}

		public static String stableChecksum(List<ParameterConfig> configs) {
			String stringValue = configs.stream().map(ParameterConfig::asString).collect(joining("|"));
			return String.valueOf(abs(stringValue.hashCode()));
		}

		private static String asString(ParameterConfig config) {
			return config.type.getName() + ":"
					+ config.annotations.stream().map(Class::getName).sorted().collect(joining(","));
		}

	}

	@Value
	public static class ConstructorConfig {
		List<ParameterConfig> parameterConfig;
	}

	@Value
	public static class MethodConfig {
		String methodname;
		List<ParameterConfig> parameterConfig;
	}

	private final Builder<Object> bb;
	private final List<ConstructorConfig> constructors = new ArrayList<>();
	private final List<MethodConfig> methods = new ArrayList<>();

	public TestClassBuilder(String classname) {
		bb = new ByteBuddy() //
				.subclass(Object.class, ConstructorStrategy.Default.NO_CONSTRUCTORS) //
				.implement(ForLoadedType.of(org.jmolecules.ddd.types.ValueObject.class)) //
				.name(classname);
	}

	public TestClassBuilder constructor(ConstructorConfig constructor) {
		this.constructors.add(constructor);
		return this;
	}

	public TestClassBuilder method(MethodConfig method) {
		this.methods.add(method);
		return this;
	}

	public Unloaded<Object> make() throws NoSuchMethodException {
		Builder<Object> built = bb;
		NameMaker nameMaker = new NameMaker();

		for (ConstructorConfig constructorConfig : constructors) {
			Initial<Object> ctorInitial = built.defineConstructor(Visibility.PUBLIC);

			Annotatable<Object> paramDef = null;
			for (ParameterConfig parameterConfig : constructorConfig.getParameterConfig()) {
				paramDef = (paramDef == null ? ctorInitial : paramDef).withParameter(parameterConfig.getType(),
						nameMaker.makeName(parameterConfig.getType()));
				for (Class<? extends Annotation> anno : parameterConfig.getAnnotations()) {
					paramDef = paramDef.annotateParameter(createAnnotation(anno));
				}
			}

			built = (paramDef == null ? ctorInitial : paramDef)
					.intercept(net.bytebuddy.implementation.MethodCall.invoke(Object.class.getDeclaredConstructor()));
		}

		for (MethodConfig methodConfig : methods) {
			built = built.defineMethod(methodConfig.getMethodname(), void.class, Visibility.PRIVATE, Ownership.STATIC)
					.withParameter(Object.class, "someArgName").intercept(StubMethod.INSTANCE);
		}

		return built.make();
	}

	private static AnnotationDescription createAnnotation(Class<? extends Annotation> ann) {
		try {
			AnnotationDescription.Builder builder = AnnotationDescription.Builder.ofType(ann);
			if (ann.equals(Min.class) || ann.equals(Max.class)) {
				builder = builder.define("value", 0L);
			} else if (ann.equals(DecimalMin.class) || ann.equals(DecimalMax.class)) {
				builder = builder.define("value", "0");
			} else if (ann.equals(Digits.class)) {
				builder = builder.define("integer", 0).define("fraction", 0);
			} else if (ann.equals(Pattern.class)) {
				builder = builder.define("regexp", "");
			}
			return builder.build();
		} catch (Exception e) {
			throw new RuntimeException("Error creating " + ann, e);
		}
	}

}
