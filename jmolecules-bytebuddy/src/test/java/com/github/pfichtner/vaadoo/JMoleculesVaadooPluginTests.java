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
package com.github.pfichtner.vaadoo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy.JMoleculesPlugin;
import com.github.pfichtner.vaadoo.testclasses.AnnotationDoesNotSupportType;
import com.github.pfichtner.vaadoo.testclasses.ClassWithAttribute;
import com.github.pfichtner.vaadoo.testclasses.ClassWithNotNullAttribute;
import com.github.pfichtner.vaadoo.testclasses.EmptyClass;
import com.github.pfichtner.vaadoo.testclasses.ValueObjectWithAttribute;
import com.github.pfichtner.vaadoo.testclasses.ValueObjectWithRegexAttribute;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.build.Plugin.WithPreprocessor;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType.Unloaded;
import net.bytebuddy.dynamic.loading.ByteArrayClassLoader;

class JMoleculesVaadooPluginTests {

	private static final boolean DUMP_CLASS_FILES_TO_TEMP = true;

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
				.satisfies(e -> assertThat(e.getCause()).isInstanceOf(NullPointerException.class)
						.hasMessage("someString must not be null"));
	}

	@Test
	void regex(@TempDir File outputFolder) throws Exception {
		Class<?> transformedClass = transformedClass(ValueObjectWithRegexAttribute.class, outputFolder);
		Constructor<?> constructor = transformedClass.getDeclaredConstructor(String.class);
		constructor.newInstance("42");
		assertThatException().isThrownBy(() -> constructor.newInstance("4")).satisfies(e -> assertThat(e.getCause())
				.isInstanceOf(IllegalArgumentException.class).hasMessage("someTwoDigits must match \"\\d\\d\""));

	}

	@Test
	void wrongType(@TempDir File outputFolder) {
		assertThatException().isThrownBy(() -> transformedClass(AnnotationDoesNotSupportType.class, outputFolder))
				.satisfies(e -> assertThat(e).isInstanceOf(IllegalStateException.class)
						.hasMessage("Annotation " + "jakarta.validation.constraints.NotEmpty"
								+ " on type java.lang.Integer not allowed, " + "allowed only on types: "
								+ "[java.lang.CharSequence, java.util.Collection, java.util.Map, java.lang.Object[]]"));
	}

	private Class<?> transformedClass(Class<?> clazz, File outputFolder) throws Exception {
		try (WithPreprocessor plugin = new JMoleculesPlugin(outputFolder)) {
			TypeDescription typeDescription = new TypeDescription.ForLoadedType(clazz);
			ClassFileLocator locator = ClassFileLocator.ForClassLoader.of(clazz.getClassLoader());

			plugin.onPreprocess(typeDescription, locator);

			var byteBuddy = new ByteBuddy();
			var builder = byteBuddy.rebase(clazz);
			// var builder = byteBuddy.redefine(clazz);
			var transformedBuilder = plugin.apply(builder, typeDescription, locator);

			Unloaded<?> dynamicType = transformedBuilder.make();
			if (DUMP_CLASS_FILES_TO_TEMP) {
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

}
