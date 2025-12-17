package com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy.config;

import static com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy.config.PropertiesVaadooConfiguration.VAADOO_CUSTOM_ANNOTATIONS;
import static com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy.config.PropertiesVaadooConfiguration.VAADOO_JSR380_CODE_FRAGMENT_CLASS;
import static com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy.config.PropertiesVaadooConfiguration.VAADOO_JSR380_CODE_FRAGMENT_TYPE;
import static com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy.config.PropertiesVaadooConfiguration.*;
import static com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy.config.PropertiesVaadooConfiguration.VAADOO_REGEX_OPTIMIZATION;
import static com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy.config.PropertiesVaadooConfiguration.VAADOO_REMOVE_JSR380_ANNOTATIONS;
import static java.util.stream.Stream.concat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.github.pfichtner.vaadoo.fragments.Jsr380CodeFragment;
import com.github.pfichtner.vaadoo.fragments.impl.GuavaCodeFragment;
import com.github.pfichtner.vaadoo.fragments.impl.GuavaCodeFragmentIAEMixin;
import com.github.pfichtner.vaadoo.fragments.impl.JdkOnlyCodeFragment;
import com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy.config.VaadooConfiguration.KnownFragmentClass;

class PropertiesVaadooConfigurationTest {

	private static final Class<? extends Jsr380CodeFragment> defaultFragmentClass = JdkOnlyCodeFragment.class;

	Properties properties = new Properties();
	PropertiesVaadooConfiguration sut = new PropertiesVaadooConfiguration(properties);

	@Test
	void ifNoFragmentClassAndNoFragmentTypeIsSetThenJdkOnlyFragmentIsReturned() {
		assertThat(sut.jsr380CodeFragmentClass()).isEqualTo(defaultFragmentClass);
	}

	@Test
	void ifFragmentTypeIsSetThenItIsReturned() {
		useFragmentType(KnownFragmentClass.GUAVA.name());
		assertThat(sut.jsr380CodeFragmentClass()).isEqualTo(GuavaCodeFragment.class);
	}

	@Test
	void ifFragmentTypeIsInvalidValueThenDefaultFragmentClassIsReturned() {
		useFragmentType("XXXXX-INVALID-XXXX");
		assertThat(sut.jsr380CodeFragmentClass()).isEqualTo(defaultFragmentClass);
	}

	@ParameterizedTest
	@MethodSource("knownFragmentClassNames")
	void ifFragmentClassIsSetItIsUsedAndFragmenTypeDoesNotMatter(String knownFragmentClassName) {
		var dummyFragmentClass = dummyFragmentClass();
		if (knownFragmentClassName != null) {
			useFragmentType(knownFragmentClassName);
		}
		var jsr380CodeFragmentClass = setAndAccessFragmentType(dummyFragmentClass);
		assertThat(jsr380CodeFragmentClass).isEqualTo(dummyFragmentClass.getClass());
	}

	@Test
	void canSetMixins() {
		String mixin = GuavaCodeFragmentIAEMixin.class.getName();
		properties.setProperty(VAADOO_CODE_FRAGMENT_MIXINS, mixin + "," + mixin + "," + mixin);
		assertThat(sut.codeFragmentMixins()).isEqualTo(List.of(GuavaCodeFragmentIAEMixin.class,
				GuavaCodeFragmentIAEMixin.class, GuavaCodeFragmentIAEMixin.class));
	}

	@Test
	void canSetExceptionTypeForNullValues() {
		Class<? extends RuntimeException> exceptionType = UnsupportedOperationException.class;
		useNullExceptionType(exceptionType);
		assertThat(sut.nullValueExceptionType()).isEqualTo(NullPointerException.class);
		assertThat(sut.nullValueExceptionTypeInternalName()).isEqualTo(exceptionType.getName().replace('.', '/'));
	}

	@ParameterizedTest
	@MethodSource("toggleParams")
	void canToggleParam(Function<PropertiesVaadooConfiguration, Boolean> toggler, String propertyName,
			boolean defValue) {
		assertSoftly(s -> {
			s.assertThat(toggler.apply(sut)).isEqualTo(defValue);
			properties.setProperty(propertyName, String.valueOf(!defValue));
			s.assertThat(toggler.apply(sut)).isEqualTo(!defValue);
		});
	}

	static Stream<Object> toggleParams() {
		return Stream.of( //
				args(PropertiesVaadooConfiguration::regexOptimizationEnabled, VAADOO_REGEX_OPTIMIZATION, true), //
				args(PropertiesVaadooConfiguration::customAnnotationsEnabled, VAADOO_CUSTOM_ANNOTATIONS, true), //
				args(PropertiesVaadooConfiguration::removeJsr380Annotations, VAADOO_REMOVE_JSR380_ANNOTATIONS, true));
	}

	static Arguments args(Function<PropertiesVaadooConfiguration, Boolean> function, String toggler,
			boolean defaultValue) {
		return Arguments.arguments(function, toggler, defaultValue);
	}

	static Stream<String> knownFragmentClassNames() {
		return concat(
				EnumSet.allOf(KnownFragmentClass.class).stream().map(KnownFragmentClass::name)
						.flatMap(n -> Stream.of(n, n.toLowerCase(), n.toUpperCase())),
				Stream.of(" ", "XXXXX-INVALID-XXXX", null));
	}

	private void useFragmentType(String fragmentType) {
		properties.setProperty(VAADOO_JSR380_CODE_FRAGMENT_TYPE, fragmentType);
	}

	private void useNullExceptionType(Class<? extends RuntimeException> exceptionType) {
		properties.setProperty(VAADOO_NON_NULL_EXCEPTION_TYPE, exceptionType.getName());
	}

	private Class<? extends Jsr380CodeFragment> setAndAccessFragmentType(Object dummyFragmentClass) {
		properties.setProperty(VAADOO_JSR380_CODE_FRAGMENT_CLASS, dummyFragmentClass.getClass().getName());
		return sut.jsr380CodeFragmentClass();
	}

	private static Object dummyFragmentClass() {
		return Proxy.newProxyInstance(PropertiesVaadooConfigurationTest.class.getClassLoader(),
				new Class<?>[] { Jsr380CodeFragment.class }, (InvocationHandler) (__p, __m, __a) -> null);
	}

}
