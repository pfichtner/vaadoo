package com.github.pfichtner.vaadoo;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static java.util.stream.Stream.concat;
import static net.bytebuddy.description.modifier.Visibility.PUBLIC;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.github.pfichtner.vaadoo.supplier.Blanks;
import com.github.pfichtner.vaadoo.supplier.CharSequenceClasses;
import com.github.pfichtner.vaadoo.supplier.NonBlanks;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder.MethodDefinition.ParameterDefinition.Annotatable;
import net.bytebuddy.dynamic.DynamicType.Unloaded;
import net.bytebuddy.dynamic.loading.ByteArrayClassLoader;
import net.bytebuddy.dynamic.loading.ByteArrayClassLoader.PersistenceHandler;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.MethodCall;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.WithNull;

/**
 * This is the base of all dynamic tests (we can check all the different types
 * easily)
 */
class DynamicByteCodeTest {

	static record Config(List<ConfigEntry> entries) {
		private static Config config() {
			return new Config(Collections.emptyList());
		}

		public <T> Config withEntry(Class<T> paramType, String name, T value, Class<? extends Annotation> annoClass) {
			var newEntry = new ConfigEntry(paramType, name, value, annoClass);
			return new Config(concat(entries.stream(), Stream.of(newEntry)).toList());
		}
	}

	static record ConfigEntry(Class<?> paramType, String name, Object value, Class<? extends Annotation> annoClass) {
	}

	// TODO add the moment the ByteBuddy generated code contains debug information,
	// we want to support (and test both): Bytecode with and w/o debug information
	AddJsr380ValidationPlugin sut = new AddJsr380ValidationPlugin();

	@Property
	void showcaseWithThreeParams( //
			@ForAll(supplier = CharSequenceClasses.class) Class<?> clazz1, //
			@ForAll(supplier = CharSequenceClasses.class) Class<?> clazz2, //
			@ForAll(supplier = CharSequenceClasses.class) Class<?> clazz3, //
			@ForAll(supplier = Blanks.class) String blank //
	) throws Exception {
		var config = Config.config() //
				.withEntry(casted(clazz1, CharSequence.class), "parameter1", blank, NotNull.class) //
				.withEntry(casted(clazz2, CharSequence.class), "parameter2", blank, NotBlank.class) //
				.withEntry(casted(clazz3, CharSequence.class), "parameter3", blank, NotBlank.class);
		var transformedClass = transform(dynamicClass(config));
		assertException(config, transformedClass, //
				"parameter2 must not be blank", IllegalArgumentException.class);
	}

	@Property
	void notBlankOks( //
			@ForAll(supplier = CharSequenceClasses.class) Class<?> clazz, //
			@ForAll(supplier = NonBlanks.class) String nonBlank //
	) throws Exception {
		var config = Config.config().withEntry(casted(clazz, CharSequence.class), "param", nonBlank, NotBlank.class);
		var transformedClass = transform(dynamicClass(config));
		assertNoException(config, transformedClass);
	}

	@Property
	void notBlankNoks( //
			@ForAll(supplier = CharSequenceClasses.class) Class<?> clazz, //
			@WithNull @ForAll(supplier = Blanks.class) String blankString //
	) throws Exception {
		String parameterName = "param";
		boolean stringIsNull = blankString == null;
		var config = Config.config() //
				.withEntry(casted(clazz, CharSequence.class), parameterName, blankString, NotBlank.class);
		var transformedClass = transform(dynamicClass(config));
		assertException(config, transformedClass, //
				parameterName + " must not be " + (stringIsNull ? "null" : "blank"),
				stringIsNull ? NullPointerException.class : IllegalArgumentException.class);
	}

	@SuppressWarnings("unchecked")
	private static <T> Class<T> casted(Class<?> clazzArg, Class<T> target) {
		return (Class<T>) clazzArg;
	}

	private static void assertException(Config config, Class<?> transformedClass, String description,
			Class<? extends Exception> type) throws Exception {
		assertThat(provideExecException(transformedClass, config)) //
				.withFailMessage("expected to throw exception but didn't") //
				.hasValueSatisfying(e -> assertThat(e).isExactlyInstanceOf(type).hasMessageContaining(description));
	}

	private void assertNoException(Config config, Class<?> transformedClass) throws Exception {
		assertThat(provideExecException(transformedClass, config)).isEmpty();
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
		return values.stream().map(ConfigEntry::value).toArray();
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
			inner = value.annoClass == null //
					? inner //
					: inner.annotateParameter(AnnotationDescription.Builder.ofType(value.annoClass).build());
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