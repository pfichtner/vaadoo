package com.github.pfichtner.vaadoo;

import static java.lang.Math.abs;
import static java.util.stream.Collectors.joining;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
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
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeDescription.ForLoadedType;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.dynamic.DynamicType.Builder.MethodDefinition.ParameterDefinition.Annotatable;
import net.bytebuddy.dynamic.DynamicType.Builder.MethodDefinition.ParameterDefinition.Initial;
import net.bytebuddy.dynamic.DynamicType.Unloaded;
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy;
import net.bytebuddy.implementation.StubMethod;

public class TestClassBuilder {

	private static final Constructor<Object> objectNoArgConstructor = objectNoArgConstructor();

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
	public static class ConstructorDefinition {
		List<ParameterConfig> parameterConfig;
	}

	@Value
	public static class MethodDefinition {
		String methodname;
		List<ParameterConfig> parameterConfig;
	}

	private final String classname;
	private final List<TypeDescription> interfaces = new ArrayList<>();
	private final List<ConstructorDefinition> constructors = new ArrayList<>();
	private final List<MethodDefinition> methods = new ArrayList<>();

	public TestClassBuilder(String classname) {
		this.classname = classname;
	}

	private static Constructor<Object> objectNoArgConstructor() {
		try {
			return Object.class.getDeclaredConstructor();
		} catch (NoSuchMethodException | SecurityException e) {
			throw new RuntimeException(e);
		}
	}

	public TestClassBuilder implementsValueObject() {
		return withInterface(org.jmolecules.ddd.types.ValueObject.class);
	}

	public TestClassBuilder withInterface(Class<?> clazz) {
		this.interfaces.add(ForLoadedType.of(clazz));
		return this;
	}

	public TestClassBuilder constructor(ConstructorDefinition constructor) {
		this.constructors.add(constructor);
		return this;
	}

	public TestClassBuilder method(MethodDefinition method) {
		this.methods.add(method);
		return this;
	}

	public Unloaded<Object> build() {
		NameMaker nameMaker = new NameMaker();

		Builder<Object> builder = interfaces.stream().reduce(base(), Builder::implement, (__, b) -> b);
		for (ConstructorDefinition constructorConfig : constructors) {
			Initial<Object> ctorInitial = builder.defineConstructor(Visibility.PUBLIC);

			Annotatable<Object> paramDef = null;
			for (ParameterConfig parameterConfig : constructorConfig.getParameterConfig()) {
				paramDef = (paramDef == null ? ctorInitial : paramDef).withParameter(parameterConfig.getType(),
						nameMaker.makeName(parameterConfig.getType()));
				for (Class<? extends Annotation> anno : parameterConfig.getAnnotations()) {
					paramDef = paramDef.annotateParameter(createAnnotation(anno));
				}
			}

			builder = (paramDef == null ? ctorInitial : paramDef)
					.intercept(net.bytebuddy.implementation.MethodCall.invoke(objectNoArgConstructor));
		}

		for (MethodDefinition methodConfig : methods) {
			builder = builder
					.defineMethod(methodConfig.getMethodname(), void.class, Visibility.PRIVATE, Ownership.STATIC)
					.withParameter(Object.class, "someArgName").intercept(StubMethod.INSTANCE);
		}

		return builder.make();
	}

	private Builder<Object> base() {
		Builder<Object> built = new ByteBuddy() //
				.subclass(Object.class, ConstructorStrategy.Default.NO_CONSTRUCTORS) //
				.name(classname);
		return built;
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
