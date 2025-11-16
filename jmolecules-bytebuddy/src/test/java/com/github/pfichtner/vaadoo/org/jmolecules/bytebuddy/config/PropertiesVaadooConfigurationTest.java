package com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy.config;

import static com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy.config.PropertiesVaadooConfiguration.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;

import com.github.pfichtner.vaadoo.fragments.Jsr380CodeFragment;
import com.github.pfichtner.vaadoo.fragments.impl.GuavaCodeFragment;
import com.github.pfichtner.vaadoo.fragments.impl.JdkOnlyCodeFragment;
import com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy.config.VaadooConfiguration.KnownFragmentClass;

class PropertiesVaadooConfigurationTest {

	Properties properties = new Properties();
	PropertiesVaadooConfiguration sut = new PropertiesVaadooConfiguration(properties);

	@Test
	void doesReturnDefaultFragmentClass() {
		assertThat(sut.jsr380CodeFragmentClass()).isEqualTo(JdkOnlyCodeFragment.class);
	}

	@Test
	void ifNoFragmentClassAndNoFragmentTypeIsSetThenJdkOnlyFragmentIsReturned() {
		assertThat(sut.jsr380CodeFragmentClass()).isEqualTo(JdkOnlyCodeFragment.class);
	}

	@Test
	void ifFragmentTypeIsSetThenItIsReturned() {
		properties.setProperty(VAADOO_JSR380_CODE_FRAGMENT_TYPE, KnownFragmentClass.GUAVA.name());
		assertThat(sut.jsr380CodeFragmentClass()).isEqualTo(GuavaCodeFragment.class);
	}

	@ParameterizedTest
	@EnumSource
	@NullSource
	void ifFragmentClassIsSetItIsUsed(KnownFragmentClass knownFragmentClass) {
		Object dummyFragmentClass = Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[] { Jsr380CodeFragment.class }, (InvocationHandler) (__p, __m, __a) -> null);
		if (knownFragmentClass != null) {
			properties.setProperty(VAADOO_JSR380_CODE_FRAGMENT_TYPE, knownFragmentClass.name());
		}
		properties.setProperty(VAADOO_JSR380_CODE_FRAGMENT_CLASS, dummyFragmentClass.getClass().getName());
		assertThat(sut.jsr380CodeFragmentClass()).isEqualTo(dummyFragmentClass.getClass());
	}

}
