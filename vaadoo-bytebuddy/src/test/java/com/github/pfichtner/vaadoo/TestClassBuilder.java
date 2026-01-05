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
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.concat;
import static lombok.AccessLevel.PRIVATE;

import java.lang.annotation.Annotation;
import java.lang.annotation.Repeatable;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

		TypeDefinition typeDefinition();

		List<AnnotationDefinition> annotations();

		public static String stableChecksum(List<ParameterDefinition> parameters) {
			String stringValue = parameters.stream().map(ParameterDefinition::asString).collect(joining("|"));
			return String.valueOf(abs(stringValue.hashCode()));
		}

		static String asString(ParameterDefinition definition) {
			return format("%s:%s", definition.typeDefinition().type().getName(),
					definition.annotations().stream().map(d -> asString(d)).sorted().collect(joining(",")));
		}

		static String asString(AnnotationDefinition definition) {
			return format("%s[%s]", definition.annotation().getName(), definition.values.entrySet().stream()
					.map(v -> v.getKey() + "=" + v.getValue()).collect(joining(",")));
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
	@RequiredArgsConstructor(staticName = "of")
	@Accessors(fluent = true)
	public static class AnnotationDefinition {
		Class<? extends Annotation> annotation;
		LinkedHashMap<String, Object> values;

		public static AnnotationDefinition of(Class<? extends Annotation> annotation) {
			return of(annotation, emptyMap());
		}

		public static AnnotationDefinition of(Class<? extends Annotation> annotation, Map<String, Object> values) {
			return new AnnotationDefinition(annotation, new LinkedHashMap<>(values));
		}

	}

	@Value
	@RequiredArgsConstructor
	@Accessors(fluent = true)
	// example List<@NotBlank String>
	public static class TypeDefinition {
		// e.g. List
		Class<?> type;
		// e.g. String
		Class<?> genericType;
		// e.g. NotBlank()
		List<AnnotationDefinition> genericTypeAnnotations;

		public TypeDefinition(Class<?> type) {
			this(type, null, emptyList());
		}
	}

	@Value
	@RequiredArgsConstructor
	@Accessors(fluent = true)
	public static class DefaultParameterDefinition implements ParameterDefinition {
		TypeDefinition typeDefinition;
		List<AnnotationDefinition> annotations;

		@SafeVarargs
		public DefaultParameterDefinition(Class<?> type, AnnotationDefinition... annotations) {
			this(type, List.of(annotations));
		}

		public DefaultParameterDefinition(Class<?> type, List<AnnotationDefinition> annotations) {
			this(new TypeDefinition(type), annotations);
		}

		@SafeVarargs
		public DefaultParameterDefinition(TypeDefinition type, AnnotationDefinition... annotations) {
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
		return concat(col.stream(), Stream.of(add)).collect(toList());
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
						: nameMaker.makeName(parameter.typeDefinition().type());
				TypeDefinition typeDefinition = parameter.typeDefinition();
				TypeDescription rawType = TypeDescription.Generic.Builder.rawType(typeDefinition.type()).build()
						.asErasure();
				paramDef = (paramDef == null ? ctorInitial : paramDef).withParameter(
						typeDefinition.genericType() == null ? rawType : toGenericType(parameter, rawType), name);
				// Group annotations by their annotation type so we can create a container
				// annotation when multiple repeatable annotations of the same type are present.
				Map<Class<? extends Annotation>, List<AnnotationDefinition>> grouped = parameter.annotations().stream()
						.collect(groupingBy(AnnotationDefinition::annotation, LinkedHashMap::new, toList()));
				for (Map.Entry<Class<? extends Annotation>, List<AnnotationDefinition>> entry : grouped.entrySet()) {
					var annoClass = entry.getKey();
					var definitions = entry.getValue();
					if (definitions.size() == 1) {
						paramDef = paramDef.annotateParameter(createAnnotation(definitions.get(0)));
					} else {
						paramDef = paramDef.annotateParameter(repackToContainer(definitions, annoClass));
					}
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

	private static TypeDescription.Generic toGenericType(ParameterDefinition parameter, TypeDescription rawType) {
		TypeDefinition typeDefinition = parameter.typeDefinition();
		return TypeDescription.Generic.Builder.parameterizedType(rawType,
				addAnnotations(TypeDescription.Generic.Builder.of(typeDefinition.genericType()),
						typeDefinition.genericTypeAnnotations()).build())
				.build();
	}

	private static TypeDescription.Generic.Builder addAnnotations(TypeDescription.Generic.Builder genericType,
			List<AnnotationDefinition> genericTypeAnnotations) {
		for (AnnotationDefinition annotationDefinition : genericTypeAnnotations) {
			genericType = genericType
					.annotate(AnnotationDescription.Builder.ofType(annotationDefinition.annotation()).build());
		}
		return genericType;
	}

	private static AnnotationDescription repackToContainer(List<AnnotationDefinition> defs,
			Class<? extends Annotation> annoClass) {
		Repeatable repeatable = annoClass.getAnnotation(Repeatable.class);
		Class<? extends Annotation> container = repeatable.value();
		AnnotationDescription[] individuals = defs.stream().map(TestClassBuilder::createAnnotation)
				.toArray(AnnotationDescription[]::new);
		return AnnotationDescription.Builder.ofType(container)
				.defineAnnotationArray("value", TypeDescription.ForLoadedType.of(annoClass), individuals).build();
	}

	private Builder<Object> base() {
		return new ByteBuddy() //
				.subclass(Object.class, ConstructorStrategy.Default.NO_CONSTRUCTORS) //
				.name(classname);
	}

	private static AnnotationDescription createAnnotation(AnnotationDefinition annotationDefinition) {
		Class<? extends Annotation> anno = annotationDefinition.annotation();
		try {
			AnnotationDescription.Builder builder = AnnotationDescription.Builder.ofType(anno);
			if (anno.equals(Min.class) || anno.equals(Max.class)) {
				builder = builder.define("value", getAnnotationValue(annotationDefinition, "value", 0L));
			} else if (anno.equals(DecimalMin.class) || anno.equals(DecimalMax.class)) {
				builder = builder.define("value", getAnnotationValue(annotationDefinition, "value", "0"));
			} else if (anno.equals(Digits.class)) {
				builder = builder //
						.define("integer", getAnnotationValue(annotationDefinition, "integer", 0)) //
						.define("fraction", getAnnotationValue(annotationDefinition, "fraction", 0));
			} else if (anno.equals(Pattern.class)) {
				builder = builder.define("regexp", getAnnotationValue(annotationDefinition, "regexp", ""));
			}
			return builder.build();
		} catch (Exception e) {
			throw new RuntimeException(format("Error creating %s", anno), e);
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> T getAnnotationValue(AnnotationDefinition annotationDefinition, String key, T defaultValue) {
		return (T) annotationDefinition.values().getOrDefault(key, defaultValue);
	}

}
