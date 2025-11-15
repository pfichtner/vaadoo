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

import static java.lang.Math.abs;
import static java.lang.String.format;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static lombok.AccessLevel.PRIVATE;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.experimental.Delegate;
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

@Value
@lombok.Builder(toBuilder = true)
@AllArgsConstructor
public class TestClassBuilder implements Buildable<Unloaded<?>> {

	private static final Constructor<Object> objectNoArgConstructor = objectNoArgConstructor();

	private static class NameMaker {

		private final Set<String> names = new HashSet<>();

		public String makeName(Class<?> clazz) {
			String suggestion = clazz.isArray() ? clazz.getComponentType().getSimpleName() + "Array"
					: clazz.getSimpleName();
			return makeName(suggestion.substring(0, 1).toLowerCase() + suggestion.substring(1));
		}

		private String makeName(String suggestion) {
			String newName = suggestion;
			for (int cnt = 1; !names.add(newName); cnt++) {
				newName = suggestion + cnt;
			}
			return newName;
		}

	}

	public interface ParameterDefinition {

		Class<?> getType();

		List<Class<? extends Annotation>> getAnnotations();

		public static String stableChecksum(List<ParameterDefinition> parameters) {
			String stringValue = parameters.stream().map(ParameterDefinition::asString).collect(joining("|"));
			return String.valueOf(abs(stringValue.hashCode()));
		}

		static String asString(ParameterDefinition definition) {
			return format("%s:%s", definition.getType().getName(),
					definition.getAnnotations().stream().map(Class::getName).sorted().collect(joining(",")));
		}

	}

	@Value
	@Accessors(fluent = true)
	public static class NamedParameterDefinition implements ParameterDefinition {

		@Delegate
		@Getter(value = PRIVATE)
		ParameterDefinition delegate;
		String name;

	}

	@Value
	@RequiredArgsConstructor
	public static class DefaultParameterDefinition implements ParameterDefinition {
		Class<?> type;
		List<Class<? extends Annotation>> annotations;

		@SafeVarargs
		public DefaultParameterDefinition(Class<?> type, Class<? extends Annotation>... annotations) {
			this(type, List.of(annotations));
		}

		public ParameterDefinition withName(String name) {
			return new NamedParameterDefinition(this, name);
		}

	}

	@Value
	@Accessors(fluent = true)
	@RequiredArgsConstructor
	public static class ConstructorDefinition {
		List<ParameterDefinition> params;

		public ConstructorDefinition(ParameterDefinition... params) {
			this(List.of(params));
		}
	}

	@Value
	@Accessors(fluent = true)
	@RequiredArgsConstructor
	public static class MethodDefinition {
		String methodname;
		List<ParameterDefinition> params;

		public MethodDefinition(String methodname, ParameterDefinition... params) {
			this(methodname, List.of(params));
		}
	}

	String classname;
	List<AnnotationDescription> annotations;
	List<TypeDescription> interfaces;
	List<ConstructorDefinition> constructors;
	List<MethodDefinition> methods;

	private TestClassBuilder(String classname) {
		this.classname = classname;
		this.annotations = new ArrayList<>();
		this.interfaces = new ArrayList<>();
		this.constructors = new ArrayList<>();
		this.methods = new ArrayList<>();
	}

	public static TestClassBuilder testClass(String classname) {
		return new TestClassBuilder(classname);
	}

	private static Constructor<Object> objectNoArgConstructor() {
		try {
			return Object.class.getDeclaredConstructor();
		} catch (NoSuchMethodException | SecurityException e) {
			throw new RuntimeException(e);
		}
	}

	public TestClassBuilder annotatedByValueObject() {
		return withAnnotation(org.jmolecules.ddd.annotation.ValueObject.class);
	}

	private TestClassBuilder withAnnotation(Class<? extends Annotation> clazz) {
		return toBuilder().annotations(append(this.annotations, AnnotationDescription.Builder.ofType(clazz).build()))
				.build();
	}

	public TestClassBuilder thatImplementsValueObject() {
		return withInterface(org.jmolecules.ddd.types.ValueObject.class);
	}

	public TestClassBuilder withInterface(Class<?> clazz) {
		return toBuilder().interfaces(append(this.interfaces, ForLoadedType.of(clazz))).build();
	}

	public TestClassBuilder withConstructor(ConstructorDefinition constructor) {
		return toBuilder().constructors(append(this.constructors, constructor)).build();
	}

	public Buildable<Unloaded<?>> withMethod(MethodDefinition method) {
		return toBuilder().methods(append(this.methods, method)).build();
	}

	private static <T> List<T> append(List<T> col, T add) {
		return Stream.concat(col.stream(), Stream.of(add)).collect(toList());
	}

	@Override
	public Unloaded<?> build() {
		NameMaker nameMaker = new NameMaker();

		Builder<Object> builder = base();
		builder = annotations.stream().reduce(builder, Builder::annotateType, (__, b) -> b);
		builder = interfaces.stream().reduce(builder, Builder::implement, (__, b) -> b);
		for (ConstructorDefinition ctor : constructors) {
			Initial<Object> ctorInitial = builder.defineConstructor(Visibility.PUBLIC);

			Annotatable<Object> paramDef = null;
			for (ParameterDefinition parameter : ctor.params()) {
				String name = parameter instanceof NamedParameterDefinition
						? nameMaker.makeName(((NamedParameterDefinition) parameter).name())
						: nameMaker.makeName(parameter.getType());
				paramDef = (paramDef == null ? ctorInitial : paramDef).withParameter(parameter.getType(), name);
				for (Class<? extends Annotation> anno : parameter.getAnnotations()) {
					paramDef = paramDef.annotateParameter(createAnnotation(anno));
				}
			}

			builder = (paramDef == null ? ctorInitial : paramDef)
					.intercept(net.bytebuddy.implementation.MethodCall.invoke(objectNoArgConstructor));
		}

		for (MethodDefinition method : methods) {
			builder = builder.defineMethod(method.methodname(), void.class, Visibility.PRIVATE, Ownership.STATIC)
					.withParameter(Object.class, "someArgName").intercept(StubMethod.INSTANCE);
		}

		return builder.make();
	}

	private Builder<Object> base() {
		return new ByteBuddy() //
				.subclass(Object.class, ConstructorStrategy.Default.NO_CONSTRUCTORS) //
				.name(classname);
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
