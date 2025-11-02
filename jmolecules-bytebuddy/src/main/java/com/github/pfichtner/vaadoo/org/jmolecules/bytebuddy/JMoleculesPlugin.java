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
package com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy;

import static java.util.Collections.emptyList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.build.Plugin.WithPreprocessor;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType.Builder;

/**
 * @author Oliver Drotbohm
 * @author Simon Zambrovski
 */
@Slf4j
public class JMoleculesPlugin implements LoggingPlugin, WithPreprocessor {

	private final Map<ClassFileLocator, List<LoggingPlugin>> globalPlugins = new HashMap<>();
	private final Map<TypeDescription, List<? extends LoggingPlugin>> delegates = new HashMap<>();
	private final VaadooConfiguration configuration;

	public JMoleculesPlugin(File outputFolder) {
		this.configuration = new VaadooConfiguration(outputFolder);
	}

	@Override
	public void onPreprocess(TypeDescription typeDescription, ClassFileLocator classFileLocator) {
		if (!configuration.include(typeDescription) || PluginUtils.isCglibProxyType(typeDescription)) {
			return;
		}

		List<LoggingPlugin> plugins = globalPlugins.computeIfAbsent(classFileLocator, locator -> {
			ClassWorld world = ClassWorld.of(locator);
			return Stream.of( //
					vaadooPlugin(world) //
			).flatMap(identity()).collect(toList());
		});

		delegates.computeIfAbsent(typeDescription, it -> {
			return plugins.stream().filter(plugin -> plugin.matches(it)).peek(plugin -> {
				if (plugin instanceof WithPreprocessor) {
					((WithPreprocessor) plugin).onPreprocess(it, classFileLocator);
				}
			}).collect(toList());
		});
	}

	@Override
	public boolean matches(TypeDescription target) {
		return !PluginUtils.isCglibProxyType(target) //
				&& !delegates.getOrDefault(target, emptyList()).isEmpty();
	}

	@Override
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Builder<?> apply(Builder<?> builder, TypeDescription typeDescription, ClassFileLocator classFileLocator) {
		return delegates.get(typeDescription).stream().reduce(builder,
				(it, plugin) -> (Builder) plugin.apply(it, typeDescription, classFileLocator), (left, right) -> right);
	}

	private static Stream<LoggingPlugin> vaadooPlugin(ClassWorld world) {
		return Stream.of(new VaadooPlugin());
		// TODO does vaadoo depend on something to get active at all?
//		return world.isAvailable("org.axonframework.spring.stereotype.Aggregate") //
//				? Stream.of(new VaadooPlugin()) //
//				: Stream.empty();
	}

	@Slf4j
	private static class VaadooConfiguration {

		private static final String VAADOO_CONFIG = "vaadoo.config";

		private final Properties properties = new Properties();

		public VaadooConfiguration(File outputFolder) {
			Path projectRoot = detectProjectRoot(outputFolder);
			loadProperties(detectConfiguration(projectRoot), outputFolder);

			String toInclude = getPackagesToInclude();
			if (toInclude != null) {
				log.info("Applying code generation to types located in package(s): {}.", toInclude);
			}
		}

		private void loadProperties(File configuration, File outputFolder) {
			if (configuration == null) {
				logNoConfigFound(outputFolder);
				return;
			}

			try {
				this.properties.load(new FileInputStream(configuration));
			} catch (FileNotFoundException e) {
				logNoConfigFound(outputFolder);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}

		public void logNoConfigFound(File outputFolder) {
			log.info("No {} found traversing {}", VAADOO_CONFIG, outputFolder.getAbsolutePath());
		}

		public boolean include(TypeDescription description) {
			String value = properties.getProperty("bytebuddy.include", "");
			return value.trim().isEmpty() || Stream.of(value.split("\\,")).map(String::trim).map(it -> it.concat("."))
					.anyMatch(description.getName()::startsWith);
		}

		private String getPackagesToInclude() {
			return properties.getProperty("bytebuddy.include");
		}

		private static Path detectProjectRoot(File file) {
			String path = file.getAbsolutePath();
			return Stream.of("target/classes", "build/classes").filter(path::contains)
					.map(it -> new File(path.substring(0, path.indexOf(it) - 1))).map(File::toPath).findFirst()
					.orElseGet(file::toPath);
		}

		private static File detectConfiguration(Path folder) {
			if (!hasBuildFile(folder)) {
				return null;
			}

			File candidate = folder.resolve(VAADOO_CONFIG).toFile();

			if (candidate.exists()) {
				log.info("Found {} at {}", VAADOO_CONFIG, candidate.getAbsolutePath());
				return candidate;
			}

			return detectConfiguration(folder.getParent());
		}
	}

	private static boolean hasBuildFile(Path folder) {
		return Stream.of("pom.xml", "build.gradle", "build.gradle.kts").map(folder::resolve).map(Path::toFile)
				.anyMatch(File::exists);
	}

}
