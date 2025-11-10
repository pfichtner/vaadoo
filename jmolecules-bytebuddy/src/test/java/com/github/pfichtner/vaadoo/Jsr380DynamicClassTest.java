package com.github.pfichtner.vaadoo;

import static java.lang.Math.min;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Comparator.comparing;
import static java.util.Map.entry;
import static java.util.Map.Entry.comparingByKey;
import static java.util.function.Function.identity;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static net.jqwik.api.ShrinkingMode.OFF;
import static org.approvaltests.Approvals.settings;
import static org.approvaltests.Approvals.verify;
import static org.approvaltests.namer.NamerFactory.withParameters;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.approvaltests.core.Options;
import org.approvaltests.core.Scrubber;
import org.approvaltests.namer.NamedEnvironment;
import org.approvaltests.reporters.AutoApproveWhenEmptyReporter;
import org.approvaltests.scrubbers.RegExScrubber;

import com.github.pfichtner.vaadoo.TestClassBuilder.ConstructorConfig;
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
import net.jqwik.api.Assume;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tuple;
import net.jqwik.api.arbitraries.ListArbitrary;

class Jsr380DynamicClassTest {

	static final boolean DUMP_CLASS_FILES_TO_TEMP = false;

	static final Map<Class<?>, List<Class<?>>> SUPERTYPE_TO_SUBTYPES = Map.ofEntries(
			entry(Object.class,
					List.of(String.class, StringBuilder.class, StringBuffer.class, Number.class, Collection.class,
							Map.class, Comparable.class)), //
			entry(CharSequence.class, List.of(String.class, StringBuilder.class, StringBuffer.class)), //
			entry(Collection.class, List.of(List.class, Set.class)), //
			entry(List.class, List.of(ArrayList.class, LinkedList.class)), //
			entry(Set.class, List.of(HashSet.class, LinkedHashSet.class)), //
			entry(Map.class, List.of(HashMap.class, TreeMap.class, LinkedHashMap.class)), //
			entry(Object[].class, List.of(String[].class, Integer[].class, Long[].class, Object[].class)), //
			entry(Number.class, List.of(Integer.class, Long.class, Double.class, Float.class, BigDecimal.class)), //
			entry(Comparable.class, List.of(String.class, Integer.class, LocalDate.class, LocalDateTime.class)), //
			entry(Date.class,
					List.of(java.sql.Date.class, java.sql.Timestamp.class, Calendar.class, LocalDate.class,
							LocalDateTime.class, Instant.class, OffsetDateTime.class, ZonedDateTime.class)),
			entry(LocalDate.class, List.of(LocalDateTime.class)), //
			entry(LocalDateTime.class, List.of(OffsetDateTime.class, ZonedDateTime.class)) //
	);

	static final Map<Class<? extends Annotation>, List<Class<?>>> ANNO_TO_TYPES = //
			Stream.of(Jsr380CodeFragment.class.getMethods()) //
					.sorted(comparing(Method::getName)) //
					.collect(groupingBy( //
							m -> asAnnotation(m.getParameterTypes()[0]), //
							mapping(m -> m.getParameterTypes()[1], toList()) //
					));

	@SuppressWarnings("unchecked")
	static Class<? extends Annotation> asAnnotation(Class<?> clazz) {
		return (Class<? extends Annotation>) clazz;
	}

	static final List<Class<?>> ALL_SUPPORTED_TYPES = ANNO_TO_TYPES.values().stream() //
			.flatMap(List::stream) //
			.distinct() //
			.sorted(comparing(Class::getName)) //
			.collect(toList());

	@Provide
	Arbitrary<List<ParameterConfig>> constructorParameters() {
		return parameterConfigGen().list().ofMinSize(1).ofMaxSize(10);
	}

	private Arbitrary<ParameterConfig> parameterConfigGen() {
		Arbitrary<Class<?>> typeGen = Arbitraries.of(ALL_SUPPORTED_TYPES)
				.flatMap(baseType -> Arbitraries.of(resolveAllSubtypes(baseType)));
		return typeGen.flatMap(type -> {
			List<Class<? extends Annotation>> applicable = ANNO_TO_TYPES.entrySet().stream()
					.sorted(comparingByKey(comparing(Class::getName))) //
					.filter(e -> e.getValue().stream().anyMatch(vt -> vt.isAssignableFrom(type))) //
					.map(e -> (Class<? extends Annotation>) e.getKey()) //
					.collect(toList());

			int maxSize = applicable.size();
			IntUnaryOperator cap = n -> min(n, maxSize);
			Arbitrary<List<Class<? extends Annotation>>> frequency = Arbitraries.frequency( //
					Tuple.of(15, uniquesOfMin(applicable, cap, 0)), //
					Tuple.of(30, uniquesOfMin(applicable, cap, 1)), //
					Tuple.of(30, uniquesOfMin(applicable, cap, 2)), //
					Tuple.of(20, uniquesOfMin(applicable, cap, 3)), //
					Tuple.of(5, uniquesOfMin(applicable, cap, 4).ofMaxSize(maxSize))) //
					.flatMap(identity());
			return frequency.map(annos -> new ParameterConfig(type, annos));
		});
	}

	private static List<Class<?>> resolveAllSubtypes(Class<?> base) {
		Set<Class<?>> result = new LinkedHashSet<>();
		Deque<Class<?>> stack = new ArrayDeque<>(List.of(base));
		while (!stack.isEmpty()) {
			Class<?> current = stack.pop();
			result.add(current);
			SUPERTYPE_TO_SUBTYPES.getOrDefault(current, emptyList()).stream().filter(result::add).forEach(stack::push);
		}
		return List.copyOf(result);
	}

	private static ListArbitrary<Class<? extends Annotation>> uniquesOfMin(List<Class<? extends Annotation>> applicable,
			IntUnaryOperator cap, int i) {
		return Arbitraries.of(applicable).list().uniqueElements().ofSize(cap.applyAsInt(i));
	}

	private Unloaded<Object> transformClass(DynamicType unloaded) throws Exception {
		try (WithPreprocessor plugin = new JMoleculesPlugin(dummyRoot())) {
			TypeDescription typeDescription = unloaded.getTypeDescription();
			ClassFileLocator locator = ClassFileLocator.Simple.of(typeDescription.getName(), unloaded.getBytes());
			plugin.onPreprocess(typeDescription, locator);

			var byteBuddy = new ByteBuddy();
			var builder = byteBuddy.rebase(unloaded.getTypeDescription(), locator);
			var transformedBuilder = plugin.apply(builder, typeDescription, locator);

			@SuppressWarnings("unchecked")
			DynamicType.Unloaded<Object> transformed = (DynamicType.Unloaded<Object>) transformedBuilder.make();
			if (DUMP_CLASS_FILES_TO_TEMP) {
				transformed.saveIn(Files.createTempDirectory("generated-class").toFile());
			}

			return transformed;
		}
	}

	@Property
	void canLoadClassAreCallConstructor(@ForAll("constructorParameters") List<ParameterConfig> params)
			throws Exception {
		Unloaded<Object> unloaded = new TestClassBuilder("com.example.Generated")
				.constructor(new ConstructorConfig(params)).make();
		createInstances(params, unloaded);
	}

	@Property
	void throwsExceptionIfTypeIsNotSupportedByAnnotation(
			@ForAll("invalidParameterConfigs") List<ParameterConfig> params) throws Exception {
		Assume.that(params.stream().map(ParameterConfig::getAnnotations).anyMatch(not(List::isEmpty)));
		Unloaded<Object> unloaded = new TestClassBuilder("com.example.InvalidGenerated")
				.constructor(new ConstructorConfig(params)).make();
		newInstance(unloaded, args(params));
		assertThat(assertThrows(IllegalStateException.class, () -> transformClass(unloaded)).getMessage())
				.contains("not allowed, allowed only on");
	}

	@Provide
	private Arbitrary<List<ParameterConfig>> invalidParameterConfigs() {
		return invalidParameterConfigGen().list().ofMinSize(1).ofMaxSize(10);
	}

	private Arbitrary<ParameterConfig> invalidParameterConfigGen() {
		return Arbitraries.of(ANNO_TO_TYPES.keySet()).flatMap(a -> {
			Set<Class<?>> validTypes = ANNO_TO_TYPES.getOrDefault(a, emptyList()).stream()
					.flatMap(v -> resolveAllSubtypes(v).stream()).collect(toSet());

			List<Class<?>> invalidTypes = SUPERTYPE_TO_SUBTYPES.keySet().stream()
					.filter(t -> validTypes.stream().noneMatch(v -> v.isAssignableFrom(t))).collect(toList());

			if (invalidTypes.isEmpty()) {
				return Arbitraries.of(new ParameterConfig(Object.class));
			}

			return Arbitraries.of(invalidTypes).map(t -> new ParameterConfig(t, a));
		});
	}

	private void createInstances(List<ParameterConfig> params, Unloaded<Object> unloaded) throws Exception {
		Object[] args = args(params);
		newInstance(unloaded, args);
		try {
			newInstance(transformClass(unloaded), args);
		} catch (InvocationTargetException e) {
			if (!isAnExpectedException(e)) {
				throw e;
			}
		}
	}

	private boolean isAnExpectedException(InvocationTargetException e) {
		return oks.stream().map(p -> p.test(e.getCause())).anyMatch(Boolean.TRUE::equals);
	}

	private static Predicate<Throwable> endsWith(Function<Throwable, String> mapper, String expectedMessage) {
		return t -> mapper.apply(t).endsWith(expectedMessage);
	}

	private static Object newInstance(Unloaded<Object> unloaded, Object[] args)
			throws InstantiationException, IllegalAccessException, InvocationTargetException, Exception {
		Class<?> clazz = unloaded.load(new ClassLoader() {
		}, ClassLoadingStrategy.Default.INJECTION).getLoaded();
		Constructor<?> constructor = clazz.getDeclaredConstructors()[0];
		return constructor.newInstance(args);
	}

	private static Object[] args(List<ParameterConfig> params) {
		return params.stream().map(ParameterConfig::getType).map(Jsr380DynamicClassTest::getDefault).toArray();
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

	private static final List<Predicate<Throwable>> oks = List.of( //
			isNPE().and(endsWith(Throwable::getMessage, "must not be null")), //
			isNPE().and(endsWith(Throwable::getMessage, "must not be empty")), //
			isNPE().and(endsWith(Throwable::getMessage, "must not be blank")), //
			isIAE().and(endsWith(Throwable::getMessage, "must be null")), //
			isIAE().and(endsWith(Throwable::getMessage, "must not be empty")), //
			isIAE().and(endsWith(Throwable::getMessage, "must not be blank")), //
			isIAE().and(endsWith(Throwable::getMessage, "must be greater than 0")), //
			isIAE().and(endsWith(Throwable::getMessage, "must be less than 0")), //
			isIAE().and(endsWith(Throwable::getMessage, "must be true")), //
			isIAE().and(endsWith(Throwable::getMessage, "must be false")), //
			isIAE().and(
					endsWith(Throwable::getMessage, "numeric value out of bounds (<0 digits>.<0 digits> expected)")), //
			isIAE().and(endsWith(Throwable::getMessage, "must be a future date")), //
			isIAE().and(endsWith(Throwable::getMessage, "must be a date in the present or in the future")), //
			isIAE().and(endsWith(Throwable::getMessage, "must be a past date")) //
	);

	private static Predicate<Throwable> isIAE() {
		return IllegalArgumentException.class::isInstance;
	}

	private static Predicate<Throwable> isNPE() {
		return NullPointerException.class::isInstance;
	}

	@Example
	void noArg() throws Exception {
		List<ParameterConfig> noParams = emptyList();
		Unloaded<Object> testClass = new TestClassBuilder("com.example.Generated")
				.constructor(new ConstructorConfig(noParams)).make();
		approve(noParams, testClass);
	}

	@Property(seed = FIXED_SEED, shrinking = OFF, tries = 10)
	void storyboard(@ForAll("constructorParameters") List<ParameterConfig> params) throws Exception {
		settings().allowMultipleVerifyCallsForThisClass();
		settings().allowMultipleVerifyCallsForThisMethod();
		String checksum = ParameterConfig.stableChecksum(params);
		try (NamedEnvironment env = withParameters(checksum)) {
			Unloaded<Object> testClass = new TestClassBuilder("com.example.Generated_" + checksum)
					.constructor(new ConstructorConfig(params)).make();
			approve(params, testClass);
		}
	}

	@Example
	void alreadyHasValidateMethod() throws Exception {
		List<ParameterConfig> params = List.of(new ParameterConfig(Object.class, List.of(NotNull.class)));
		Unloaded<Object> testClass = new TestClassBuilder("com.example.Generated")
				.constructor(new ConstructorConfig(params)).method(new MethodConfig("validate", emptyList())).make();
		approve(params, testClass);
	}

	private void approve(List<ParameterConfig> params, Unloaded<Object> generatedClass) throws Exception {
		Unloaded<Object> transformedClass = transformClass(generatedClass);
		verify(new Storyboard(params, decompile(generatedClass), decompile(transformedClass)), options());
	}

	private static Options options() {
		return new Options().withScrubber(scrubber()).withReporter(new AutoApproveWhenEmptyReporter());
	}

	private static Scrubber scrubber() {
		return new RegExScrubber("auxiliary\\.\\S+\\s+\\S+[),]", i -> format("auxiliary.[AUX1_%d AUX1_%d]", i, i));
	}

	private static String decompile(Unloaded<Object> clazz) throws IOException {
		return Decompiler.decompile(clazz.getBytes());
	}

	private static File dummyRoot() {
		return new File("jmolecules-bytebuddy-tests");
	}

}
