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

import static lombok.AccessLevel.PRIVATE;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy.JMoleculesPlugin;

import lombok.RequiredArgsConstructor;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.build.Plugin.WithPreprocessor;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.DynamicType.Unloaded;
import net.bytebuddy.dynamic.loading.ByteArrayClassLoader;

@RequiredArgsConstructor(access = PRIVATE)
public final class Transformer {

	private static final boolean DUMP_CLASS_FILES_TO_TEMP = false;

	public static Unloaded<Object> transformClass(DynamicType unloaded) throws Exception {
		return transformClass(unloaded.getTypeDescription(),
				ClassFileLocator.Simple.of(unloaded.getTypeDescription().getName(), unloaded.getBytes()));
	}

	public static Class<?> transformClass(Class<?> clazz) throws Exception {
		Unloaded<Object> transformedClass = transformClass(new TypeDescription.ForLoadedType(clazz),
				ClassFileLocator.ForClassLoader.of(clazz.getClassLoader()));
		Map<String, byte[]> allTypes = new HashMap<>();
		allTypes.put(transformedClass.getTypeDescription().getName(), transformedClass.getBytes());
		transformedClass.getAuxiliaryTypes().forEach((aux, type) -> allTypes.put(aux.getName(), type));
		ClassLoader classLoader = new ByteArrayClassLoader.ChildFirst(clazz.getClassLoader(), allTypes,
				ByteArrayClassLoader.PersistenceHandler.MANIFEST);
		return classLoader.loadClass(transformedClass.getTypeDescription().getName());
	}

	public static Unloaded<Object> transformClass(TypeDescription typeDescription, ClassFileLocator cfl)
			throws IOException {
		try (WithPreprocessor plugin = new JMoleculesPlugin(Jsr380DynamicClassTest.dummyRoot())) {
			var locator = new ClassFileLocator.Compound(cfl, ClassFileLocator.ForClassLoader.ofSystemLoader());
			plugin.onPreprocess(typeDescription, locator);

			var byteBuddy = new ByteBuddy();
			var builder = byteBuddy.rebase(typeDescription, locator);
			var transformedBuilder = plugin.apply(builder, typeDescription, locator);

			@SuppressWarnings("unchecked")
			DynamicType.Unloaded<Object> transformed = (DynamicType.Unloaded<Object>) transformedBuilder.make();

			if (DUMP_CLASS_FILES_TO_TEMP) {
				transformed.saveIn(Files.createTempDirectory("generated-class").toFile());
			}

			return transformed;
		}
	}

}
