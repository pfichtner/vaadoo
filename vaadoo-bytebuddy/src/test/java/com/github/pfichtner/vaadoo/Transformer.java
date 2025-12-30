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

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;

import com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy.JMoleculesPlugin;

import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.build.Plugin.WithPreprocessor;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.DynamicType.Unloaded;
import net.bytebuddy.dynamic.loading.ByteArrayClassLoader;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;

@Setter
@Accessors(fluent = true, chain = true)
@NoArgsConstructor
public final class Transformer {

	private boolean dumpClassFilesToTemp;
	private File projectRoot = new File("jmolecules-bytebuddy-tests");

	public Unloaded<?> transform(DynamicType unloaded) throws Exception {
		return transform(unloaded.getTypeDescription(),
				ClassFileLocator.Simple.of(unloaded.getTypeDescription().getName(), unloaded.getBytes()));
	}

	public Class<?> transform(Class<?> clazz) throws Exception {
		Unloaded<?> transformed = transform(new TypeDescription.ForLoadedType(clazz),
				ClassFileLocator.ForClassLoader.of(clazz.getClassLoader()));
		var allTypes = new HashMap<String, byte[]>();
		allTypes.put(transformed.getTypeDescription().getName(), transformed.getBytes());
		transformed.getAuxiliaryTypes().forEach((aux, type) -> allTypes.put(aux.getName(), type));
		ClassLoader classLoader = new ByteArrayClassLoader.ChildFirst(clazz.getClassLoader(), allTypes,
				ByteArrayClassLoader.PersistenceHandler.MANIFEST);
		return classLoader.loadClass(transformed.getTypeDescription().getName());
	}

	public Unloaded<?> transform(TypeDescription typeDescription, ClassFileLocator classFileLocator)
			throws IOException {
		try (WithPreprocessor plugin = new JMoleculesPlugin(projectRoot)) {
			var locator = new ClassFileLocator.Compound(classFileLocator,
					ClassFileLocator.ForClassLoader.ofSystemLoader());
			plugin.onPreprocess(typeDescription, locator);

			var byteBuddy = new ByteBuddy();
			var builder = byteBuddy.rebase(typeDescription, locator);
			var transformedBuilder = plugin.apply(builder, typeDescription, locator);

			Unloaded<?> transformed = transformedBuilder.make();

			if (dumpClassFilesToTemp) {
				transformed.saveIn(Files.createTempDirectory("generated-class").toFile());
			}

			return transformed;
		}
	}

	/**
	 * Default newInstance: forbid well-known JSR380 packages.
	 */
	public static Object newInstance(Unloaded<?> unloaded, Object[] args) throws Exception {
		return newInstance(unloaded, args, "javax.validation.", "jakarta.validation.");
	}

	/**
	 * Overloaded newInstance allowing to specify forbidden package prefixes.
	 */
	public static Object newInstance(Unloaded<?> unloaded, Object[] args, String... forbiddenPackagePrefixes)
			throws Exception {
		ClassLoader blockingClassLoader = new ForbiddenPackagesClassLoader(
				Thread.currentThread().getContextClassLoader(), forbiddenPackagePrefixes);
		Class<?> clazz = unloaded.load(blockingClassLoader, ClassLoadingStrategy.Default.INJECTION).getLoaded();
		try {
			return clazz.getDeclaredConstructors()[0].newInstance(args);
		} catch (InvocationTargetException e) {
			Throwable cause = e.getCause();
			throw cause instanceof Exception ? (Exception) cause : new RuntimeException(e);
		}
	}

	static final class ForbiddenPackagesClassLoader extends ClassLoader {
		private final List<String> forbiddenPrefixes;

		ForbiddenPackagesClassLoader(ClassLoader parent, String... forbiddenPrefixes) {
			super(parent);
			this.forbiddenPrefixes = List.of(forbiddenPrefixes);
		}

		@Override
		protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
			if (name != null) {
				var forbiddenNames = forbiddenPrefixes.stream().filter(p -> name.startsWith(p)).collect(toList());
				if (!forbiddenNames.isEmpty()) {
					throw new ClassNotFoundException(
							format("Class(es) forbidden by prefix %s: %s", forbiddenNames, name));
				}
			}
			return super.loadClass(name, resolve);
		}
	}

}
