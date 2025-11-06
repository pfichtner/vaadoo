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
import lombok.Value;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.description.type.TypeDescription.ForLoadedType;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.dynamic.DynamicType.Builder.MethodDefinition.ParameterDefinition.Annotatable;
import net.bytebuddy.dynamic.DynamicType.Builder.MethodDefinition.ParameterDefinition.Initial;
import net.bytebuddy.dynamic.DynamicType.Unloaded;
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.jar.asm.Type;

public class TestClassBuilder {

	public static class NameMaker {

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
	public static class ParameterConfig {
		Class<?> type;
		List<Class<? extends Annotation>> annotations;

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
	public static class MethodConfig {
		String methodname;
		List<ParameterConfig> parameterConfig;
	}

	private final Builder<Object> bb;
	private final List<ParameterConfig> constructors = new ArrayList<>();
	private final List<MethodConfig> methods = new ArrayList<>();

	public TestClassBuilder(String classname) {
		bb = new ByteBuddy() //
				.subclass(Object.class, ConstructorStrategy.Default.NO_CONSTRUCTORS) //
				.implement(ForLoadedType.of(org.jmolecules.ddd.types.ValueObject.class)) //
				.name(classname);
	}

	public TestClassBuilder constructor(ParameterConfig config) {
		this.constructors.add(config);
		return this;
	}

	public TestClassBuilder constructors(List<ParameterConfig> params) {
		return params.stream().reduce(this, TestClassBuilder::constructor, (x, __) -> x);
	}

	public TestClassBuilder method(MethodConfig config) {
		this.methods.add(config);
		return this;
	}

	public Unloaded<Object> generateClass() throws NoSuchMethodException {

		Initial<Object> builder = bb.defineConstructor(Visibility.PUBLIC);

		Annotatable<Object> paramDef = null;
		NameMaker nameMaker = new NameMaker();
		for (ParameterConfig parameterConfig : constructors) {
			paramDef = addAnnotation(builder, paramDef, nameMaker.makeName(parameterConfig.type), parameterConfig);
		}

		for (MethodConfig methodConfig : methods) {
			builder = bb.defineMethod(methodConfig.getMethodname(), Void.class.getGenericSuperclass());
		}

		return (paramDef == null ? builder : paramDef) //
				.intercept(MethodCall.invoke(Object.class.getDeclaredConstructor())) //
				.make();
	}

	private Annotatable<Object> addAnnotation(Initial<Object> ctor, Annotatable<Object> paramDef, String paramName,
			ParameterConfig parameterConfig) {
		paramDef = (paramDef == null ? ctor : paramDef).withParameter((Class<?>) parameterConfig.getType(), paramName);
		for (Class<? extends Annotation> anno : parameterConfig.getAnnotations()) {
			return paramDef.annotateParameter(buildAnnotation(anno, parameterConfig.getType()));
		}
		return paramDef;
	}

	private static AnnotationDescription buildAnnotation(Class<? extends Annotation> ann, Class<?> paramType) {
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
