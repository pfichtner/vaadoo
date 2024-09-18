package com.github.pfichtner.vaadoo;

import static com.github.pfichtner.vaadoo.DynamicByteCodeTest.Config.config;
import static com.github.pfichtner.vaadoo.DynamicByteCodeTest.ConfigEntry.entry;
import static com.github.pfichtner.vaadoo.NumberWrapper.numberWrapper;
import static com.github.pfichtner.vaadoo.supplier.CharSequences.Type.BLANKS;
import static com.github.pfichtner.vaadoo.supplier.CharSequences.Type.NON_BLANKS;
import static com.github.pfichtner.vaadoo.supplier.Classes.SubTypes.ARRAYS;
import static com.github.pfichtner.vaadoo.supplier.Classes.SubTypes.CHARSEQUENCES;
import static com.github.pfichtner.vaadoo.supplier.Classes.SubTypes.COLLECTIONS;
import static com.github.pfichtner.vaadoo.supplier.Classes.SubTypes.MAPS;
import static com.github.pfichtner.vaadoo.supplier.Classes.SubTypes.NUMBERS;
import static com.github.pfichtner.vaadoo.supplier.Classes.SubTypes.WRAPPERS;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static java.util.UUID.randomUUID;
import static java.util.stream.Stream.concat;
import static java.util.stream.Stream.empty;
import static net.bytebuddy.description.modifier.Visibility.PUBLIC;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Stream;

import com.github.pfichtner.vaadoo.supplier.CharSequences;
import com.github.pfichtner.vaadoo.supplier.Classes;
import com.github.pfichtner.vaadoo.supplier.Primitives;
import com.github.pfichtner.vaadoo.supplier.TypeAndExample;
import com.google.common.base.Supplier;

import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.annotation.AnnotationDescription.Builder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder.MethodDefinition.ParameterDefinition.Annotatable;
import net.bytebuddy.dynamic.DynamicType.Unloaded;
import net.bytebuddy.dynamic.loading.ByteArrayClassLoader;
import net.bytebuddy.dynamic.loading.ByteArrayClassLoader.PersistenceHandler;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.MethodCall;
import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.WithNull;

/**
 * This is the base of all dynamic tests (we can check all the different types
 * easily)
 */
class DynamicByteCodeTest {

	public static record Config(List<ConfigEntry> entries) {

		public Config(List<ConfigEntry> entries) {
			this.entries = List.copyOf(entries);
		}

		public static Config config() {
			return new Config(emptyList());
		}

		public Config withEntry(ConfigEntry newEntry) {
			return new Config(concat(entries.stream(), Stream.of(newEntry)).toList());
		}

	}

	public static record ConfigEntry(Class<?> paramType, String name, Object value,
			Class<? extends Annotation> annoClass, Map<String, Object> annoValues) {
		public static <T> ConfigEntry entry(Class<T> paramType, String name, T value) {
			return new ConfigEntry(paramType, name, value, null, emptyMap());
		}

		public ConfigEntry withAnno(Class<? extends Annotation> annoClass) {
			return withAnno(annoClass, emptyMap());
		}

		public ConfigEntry withAnno(Class<? extends Annotation> annoClass, Map<String, Object> annoValues) {
			return new ConfigEntry(paramType(), name(), value(), annoClass, annoValues);
		}
	}

	// TODO add the moment the ByteBuddy generated code contains debug information,
	// we want to support (and test both): Bytecode with and w/o debug information
	AddJsr380ValidationPlugin sut = new AddJsr380ValidationPlugin();

	@Property
	void showcaseWithThreeParams( //
			@ForAll(supplier = CharSequences.class) //
			@CharSequences.Types(BLANKS) //
			String blank) //
			throws Exception {
		var config = config() //
				.withEntry(entry(String.class, "param1", blank).withAnno(NotNull.class)) //
				.withEntry(entry(String.class, "param2", blank).withAnno(NotBlank.class)) //
				.withEntry(entry(String.class, "param3", blank).withAnno(NotBlank.class));
		var transformed = transform(dynamicClass(config));
		assertException(config, transformed, "param2 must not be blank", IllegalArgumentException.class);
	}

	@Property
	void nullOks( //
			@ForAll(supplier = Classes.class) //
			TypeAndExample tuple) //
			throws Exception {
		Object nullValue = null;
		var config = randomConfigWith(entry(tuple.type(), "param", nullValue).withAnno(Null.class));
		var transformed = transform(dynamicClass(config));
		assertNoException(config, transformed);
	}

	@Property
	void nullNoks( //
			@ForAll(supplier = Classes.class) //
			TypeAndExample tuple) //
			throws Exception {
		var parameterName = "param";
		var config = randomConfigWith(entry(tuple.type(), parameterName, tuple.example()).withAnno(Null.class));
		var transformed = transform(dynamicClass(config));
		assertException(config, transformed, parameterName + " expected to be null", IllegalArgumentException.class);
	}

	@Property
	void notnullOks( //
			@ForAll(supplier = Classes.class) //
			TypeAndExample tuple) //
			throws Exception {
		var parameterName = "param";
		var config = randomConfigWith(entry(tuple.type(), parameterName, null).withAnno(NotNull.class));
		var transformed = transform(dynamicClass(config));
		assertException(config, transformed, parameterName + " must not be null", NullPointerException.class);
	}

	@Property
	void notnullNoks( //
			@ForAll(supplier = Classes.class) //
			TypeAndExample tuple) //
			throws Exception {
		var config = randomConfigWith(entry(tuple.type(), "param", tuple.example()).withAnno(NotNull.class));
		var transformed = transform(dynamicClass(config));
		assertNoException(config, transformed);
	}

	@Property
	void assertTruesPrimitives( //
			@ForAll(supplier = Primitives.class) //
			@Primitives.Types(boolean.class) //
			TypeAndExample tuple) //
			throws Exception {
		assertTrues(tuple);
	}

	@Property
	void assertTruesWrappers( //
			@ForAll(supplier = Classes.class) //
			@Classes.Types(value = WRAPPERS, ofType = Boolean.class) //
			TypeAndExample tuple) //
			throws Exception {
		assertTrues(tuple);
	}

	private void assertTrues(TypeAndExample tuple) throws Exception {
		var parameterName = "param";
		var value = (boolean) tuple.example();
		var config = randomConfigWith(
				entry(casted(tuple.type(), Boolean.class), parameterName, value).withAnno(AssertTrue.class));
		var transformed = transform(dynamicClass(config));

		var execResult = provideExecException(transformed, config);
		if (value) {
			assertNoException(execResult);
		} else {
			assertException(execResult, parameterName + " should be true", IllegalArgumentException.class);
		}
	}

	@Property
	void assertFalsesPrimitives( //
			@ForAll(supplier = Primitives.class) //
			@Primitives.Types(boolean.class) //
			TypeAndExample tuple) //
			throws Exception {
		assertFalses(tuple);
	}

	@Property
	void assertFalsesWrappers( //
			@ForAll(supplier = Classes.class) //
			@Classes.Types(value = WRAPPERS, ofType = Boolean.class) //
			TypeAndExample tuple) //
			throws Exception {
		assertFalses(tuple);
	}

	private void assertFalses(TypeAndExample tuple) throws Exception {
		var parameterName = "param";
		var value = (boolean) tuple.example();
		var config = randomConfigWith(
				entry(casted(tuple.type(), Boolean.class), parameterName, value).withAnno(AssertFalse.class));
		var transformed = transform(dynamicClass(config));

		var execResult = provideExecException(transformed, config);
		if (value) {
			assertException(execResult, parameterName + " should be false", IllegalArgumentException.class);
		} else {
			assertNoException(execResult);
		}
	}

	@Property
	void notBlankOks( //
			@ForAll(supplier = Classes.class) //
			@Classes.Types(CHARSEQUENCES) //
			TypeAndExample tuple, //
			@ForAll(supplier = CharSequences.class) //
			@CharSequences.Types(NON_BLANKS) //
			CharSequence nonBlank //
	) throws Exception {
		var config = randomConfigWith(entry(tuple.type(), "param", nonBlank).withAnno(NotBlank.class));
		var transformed = transform(dynamicClass(config));
		assertNoException(config, transformed);
	}

	@Property
	void notBlankNoks( //
			@ForAll(supplier = Classes.class) //
			@Classes.Types(CHARSEQUENCES) //
			TypeAndExample tuple, //
			@WithNull //
			@ForAll(supplier = CharSequences.class) //
			@CharSequences.Types(BLANKS) //
			CharSequence blank //
	) throws Exception {
		var parameterName = "param";
		boolean stringIsNull = blank == null;
		var config = randomConfigWith(
				entry(casted(tuple.type(), CharSequence.class), parameterName, blank).withAnno(NotBlank.class));
		var transformed = transform(dynamicClass(config));
		assertException(config, transformed, //
				parameterName + " must not be " + (stringIsNull ? "null" : "blank"),
				stringIsNull ? NullPointerException.class : IllegalArgumentException.class);
	}

	@Property
	void notEmptyOks( //
			@ForAll(supplier = Classes.class) //
			@Classes.Types({ CHARSEQUENCES, COLLECTIONS, MAPS, ARRAYS }) //
			TypeAndExample tuple //
	) throws Exception {
		var config = randomConfigWith(
				entry(tuple.type(), "param", convertValue(tuple.example(), false)).withAnno(NotEmpty.class));
		var transformed = transform(dynamicClass(config));
		assertNoException(config, transformed);
	}

	@Property
	void notEmptyNoks( //
			@ForAll(supplier = Classes.class) //
			@Classes.Types({ CHARSEQUENCES, COLLECTIONS, MAPS, ARRAYS }) //
			TypeAndExample tuple //
	) throws Exception {
		var parameterName = "param";
		var config = randomConfigWith(
				entry(tuple.type(), parameterName, convertValue(tuple.example(), true)).withAnno(NotEmpty.class));
		var transformed = transform(dynamicClass(config));
		assertException(config, transformed, parameterName + " must not be empty", IllegalArgumentException.class);
	}

	@Property
	void minValuesValueLowerThanMin( //
			@ForAll(supplier = Primitives.class) //
			@Primitives.Types({ int.class, long.class, short.class, byte.class }) //
			TypeAndExample tuple //
	) throws Exception {
		var parameterName = "param";
		var value = numberWrapper(tuple.type(), tuple.example());
		Assume.that(!value.isMin());

		@SuppressWarnings("unchecked")
		var config = randomConfigWith(entry(value.type(), parameterName, value.sub(1)).withAnno(Min.class,
				Map.of("value", value.flooredLong())));
		var transformed = transform(dynamicClass(config));
		assertException(config, transformed, parameterName + " should be >= " + value.flooredLong(),
				IllegalArgumentException.class);
	}

	@Property
	void minValuesValueEqualMinPrimitives( //
			@ForAll(supplier = Primitives.class) //
			@Primitives.Types({ int.class, long.class, short.class, byte.class }) //
			TypeAndExample tuple //
	) throws Exception {
		var parameterName = "param";
		var value = numberWrapper(tuple.type(), tuple.example());

		@SuppressWarnings("unchecked")
		var config = randomConfigWith(entry(value.type(), parameterName, value.value()).withAnno(Min.class,
				Map.of("value", value.flooredLong())));
		var transformed = transform(dynamicClass(config));
		assertNoException(config, transformed);
	}

	@Property
	void minValuesValueLowerThanMinObjects( //
			@ForAll(supplier = Classes.class) //
			@Classes.Types(value = NUMBERS) //
			TypeAndExample tuple //
	) throws Exception {
		var parameterName = "param";
		var value = numberWrapper(tuple.type(), tuple.example());
		Assume.that(!value.isMin());

		Number sub = value.sub(1);
		Assume.that(!upperBoundOutOfLongRange(sub));

		@SuppressWarnings("unchecked")
		var config = randomConfigWith(
				entry(value.type(), parameterName, sub).withAnno(Min.class, Map.of("value", value.flooredLong())));
		var transformed = transform(dynamicClass(config));
		assertException(config, transformed, parameterName + " should be >= " + value.flooredLong(),
				IllegalArgumentException.class);
	}

	@Property
	void minValuesValueEqualMinObjects( //
			@ForAll(supplier = Classes.class) //
			@Classes.Types(value = NUMBERS) //
			TypeAndExample tuple //
	) throws Exception {
		var parameterName = "param";
		var value = numberWrapper(tuple.type(), tuple.example());
		Assume.that(!lowerBoundOutOfLongRange(value.value()));

		@SuppressWarnings("unchecked")
		var config = randomConfigWith(entry(value.type(), parameterName, value.value()).withAnno(Min.class,
				Map.of("value", value.flooredLong())));
		var transformed = transform(dynamicClass(config));
		assertNoException(config, transformed);
	}

	@Property
	void minValuesValueNullObjectsAreOk( //
			@ForAll(supplier = Classes.class) //
			@Classes.Types(value = NUMBERS) //
			TypeAndExample tuple //
	) throws Exception {
		var parameterName = "param";
		Object nullValue = null;
		var value = numberWrapper(tuple.type(), tuple.example());

		@SuppressWarnings("unchecked")
		var config = randomConfigWith(entry(value.type(), parameterName, nullValue).withAnno(Min.class,
				Map.of("value", value.flooredLong())));
		var transformed = transform(dynamicClass(config));
		assertNoException(config, transformed);
	}

	@Property
	void minValuesValueGreaterThanMinObjects( //
			@ForAll(supplier = Classes.class) //
			@Classes.Types(value = NUMBERS) //
			TypeAndExample tuple //
	) throws Exception {
		var parameterName = "param";
		var value = numberWrapper(tuple.type(), tuple.example());
		Assume.that(!value.isMax());
		Assume.that(!lowerBoundOutOfLongRange(value.value()));

		@SuppressWarnings("unchecked")
		var config = randomConfigWith(entry(value.type(), parameterName, value.add(1)).withAnno(Min.class,
				Map.of("value", value.flooredLong())));
		var transformed = transform(dynamicClass(config));
		assertNoException(config, transformed);
	}

	@Property
	void minValuesValueGreaterThanMin( //
			@ForAll(supplier = Primitives.class) //
			@Primitives.Types({ int.class, long.class, short.class, byte.class }) //
			TypeAndExample tuple //
	) throws Exception {
		var parameterName = "param";
		var value = numberWrapper(tuple.type(), tuple.example());
		Assume.that(!value.isMax());

		@SuppressWarnings("unchecked")
		var config = randomConfigWith(entry(value.type(), parameterName, value.add(1)).withAnno(Min.class,
				Map.of("value", value.flooredLong())));
		var transformed = transform(dynamicClass(config));
		assertNoException(config, transformed);
	}

	static boolean lowerBoundOutOfLongRange(Number value) {
		return new BigDecimal(value.toString()).compareTo(new BigDecimal(Long.MIN_VALUE)) < 0;
	}

	static boolean upperBoundOutOfLongRange(Number value) {
		return new BigDecimal(value.toString()).compareTo(new BigDecimal(Long.MAX_VALUE)) > 0;
	}

	private Config randomConfigWith(ConfigEntry entry) {
		SecureRandom random = new SecureRandom();
		// TODO use random classes
		Supplier<ConfigEntry> supplier = () -> entry(Object.class, "param" + randomUUID().toString().replace("-", "_"),
				randomUUID());
		return new Config(Stream.of( //
				Stream.generate(supplier).limit(random.nextInt(5)), //
				Stream.of(entry), //
				Stream.generate(supplier).limit(random.nextInt(5)) //
		).reduce(empty(), Stream::concat).toList());
	}

	@SuppressWarnings("unchecked")
	private static Object convertValue(Object in, boolean empty) {
		Object result = in;
		if (empty) {
			if (result instanceof CharSequence) {
				result = "";
			} else if (result instanceof Collection collection) {
				collection.clear();
			} else if (result instanceof Map map) {
				map.clear();
			} else if (result.getClass().isArray()) {
				result = Array.newInstance(result.getClass().getComponentType(), 0);
			}
		} else {
			if (result instanceof CharSequence) {
				result = " ";
			} else if (result instanceof Collection collection) {
				collection.add(new Object());
			} else if (result instanceof Map map) {
				map.put(new Object(), new Object());
			} else if (result.getClass().isArray()) {
				// empty but of size "one"
				result = Array.newInstance(result.getClass().getComponentType(), 1);
			}
		}
		return result;
	}

	@SuppressWarnings("unchecked")
	private static <T> Class<T> casted(Class<?> clazzArg, Class<T> target) {
		return (Class<T>) clazzArg;
	}

	private static void assertException(Config config, Class<?> transformed, String description,
			Class<? extends Exception> type) throws Exception {
		assertException(provideExecException(transformed, config), description, type);
	}

	private static void assertException(Optional<Throwable> provideExecException, String description,
			Class<? extends Exception> type) {
		assertThat(provideExecException) //
				.withFailMessage("expected to throw exception but didn't") //
				.hasValueSatisfying(e -> assertThat(e).isExactlyInstanceOf(type).hasMessageContaining(description));
	}

	private void assertNoException(Config config, Class<?> transformed) throws Exception {
		assertThat(provideExecException(transformed, config)).isEmpty();
	}

	private void assertNoException(Optional<Throwable> execResult) {
		assertThat(execResult).isEmpty();
	}

	private static Optional<Throwable> provideExecException(Class<?> dynamicClass, Config config) throws Exception {
		var constructor = dynamicClass.getDeclaredConstructor(types(config.entries));
		try {
			constructor.newInstance(params(config.entries));
			return Optional.empty();
		} catch (InvocationTargetException e) {
			return Optional.of(e.getCause());
		}
	}

	private static Class<?>[] types(List<ConfigEntry> values) {
		return values.stream().map(ConfigEntry::paramType).toArray(Class[]::new);
	}

	private static Object[] params(List<ConfigEntry> values) {
		return values.stream().map(DynamicByteCodeTest::castToTargetType).toArray();
	}

	private static Object castToTargetType(ConfigEntry entry) {
		return entry.paramType.isPrimitive() ? primCast(entry) : objCast(entry);
	}

	private static Object primCast(ConfigEntry entry) {
		return entry.paramType == boolean.class //
				? (Boolean) entry.value() //
				: numberWrapper(entry.paramType(), entry.value()).value();
	}

	private static Object objCast(ConfigEntry entry) {
		return entry.paramType().cast(entry.value());
	}

	private Unloaded<Object> dynamicClass(Config config) throws NoSuchMethodException {
		return dynamicClass("com.example.GeneratedTestClass", config.entries);
	}

	private static Unloaded<Object> dynamicClass(String name, List<ConfigEntry> values) throws NoSuchMethodException {
		var builder = new ByteBuddy().subclass(Object.class).name(name).defineConstructor(PUBLIC);

		Annotatable<Object> inner = null;
		for (ConfigEntry value : values) {
			inner = inner == null ? builder.withParameter(value.paramType, value.name())
					: inner.withParameter(value.paramType, value.name());
			if (value.annoClass != null) {
				Builder annoBuilder = AnnotationDescription.Builder.ofType(value.annoClass);

				for (Entry<String, Object> annoValue : value.annoValues.entrySet()) {
					// TODO at the moment only longs are supported, add accordingly
					long longVal = (long) annoValue.getValue();
					annoBuilder = annoBuilder.define(annoValue.getKey(), longVal);
				}

				inner = inner.annotateParameter(annoBuilder.build());
			}
		}

		var builderSuf = inner == null ? builder : inner;
		return builderSuf.intercept(MethodCall.invoke(Object.class.getConstructor())).make();
	}

	private Class<?> transform(Unloaded<Object> dynamicClass) throws NoSuchMethodException, ClassNotFoundException {
		var name = dynamicClass.getTypeDescription().getName();
		var originalClassLoader = sut.getClass().getClassLoader();

		var loadedClass = new ByteArrayClassLoader(originalClassLoader, singletonMap(name, dynamicClass.getBytes()),
				PersistenceHandler.MANIFEST).loadClass(name);
		var builder = new ByteBuddy().redefine(loadedClass);
		var transformed = sut.apply(builder, TypeDescription.ForLoadedType.of(loadedClass), null).make();
		var transformedClassLoader = new ByteArrayClassLoader(originalClassLoader, emptyMap(),
				PersistenceHandler.MANIFEST);
		return transformed.load(transformedClassLoader, ClassLoadingStrategy.Default.WRAPPER).getLoaded();
	}

}
