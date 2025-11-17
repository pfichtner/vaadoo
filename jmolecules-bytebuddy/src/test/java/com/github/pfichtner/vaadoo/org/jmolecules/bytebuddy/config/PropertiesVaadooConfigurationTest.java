package com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy.config;

import static com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy.config.PropertiesVaadooConfiguration.VAADOO_JSR380_CODE_FRAGMENT_CLASS;
import static com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy.config.PropertiesVaadooConfiguration.VAADOO_JSR380_CODE_FRAGMENT_TYPE;
import static java.util.stream.Stream.concat;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.EnumSet;
import java.util.Properties;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.github.pfichtner.vaadoo.fragments.Jsr380CodeFragment;
import com.github.pfichtner.vaadoo.fragments.impl.GuavaCodeFragment;
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

	static Stream<String> knownFragmentClassNames() {
		return concat(
				EnumSet.allOf(KnownFragmentClass.class).stream().map(KnownFragmentClass::name)
						.flatMap(n -> Stream.of(n, n.toLowerCase(), n.toUpperCase())),
				Stream.of(" ", "XXXXX-INVALID-XXXX", null));
	}

	private void useFragmentType(String fragmentType) {
		properties.setProperty(VAADOO_JSR380_CODE_FRAGMENT_TYPE, fragmentType);
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
