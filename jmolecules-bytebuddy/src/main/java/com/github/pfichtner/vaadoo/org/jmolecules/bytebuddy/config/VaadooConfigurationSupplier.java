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
package com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy.config;

import static com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy.config.DefaultJMoleculesVaadooConfiguration.jMoleculesVaadooConfigurationIfApplicable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;

import com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy.ClassWorld;

import lombok.extern.slf4j.Slf4j;

/**
 * Supplies the effective {@link VaadooConfiguration} for the Vaadoo Byte Buddy
 * instrumentation pipeline.
 *
 * <p>This class resolves configuration from two possible sources:
 *
 * <ol>
 *   <li><strong>A local {@code vaadoo.config} properties file</strong> located
 *       in the project's root directory.  
 *       <p>The supplier attempts to detect the project root by examining the
 *       build output folder (e.g. {@code target/classes} or
 *       {@code build/classes}) and walking upward until it finds a recognized
 *       build file ({@code pom.xml}, {@code build.gradle},
 *       {@code build.gradle.kts}). If a {@code vaadoo.config} file exists in
 *       that folder, it is loaded and converted to a
 *       {@link PropertiesVaadooConfiguration} instance.</p>
 *   </li>
 *
 *   <li><strong>A jMolecules-derived configuration</strong> discovered through
 *       {@link DefaultJMoleculesVaadooConfiguration#jMoleculesVaadooConfigurationIfApplicable(ClassWorld)}.
 *       <p>If no explicit configuration file is found, the supplier delegates
 *       to the jMolecules-based detection mechanism. This allows Vaadoo to
 *       automatically activate only when the current project uses jMolecules
 *       types or conventions.</p>
 *   </li>
 * </ol>
 *
 * <p>If neither a config file nor a jMolecules-based configuration is available,
 * {@link VaadooConfiguration#DEFAULT} is used.
 *
 * <p>The resolution order is therefore:
 *
 * <ol>
 *   <li>Configuration from {@code vaadoo.config} (highest priority)</li>
 *   <li>jMolecules-based auto-detection</li>
 *   <li>Fallback to the default configuration</li>
 * </ol>
 *
 * <p>This class is responsible only for discovering and supplying configuration.
 * It performs no Byte Buddy transformations itself.
 *
 * <p>Logging is used to indicate whether a configuration file was found, which
 * project root was detected, and when fallbacks occur.
 *
 * @author Peter Fichtner
 */
@Slf4j
public class VaadooConfigurationSupplier {

	public static final String VAADOO_CONFIG = "vaadoo.config";

	private final VaadooConfiguration configurationFromConfigFile;

	public VaadooConfigurationSupplier(File outputFolder) {
		this.configurationFromConfigFile = tryLoadConfig(outputFolder).orElse(null);
	}

	public VaadooConfiguration configuration(ClassWorld world) {
		if (configurationFromConfigFile != null) {
			return configurationFromConfigFile;
		}
		return jMoleculesVaadooConfigurationIfApplicable(world).orElseGet(() -> VaadooConfiguration.DEFAULT);
	}

	private static Optional<PropertiesVaadooConfiguration> tryLoadConfig(File outputFolder) {
		Path projectRoot = detectProjectRoot(outputFolder);
		File file = detectConfiguration(projectRoot);
		return Optional.ofNullable(loadProperties(file, outputFolder)).map(PropertiesVaadooConfiguration::new);
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

	private static boolean hasBuildFile(Path folder) {
		return Stream.of("pom.xml", "build.gradle", "build.gradle.kts").map(folder::resolve).map(Path::toFile)
				.anyMatch(File::exists);
	}

	private static Properties loadProperties(File configuration, File outputFolder) {
		if (configuration == null) {
			logNoConfigFound(outputFolder);
			return null;
		}

		try {
			Properties properties = new Properties();
			properties.load(new FileInputStream(configuration));
			return properties;
		} catch (FileNotFoundException e) {
			logNoConfigFound(outputFolder);
			return null;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void logNoConfigFound(File outputFolder) {
		log.info("No {} found traversing {}", VAADOO_CONFIG, outputFolder.getAbsolutePath());
	}

}
