package com.github.pfichtner.vaadoo;

import static com.github.pfichtner.vaadoo.Decompiler.decompile;
import static java.lang.Math.min;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static net.jqwik.api.ShrinkingMode.OFF;
import static org.approvaltests.Approvals.settings;
import static org.approvaltests.Approvals.verify;
import static org.approvaltests.namer.NamerFactory.withParameters;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;

import org.approvaltests.core.Options;
import org.approvaltests.core.Scrubber;
import org.approvaltests.namer.NamedEnvironment;
import org.approvaltests.reporters.AutoApproveWhenEmptyReporter;
import org.approvaltests.scrubbers.RegExScrubber;
import org.lambda.functions.Function1;

import com.github.pfichtner.vaadoo.TestClassBuilder.MethodConfig;
import com.github.pfichtner.vaadoo.TestClassBuilder.ParameterConfig;
import com.github.pfichtner.vaadoo.fragments.Jsr380CodeFragment;
import com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy.JMoleculesPlugin;

import jakarta.validation.constraints.NotNull;
import lombok.Value;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.build.Plugin.WithPreprocessor;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.DynamicType.Unloaded;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tuple;

// TODO create arguments that are compatible, e.g. List, Set for Collection, String[], Integer[], Foo[] for Object[], e.g. 
class Jsr380DynamicClassTest {

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

	@Provide
	Arbitrary<List<ParameterConfig>> constructorParameters() {
		return parameterConfigGen().list().ofMinSize(1).ofMaxSize(10);
	}

	private Arbitrary<ParameterConfig> parameterConfigGen() {
		Arbitrary<Class<?>> typeGen = Arbitraries.of(ALL_SUPPORTED_TYPES);
		return typeGen.flatMap(type -> {
			@SuppressWarnings("unchecked")
			List<Class<? extends Annotation>> applicable = ANNO_TO_TYPES.entrySet().stream()
					.sorted(Map.Entry.comparingByKey(comparing(Class::getName)))
					.filter(e -> e.getValue().stream().anyMatch(vt -> vt.isAssignableFrom(type) || vt.equals(type)))
					.map(e -> (Class<? extends Annotation>) e.getKey()).collect(toList());

			int maxSize = applicable.size();
			IntUnaryOperator cap = n -> min(n, maxSize);
			Arbitrary<List<Class<? extends Annotation>>> frequency = Arbitraries.frequency(
					Tuple.of(15, Arbitraries.of(applicable).list().uniqueElements().ofSize(cap.applyAsInt(0))),
					Tuple.of(30, Arbitraries.of(applicable).list().uniqueElements().ofSize(cap.applyAsInt(1))),
					Tuple.of(30, Arbitraries.of(applicable).list().uniqueElements().ofSize(cap.applyAsInt(2))),
					Tuple.of(20, Arbitraries.of(applicable).list().uniqueElements().ofSize(cap.applyAsInt(3))),
					Tuple.of(5, Arbitraries.of(applicable).list().uniqueElements().ofMinSize(cap.applyAsInt(4))
							.ofMaxSize(maxSize)))
					.flatMap(Function.identity());
			return frequency.map(annos -> new ParameterConfig(type, annos));
		});
	}

	private Unloaded<Object> transformedClass(File outputFolder, DynamicType unloaded) throws Exception {
		try (WithPreprocessor plugin = new JMoleculesPlugin(outputFolder)) {
			TypeDescription typeDescription = unloaded.getTypeDescription();
			ClassFileLocator locator = ClassFileLocator.Simple.of(typeDescription.getName(), unloaded.getBytes());
			plugin.onPreprocess(typeDescription, locator);

			var byteBuddy = new ByteBuddy();
			var builder = byteBuddy.rebase(unloaded.getTypeDescription(), locator);
			var transformedBuilder = plugin.apply(builder, typeDescription, locator);

			@SuppressWarnings("unchecked")
			DynamicType.Unloaded<Object> transformed = (DynamicType.Unloaded<Object>) transformedBuilder.make();
			return transformed;
		}
	}

	@Property(tries = 10)
	void camLoadClassAreCallConstructor(@ForAll("constructorParameters") List<ParameterConfig> params)
			throws Exception {
		String random = UUID.randomUUID().toString().replace("-", "");
		TestClassBuilder tcb = new TestClassBuilder("com.example.Generated_" + random);
		Class<?> generated = tcb.constructors(params).generateClass()
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
			List<Class<?>> actualAnnos = Arrays.stream(paramAnnos[i]).map(Annotation::annotationType).collect(toList());

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
	static class Storyboard {
		List<ParameterConfig> params;
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

	private static final String FIXED_SEED = "-1787866974758305853";

	@Example
	void noArg() throws Exception {
		List<ParameterConfig> params = emptyList();
		String checksum = ParameterConfig.stableChecksum(params);
		try (NamedEnvironment env = withParameters(checksum)) {
			Unloaded<Object> testClass = new TestClassBuilder("com.example.Generated_" + checksum).constructors(params)
					.generateClass();
			approve(params, testClass);
		}
	}

	@Property(seed = FIXED_SEED, shrinking = OFF, tries = 10)
	void storyboard(@ForAll("constructorParameters") List<ParameterConfig> params) throws Exception {
		settings().allowMultipleVerifyCallsForThisClass();
		settings().allowMultipleVerifyCallsForThisMethod();
		String checksum = ParameterConfig.stableChecksum(params);
		try (NamedEnvironment env = withParameters(checksum)) {
			Unloaded<Object> testClass = new TestClassBuilder("com.example.Generated_" + checksum).constructors(params)
					.generateClass();
			approve(params, testClass);
		}
	}

	@Example
	void alreadyHasValidateMethod() throws Exception {
		List<ParameterConfig> params = List.of(new ParameterConfig(Object.class, List.of(NotNull.class)));
		String checksum = ParameterConfig.stableChecksum(params);
		Unloaded<Object> testClass = new TestClassBuilder("com.example.Generated_" + checksum).constructors(params)
				.method(new MethodConfig("validate", emptyList())).generateClass();
		approve(params, testClass);
	}

	private void approve(List<ParameterConfig> params, Unloaded<Object> generatedClass) throws Exception {
		Scrubber scrubber = new RegExScrubber("auxiliary\\.\\S+\\s+\\S+[),]",
				(Function1<Integer, String>) i -> format("auxiliary.[AUX1_%d AUX1_%d]", i, i));
		Options options = new Options().withScrubber(scrubber).withReporter(new AutoApproveWhenEmptyReporter());
		Unloaded<Object> transformedClass = transformedClass(dummyRoot(), generatedClass);
		verify(new Storyboard(params, decompile(generatedClass.getBytes()), decompile(transformedClass.getBytes())),
				options);
	}

	private File dummyRoot() {
		return new File("jmolecules-bytebuddy-tests");
	}

}
