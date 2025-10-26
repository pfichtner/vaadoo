/*
 * Copyright 2021-2025 the original author or authors.
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
package org.jmolecules.bytebuddy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatRuntimeException;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.jmolecules.bytebuddy.testclasses.ClassWithAttribute;
import org.jmolecules.bytebuddy.testclasses.ClassWithNotNullAttribute;
import org.jmolecules.bytebuddy.testclasses.EmptyClass;
import org.jmolecules.bytebuddy.testclasses.ValueObjectWithAttribute;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import example.SampleValueObject;
import example.SampleValueObjectWithSideEffect;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.build.Plugin.WithPreprocessor;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType.Unloaded;
import net.bytebuddy.dynamic.loading.ByteArrayClassLoader;

class JMoleculesVaadooPluginTests {

	private static final boolean DEBUG = true;

	@Test
	void emptyClassIsUnchanged(@TempDir File outputFolder) throws Exception {
		assertThatNoException().isThrownBy(() -> transformedClass(EmptyClass.class, outputFolder));
	}

	@Test
	void classWithAttribute(@TempDir File outputFolder) throws Exception {
		assertThatNoException().isThrownBy(() -> transformedClass(ClassWithAttribute.class, outputFolder));
	}

	@Test
	void classWithNotNullAttribute(@TempDir File outputFolder) throws Exception {
		assertThatNoException().isThrownBy(() -> transformedClass(ClassWithNotNullAttribute.class, outputFolder));
	}

	@Test
	void valueObjectWithAttribute(@TempDir File outputFolder) throws Exception {
		Class<?> transformedClass = transformedClass(ValueObjectWithAttribute.class, outputFolder);
		Constructor<?> stringArgConstructor = transformedClass.getDeclaredConstructor(String.class);
		assertThatException().isThrownBy(() -> stringArgConstructor.newInstance((String) null))
				.satisfies(e -> assertThat(e.getCause()).isInstanceOf(NullPointerException.class).hasMessage("XXXXX"));
	}

	private Class<?> transformedClass(Class<?> clazz, File outputFolder) throws Exception {
		try (WithPreprocessor plugin = new JMoleculesPlugin(outputFolder)) {
			TypeDescription typeDescription = new TypeDescription.ForLoadedType(clazz);
			ClassFileLocator locator = ClassFileLocator.ForClassLoader.of(clazz.getClassLoader());

			plugin.onPreprocess(typeDescription, locator);

//			var builder = new ByteBuddy().redefine(clazz);
			var builder = new ByteBuddy().rebase(clazz);
			var transformedBuilder = plugin.apply(builder, typeDescription, locator);

			Unloaded<?> dynamicType = transformedBuilder.make();
			if (DEBUG) {
				dynamicType.saveIn(outputFolder);
			}
			ByteBuddyAgent.install();

			Map<String, byte[]> allTypes = new HashMap<>();
			allTypes.put(dynamicType.getTypeDescription().getName(), dynamicType.getBytes());
			dynamicType.getAuxiliaryTypes().forEach((aux, type) -> allTypes.put(aux.getName(), type));

			ClassLoader classLoader = new ByteArrayClassLoader.ChildFirst(getClass().getClassLoader(), allTypes,
					ByteArrayClassLoader.PersistenceHandler.MANIFEST);
			return classLoader.loadClass(dynamicType.getTypeDescription().getName());
		}
	}

	@Test
	void defaultsForSampleValueObject() {
		List<List<Class<?>>> methodParams = Stream.of(SampleValueObject.class.getDeclaredMethods())
				.filter(m -> m.getName().equals("validate")).map(Method::getParameterTypes).map(Arrays::asList)
				.toList();
		assertThat(methodParams).containsExactly(List.of(String.class));
	}

	@Test
	void throwsExceptionOnNullValue() {
		assertThatRuntimeException().isThrownBy(() -> new SampleValueObject(null))
				.withMessage("XXXXX");
	}

	@Test
	void doesNotThrowExceptionOnNonNullValue() {
		assertThatNoException().isThrownBy(() -> new SampleValueObject("test"));
	}

	@Test
	void mustNotCallAddOnListWithNull() {
		List<String> list = new ArrayList<>();
		assertThatRuntimeException().isThrownBy(() -> new SampleValueObjectWithSideEffect(list, null))
				.withMessage("XXXXX");
		assertThat(list).isEmpty();
	}

}
