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

import static com.github.pfichtner.vaadoo.Buildable.a;
import static com.github.pfichtner.vaadoo.TestClassBuilder.testClass;
import static com.github.pfichtner.vaadoo.Transformer.newInstance;
import static com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy.config.VaadooConfigurationSupplier.VAADOO_CONFIG;
import static java.lang.Math.min;
import static java.lang.String.format;
import static java.lang.System.lineSeparator;
import static java.nio.file.Files.walk;
import static java.util.Collections.emptyList;
import static java.util.Comparator.comparing;
import static java.util.Comparator.reverseOrder;
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
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
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
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.approvaltests.ApprovalSettings;
import org.assertj.core.api.SoftAssertionsProvider.ThrowingRunnable;

import com.github.pfichtner.vaadoo.TestClassBuilder.ConstructorDefinition;
import com.github.pfichtner.vaadoo.TestClassBuilder.DefaultParameterDefinition;
import com.github.pfichtner.vaadoo.TestClassBuilder.ParameterDefinition;
import com.github.pfichtner.vaadoo.fragments.Jsr380CodeFragment;
import com.github.pfichtner.vaadoo.fragments.impl.GuavaCodeFragment;
import com.github.pfichtner.vaadoo.fragments.impl.GuavaCodeFragmentIAEMixin;

import net.bytebuddy.dynamic.DynamicType.Unloaded;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tuple;
import net.jqwik.api.arbitraries.ListArbitrary;

class Jsr380DynamicClassPBTest {

	static final Transformer transformer = new Transformer();

	static final Map<Class<?>, List<Class<?>>> SUPERTYPE_TO_SUBTYPES = Map.ofEntries(
			entry(Object.class, List.of(String.class, //
					Number.class, StringBuilder.class, StringBuffer.class, Collection.class, Map.class,
					Comparable.class)), //
			entry(CharSequence.class, List.of(String.class, StringBuilder.class, StringBuffer.class)), //
			entry(Collection.class, List.of(List.class, Set.class)), //
			entry(List.class, List.of(ArrayList.class, LinkedList.class)), //
			entry(Set.class, List.of(HashSet.class, LinkedHashSet.class)), //
			entry(Map.class, List.of(HashMap.class, TreeMap.class, LinkedHashMap.class)), //
			entry(Object[].class, List.of(String[].class, Object[].class, //
					Boolean[].class, Byte[].class, Short[].class, Integer[].class, Long[].class, Double[].class,
					Float[].class //
			)), //
			entry(Number.class,
					List.of(Byte.class, Short.class, Integer.class, Long.class, Double.class, Float.class,
							BigInteger.class, BigDecimal.class)), //
			entry(Comparable.class,
					List.of(String.class, Boolean.class, Number.class, Date.class, LocalDate.class,
							LocalDateTime.class)), //
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
	Arbitrary<List<ParameterDefinition>> constructorParameters() {
		return parameterConfigGen().list().ofMinSize(1).ofMaxSize(10);
	}

	Arbitrary<ParameterDefinition> parameterConfigGen() {
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
			return frequency.map(annos -> new DefaultParameterDefinition(type, annos));
		});
	}

	static List<Class<?>> resolveAllSubtypes(Class<?> base) {
		Set<Class<?>> result = new LinkedHashSet<>();
		Deque<Class<?>> stack = new ArrayDeque<>(List.of(base));
		while (!stack.isEmpty()) {
			Class<?> current = stack.pop();
			result.add(current);
			SUPERTYPE_TO_SUBTYPES.getOrDefault(current, emptyList()).stream().filter(result::add).forEach(stack::push);
		}
		return List.copyOf(result);
	}

	static ListArbitrary<Class<? extends Annotation>> uniquesOfMin(List<Class<? extends Annotation>> applicable,
			IntUnaryOperator cap, int i) {
		return Arbitraries.of(applicable).list().uniqueElements().ofSize(cap.applyAsInt(i));
	}

	@Property
	void canLoadClassAreCallConstructor(@ForAll("constructorParameters") List<ParameterDefinition> params)
			throws Exception {
		var unloaded = a(testClass("com.example.Generated").thatImplementsValueObject()
				.withConstructor(new ConstructorDefinition(params)));
		createInstances(params, unloaded);
	}

	@Property
	void throwsExceptionIfTypeIsNotSupportedByAnnotation(
			@ForAll("invalidParameterConfigs") List<ParameterDefinition> params) throws Exception {
		Assume.that(params.stream().map(ParameterDefinition::getAnnotations).anyMatch(not(List::isEmpty)));
		var unloaded = a(testClass("com.example.InvalidGenerated").thatImplementsValueObject()
				.withConstructor(new ConstructorDefinition(params)));
		newInstance(unloaded, args(params));
		assertThatIllegalStateException().isThrownBy(() -> transformer.transform(unloaded))
				.withMessageContaining("not allowed, allowed only on");
	}

	@Provide
	Arbitrary<List<ParameterDefinition>> invalidParameterConfigs() {
		return invalidParameterConfigGen().list().ofMinSize(1).ofMaxSize(10);
	}

	static Arbitrary<ParameterDefinition> invalidParameterConfigGen() {
		return Arbitraries.of(ANNO_TO_TYPES.keySet()).flatMap(a -> {
			var validTypes = ANNO_TO_TYPES.getOrDefault(a, emptyList()).stream()
					.flatMap(v -> resolveAllSubtypes(v).stream()).collect(toSet());
			var invalidTypes = SUPERTYPE_TO_SUBTYPES.keySet().stream()
					.filter(t -> validTypes.stream().noneMatch(v -> v.isAssignableFrom(t))).collect(toList());
			return invalidTypes.isEmpty() //
					? Arbitraries.of(new DefaultParameterDefinition(Object.class)) //
					: Arbitraries.of(invalidTypes).map(t -> new DefaultParameterDefinition(t, a));
		});
	}

	static void createInstances(List<ParameterDefinition> params, Unloaded<?> unloaded) throws Exception {
		Object[] args = args(params);
		newInstance(unloaded, args);
		try {
			newInstance(transformer.transform(unloaded), args);
		} catch (Exception e) {
			if (!isAnExpectedException(e)) {
				throw e;
			}
		}
	}

	static boolean isAnExpectedException(Exception exception) {
		return oks.stream().anyMatch(p -> p.test(exception));
	}

	static Predicate<Throwable> endsWith(Function<Throwable, String> mapper, String expectedMessage) {
		return t -> mapper.apply(t).endsWith(expectedMessage);
	}

	static Object[] args(List<ParameterDefinition> params) {
		return params.stream().map(ParameterDefinition::getType).map(Jsr380DynamicClassPBTest::getDefault).toArray();
	}

	// TODO use Arbitrary to generate value, e.g. null, "", "XXX" for CharSequence,
	// String, ...
	static Object getDefault(Class<?> clazz) {
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

	private static final String FIXED_SEED = "-1787866974758305853";

	static final List<Predicate<Throwable>> oks = List.of( //
			isNPE().and(endsWith(Throwable::getMessage, "must not be null")), //
			isIAE().and(endsWith(Throwable::getMessage, "must not be null")), //
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

	static Predicate<Throwable> isIAE() {
		return IllegalArgumentException.class::isInstance;
	}

	static Predicate<Throwable> isNPE() {
		return NullPointerException.class::isInstance;
	}

	@Property
	void withoutImplementingValueObject_noValidationCodeGetsAdded(
			@ForAll("constructorParameters") List<ParameterDefinition> params) throws Exception {
		var checksum = ParameterDefinition.stableChecksum(params);
		var testClass = a(testClass("com.example.Generated_" + checksum) //
				.withConstructor(new ConstructorDefinition(params)));
		var transformedClass = transformer.transform(testClass);
		newInstance(transformedClass, args(params));
	}

	@Property(seed = FIXED_SEED, shrinking = OFF, tries = 10)
	void implementsValueObject(@ForAll("constructorParameters") List<ParameterDefinition> params) throws Exception {
		var projectRoot = configure(keepJsr380Annotations());
		var approver = new Approver(new Transformer().projectRoot(projectRoot));
		ApprovalSettings settings = settings();
		settings.allowMultipleVerifyCallsForThisClass();
		settings.allowMultipleVerifyCallsForThisMethod();
		withProjectRoot(projectRoot, () -> approver.approveTransformed(params));
	}

	@Property(seed = FIXED_SEED, shrinking = OFF, tries = 10)
	void implementsValueObjectWithRemovedAnnos(@ForAll("constructorParameters") List<ParameterDefinition> params)
			throws Exception {
		new Approver(new Transformer()).approveTransformed(params);
	}

	@Property(seed = FIXED_SEED, shrinking = OFF, tries = 10)
	void implementsValueObjectWeavingInGuavaCode(@ForAll("constructorParameters") List<ParameterDefinition> params)
			throws Exception {
		var projectRoot = configure(useFragmentClass(GuavaCodeFragment.class), keepJsr380Annotations());
		var approver = new Approver(new Transformer().projectRoot(projectRoot));
		ApprovalSettings settings = settings();
		settings.allowMultipleVerifyCallsForThisClass();
		settings.allowMultipleVerifyCallsForThisMethod();
		withProjectRoot(projectRoot, () -> approver.approveTransformed(params));
	}

	@Property(seed = FIXED_SEED, shrinking = OFF, tries = 10)
	void implementsValueObjectWeavingInGuavaCodeWithRemovedAnnos(
			@ForAll("constructorParameters") List<ParameterDefinition> params) throws Exception {
		var projectRoot = configure(useFragmentClass(GuavaCodeFragment.class));
		var approver = new Approver(new Transformer().projectRoot(projectRoot));
		ApprovalSettings settings = settings();
		settings.allowMultipleVerifyCallsForThisClass();
		settings.allowMultipleVerifyCallsForThisMethod();
		withProjectRoot(projectRoot, () -> approver.approveTransformed(params));
	}

	@Property(seed = FIXED_SEED, shrinking = OFF, tries = 10)
	void implementsValueObjectWeavingInGuavaCodeWithIAEMixin(
			@ForAll("constructorParameters") List<ParameterDefinition> params) throws Exception {
		var projectRoot = configure(useFragmentClass(GuavaCodeFragment.class),
				useMixins(GuavaCodeFragmentIAEMixin.class));
		var approver = new Approver(new Transformer().projectRoot(projectRoot));
		ApprovalSettings settings = settings();
		settings.allowMultipleVerifyCallsForThisClass();
		settings.allowMultipleVerifyCallsForThisMethod();
		withProjectRoot(projectRoot, () -> approver.approveTransformed(params));
	}

	private static void withProjectRoot(File projectRoot, ThrowingRunnable runnable) throws Exception {
		try {
			runnable.run();
		} finally {
			try (var paths = walk(projectRoot.toPath())) {
				paths.sorted(reverseOrder()).map(Path::toFile).forEach(File::delete);
			}
			assert !projectRoot.exists();
		}
	}

	private static Entry<String, Object> useFragmentClass(Class<? extends Jsr380CodeFragment> fragmentClass) {
		return Map.entry("vaadoo.jsr380CodeFragmentClass", fragmentClass.getName());
	}

	private static Entry<String, Object> keepJsr380Annotations() {
		return Map.entry("vaadoo.removeJsr380Annotations", false);
	}

	private Entry<String, Object> useMixins(Class<?>... clazz) {
		return Map.entry("vaadoo.codeFragmentMixins", Arrays.stream(clazz).map(Class::getName).collect(joining(",")));
	}

	@SafeVarargs
	private static File configure(Entry<String, Object>... entries) throws IOException {
		File projectRoot = Files.createTempDirectory("project-root").toFile();
		new File(projectRoot, "target/classes").mkdirs();
		writeTo(new File(projectRoot, "pom.xml"), "");
		writeTo(new File(projectRoot, VAADOO_CONFIG), Map.ofEntries(entries));
		return projectRoot;
	}

	private static void writeTo(File file, Map<String, Object> data) throws IOException {
		writeTo(file, content(data));
	}

	private static String content(Map<String, Object> data) {
		return data.entrySet().stream() //
				.map(e -> format("%s=%s", e.getKey(), e.getValue())) //
				.collect(joining(lineSeparator()));
	}

	private static void writeTo(File file, String text) throws IOException {
		try (FileWriter writer = new FileWriter(file)) {
			writer.write(text);
		}
	}

}
