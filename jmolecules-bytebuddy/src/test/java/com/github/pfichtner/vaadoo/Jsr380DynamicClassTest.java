package com.github.pfichtner.vaadoo;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;

import com.github.pfichtner.vaadoo.fragments.Jsr380CodeFragment;

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
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.dynamic.DynamicType.Builder.MethodDefinition.ParameterDefinition.Annotatable;
import net.bytebuddy.dynamic.DynamicType.Builder.MethodDefinition.ParameterDefinition.Initial;
import net.bytebuddy.dynamic.DynamicType.Unloaded;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy;
import net.bytebuddy.implementation.MethodCall;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

// TODO create arguments that are compatible, e.g. List, Set for Collection, String[], Integer[], Foo[] for Object[], e.g. 
class Jsr380DynamicClassTest {

	@Value
	private static class ParameterConfig {
		private final Class<?> type;
		private final Set<Class<? extends Annotation>> annotations;
	}

	static final Map<Class<?>, List<Class<?>>> ANNO_TO_TYPES = Stream.of(Jsr380CodeFragment.class.getMethods())
			.collect(groupingBy(m -> m.getParameterTypes()[0], mapping(m -> m.getParameterTypes()[1], toList())));

	static final Set<Class<?>> ALL_SUPPORTED_TYPES = ANNO_TO_TYPES.values().stream().flatMap(List::stream)
			.collect(toSet());

	static ToolProvider javap = ToolProvider.findFirst("javap")
			.orElseThrow(() -> new RuntimeException("javap not found"));

	// ------------------------ jqwik generator ------------------------

	@Provide
	Arbitrary<List<ParameterConfig>> constructorParameters() {
		return parameterConfigGen().list().ofMinSize(0).ofMaxSize(5);
	}

	private Arbitrary<ParameterConfig> parameterConfigGen() {
		Arbitrary<Class<?>> typeGen = Arbitraries.of(ALL_SUPPORTED_TYPES);
		return typeGen.flatMap(type -> {
			@SuppressWarnings("unchecked")
			List<Class<? extends Annotation>> applicable = ANNO_TO_TYPES.entrySet().stream()
					.filter(e -> e.getValue().stream().anyMatch(vt -> vt.isAssignableFrom(type) || vt.equals(type)))
					.map(e -> (Class<? extends Annotation>) e.getKey()).collect(toList());

			Arbitrary<Set<Class<? extends Annotation>>> annosGen = Arbitraries.of(applicable).set().ofMinSize(0)
					.ofMaxSize(applicable.size());

			return annosGen.map(annos -> new ParameterConfig(type, annos));
		});
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
			} else {

			}
			return builder.build();
		} catch (Exception e) {
			throw new RuntimeException("Error creating " + ann, e);
		}
	}

	private static Unloaded<Object> generateClass(List<ParameterConfig> params) throws NoSuchMethodException {
		Builder<Object> bb = new ByteBuddy().subclass(Object.class, ConstructorStrategy.Default.NO_CONSTRUCTORS)
				.name("com.example.Generated_" + UUID.randomUUID().toString().replace("-", ""));

		Initial<Object> ctor = bb.defineConstructor(Visibility.PUBLIC);

		Annotatable<Object> paramDef = null;
		for (int i = 0; i < params.size(); i++) {
			ParameterConfig p = params.get(i);
			paramDef = (paramDef == null ? ctor : paramDef).withParameter((Class<?>) p.getType(), "arg" + i);

			for (Class<? extends Annotation> ann : p.getAnnotations()) {
				AnnotationDescription desc = buildAnnotation(ann, p.getType());
				if (desc != null) {
					paramDef = paramDef.annotateParameter(desc);
				}
			}
		}

		return (paramDef == null ? ctor : paramDef) //
				.intercept(MethodCall.invoke(Object.class.getDeclaredConstructor())) //
				.make();
	}

	@Property(tries = 10)
	void camLoadClassAreCallCOnstructor(@ForAll("constructorParameters") List<ParameterConfig> params)
			throws Exception {
		Class<?> generated = generateClass(params)
				.load(Jsr380DynamicClassTest.class.getClassLoader(), ClassLoadingStrategy.Default.INJECTION)
				.getLoaded();
		Constructor<?> ctor = generated.getDeclaredConstructors()[0];

		Class<?>[] paramTypes = ctor.getParameterTypes();
		Annotation[][] paramAnnos = ctor.getParameterAnnotations();

		assert paramTypes.length == params.size()
				: "paramTypes.length (" + paramTypes.length + ") != params.size() (" + params.size() + ")";

		for (int i = 0; i < paramTypes.length; i++) {
			Class<?> paramType = paramTypes[i];
			Set<Class<? extends Annotation>> expectedAnnos = params.get(i).getAnnotations();

			Set<Class<?>> actualAnnos = Arrays.stream(paramAnnos[i]).map(Annotation::annotationType).collect(toSet());

			// All expected annotations are attached
			assert actualAnnos.containsAll(expectedAnnos);

			// All attached annotations are compatible with the parameter type
			for (Class<?> ann : actualAnnos) {
				List<Class<?>> allowed = ANNO_TO_TYPES.get(ann);
				assert allowed != null : "allowed is null";
				boolean valid = allowed.stream().anyMatch(t -> t.isAssignableFrom(paramType) || t.equals(paramType));
				assert valid : "Annotation " + ann.getSimpleName() + " not valid for type " + paramType.getSimpleName();
			}
		}
	}

	@Property(tries = 10)
	void writePlayBook(@ForAll("constructorParameters") List<ParameterConfig> params) throws Exception {
		Unloaded<Object> dynamicType = generateClass(params);
		File tempFile = File.createTempFile("bb-generated", ".class");
		tempFile.deleteOnExit();
		try {
			Files.write(tempFile.toPath(), dynamicType.getBytes());
			int result = javap.run(System.out, System.err, "-c", "-p", "-v", tempFile.getAbsolutePath());
			System.out.println("Exit code: " + result);
		} finally {
			tempFile.delete();
		}

		Class<?> generated = dynamicType
				.load(Jsr380DynamicClassTest.class.getClassLoader(), ClassLoadingStrategy.Default.INJECTION)
				.getLoaded();
		Constructor<?> ctor = generated.getDeclaredConstructors()[0];

		Class<?>[] paramTypes = ctor.getParameterTypes();
		Annotation[][] paramAnnos = ctor.getParameterAnnotations();

		assert paramTypes.length == params.size()
				: "paramTypes.length (" + paramTypes.length + ") != params.size() (" + params.size() + ")";

		for (int i = 0; i < paramTypes.length; i++) {
			Class<?> paramType = paramTypes[i];
			Set<Class<? extends Annotation>> expectedAnnos = params.get(i).getAnnotations();

			Set<Class<?>> actualAnnos = Arrays.stream(paramAnnos[i]).map(Annotation::annotationType).collect(toSet());

			// All expected annotations are attached
			assert actualAnnos.containsAll(expectedAnnos);

			// All attached annotations are compatible with the parameter type
			for (Class<?> ann : actualAnnos) {
				List<Class<?>> allowed = ANNO_TO_TYPES.get(ann);
				assert allowed != null : "allowed is null";
				boolean valid = allowed.stream().anyMatch(t -> t.isAssignableFrom(paramType) || t.equals(paramType));
				assert valid : "Annotation " + ann.getSimpleName() + " not valid for type " + paramType.getSimpleName();
			}
		}
	}
}
