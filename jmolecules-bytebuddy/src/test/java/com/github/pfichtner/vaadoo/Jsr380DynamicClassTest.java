package com.github.pfichtner.vaadoo;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static net.jqwik.api.ShrinkingMode.OFF;
import static org.approvaltests.Approvals.settings;
import static org.approvaltests.Approvals.verify;
import static org.approvaltests.namer.NamerFactory.withParameters;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;

import org.approvaltests.namer.NamedEnvironment;

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
		Class<?> type;
		List<Class<? extends Annotation>> annotations;

		public static String stableChecksum(List<ParameterConfig> configs) {
			return sha256(configs.stream().map(ParameterConfig::asString).collect(joining("|")));
		}

		private static String asString(ParameterConfig config) {
			return config.type.getName() + ":"
					+ config.annotations.stream().map(Class::getName).sorted().collect(joining(","));
		}

		private static String sha256(String input) {
			return String.valueOf(Math.abs(input.hashCode()));
		}

	}

	static final Map<Class<?>, List<Class<?>>> ANNO_TO_TYPES = //
			Stream.of(Jsr380CodeFragment.class.getMethods()) //
					.sorted(comparing(Method::getName)) //
					.collect(groupingBy( //
							m -> m.getParameterTypes()[0], //
							mapping(m -> m.getParameterTypes()[1], toList()) //
					));

	static final List<Class<?>> ALL_SUPPORTED_TYPES = ANNO_TO_TYPES.values().stream() //
			.flatMap(List::stream) //
			.distinct() //
			.sorted(comparing(Class::getName)) //
			.collect(toList());

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
					.sorted(Map.Entry.comparingByKey(comparing(Class::getName)))
					.filter(e -> e.getValue().stream().anyMatch(vt -> vt.isAssignableFrom(type) || vt.equals(type)))
					.map(e -> (Class<? extends Annotation>) e.getKey()).collect(toList());

			Arbitrary<List<Class<? extends Annotation>>> annosGen = Arbitraries.of(applicable).list().uniqueElements()
					.ofMinSize(0).ofMaxSize(applicable.size());

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

	private static Unloaded<Object> generateClass(List<ParameterConfig> params, String random)
			throws NoSuchMethodException {
		Builder<Object> bb = new ByteBuddy().subclass(Object.class, ConstructorStrategy.Default.NO_CONSTRUCTORS)
				.name("com.example.Generated_" + random);

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
		String random = UUID.randomUUID().toString().replace("-", "");
		Class<?> generated = generateClass(params, random)
				.load(Jsr380DynamicClassTest.class.getClassLoader(), ClassLoadingStrategy.Default.INJECTION)
				.getLoaded();
		Constructor<?> ctor = generated.getDeclaredConstructors()[0];

		// TODO call constructor, verify exception caught (or none if none)

		Class<?>[] paramTypes = ctor.getParameterTypes();
		Annotation[][] paramAnnos = ctor.getParameterAnnotations();

		assert paramTypes.length == params.size()
				: "paramTypes.length (" + paramTypes.length + ") != params.size() (" + params.size() + ")";

		for (int i = 0; i < paramTypes.length; i++) {
			Class<?> paramType = paramTypes[i];
			List<Class<? extends Annotation>> expectedAnnos = params.get(i).getAnnotations();

			List<Class<?>> actualAnnos = Arrays.stream(paramAnnos[i]).map(Annotation::annotationType).collect(toList());

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

	@Value
	static class Storyboad {
		List<ParameterConfig> params;
		JavapResult javapResult;

		@Override
		public String toString() {
			String br = "-".repeat(64);
			FirstSectionScrubber scrubber = new FirstSectionScrubber();
			return String.join("\n", //
					List.of(br, //
							"params annotations\n"
									+ params.stream().map(Object::toString).map("- "::concat).collect(joining("\n")), //
							br, //
							"rc\n" + javapResult.getResult(), //
							br, //
							scrubber.scrub(javapResult.getStdout()), //
							br, //
							javapResult.getStderr(), //
							br //
					) //
			);
		}
	}

	private static final String FIXED_SEED = "-1787866974758305853";

	@Property(seed = FIXED_SEED, shrinking = OFF, tries = 3)
	void storyboard(@ForAll("constructorParameters") List<ParameterConfig> params) throws Exception {
		settings().allowMultipleVerifyCallsForThisClass();
		settings().allowMultipleVerifyCallsForThisMethod();
		String checksum = ParameterConfig.stableChecksum(params);
		try (NamedEnvironment env = withParameters(checksum)) {
			verify(new Storyboad(params, dumpClassByteCode(generateClass(params, checksum))));
		}
	}

	@Value
	static class JavapResult {
		int result;
		String stdout;
		String stderr;
	}

	private static JavapResult dumpClassByteCode(Unloaded<Object> dynamicType) throws IOException {
		File tempFile = File.createTempFile("bytebuddy-generated", ".class");
		tempFile.deleteOnExit();
		try {
			Files.write(tempFile.toPath(), dynamicType.getBytes());
			try (ByteArrayOutputStream stdout = new ByteArrayOutputStream();
					ByteArrayOutputStream stderr = new ByteArrayOutputStream()) {
				int result = javap.run(new PrintStream(stdout), new PrintStream(stderr), "-c", "-p", "-v",
						tempFile.getAbsolutePath());
				stdout.close();
				stderr.close();
				return new JavapResult(result, lines(stdout), lines(stderr));
			}
		} finally {
			tempFile.delete();
		}
	}

	static String lines(ByteArrayOutputStream stdout) {
		return new String(stdout.toByteArray());
	}

}
