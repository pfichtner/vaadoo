package com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy.config;

import static com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy.config.PropertiesVaadooConfiguration.VAADOO_JSR380_CODE_FRAGMENT_CLASS;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import com.github.pfichtner.vaadoo.fragments.Jsr380CodeFragment;
import com.github.pfichtner.vaadoo.fragments.impl.JdkOnlyCodeFragment;

class PropertiesVaadooConfigurationTest {

	Properties properties = new Properties();
	PropertiesVaadooConfiguration sut = new PropertiesVaadooConfiguration(properties);

	@Test
	void doesReturnDefaultFragmentClass() {
		assertThat(sut.jsr380CodeFragmentClass()).isEqualTo(JdkOnlyCodeFragment.class);
	}

	@Test
	void canLoadAlternativeFragmentClass() {
		Object dummyFragmentClass = Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[] { Jsr380CodeFragment.class }, (InvocationHandler) (__p, __m, __a) -> null);
		properties.setProperty(VAADOO_JSR380_CODE_FRAGMENT_CLASS, dummyFragmentClass.getClass().getName());
		assertThat(sut.jsr380CodeFragmentClass()).isEqualTo(dummyFragmentClass.getClass());
	}

}
