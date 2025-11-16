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
package com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy.config;

import java.util.Properties;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.description.type.TypeDescription;

@Slf4j
public class PropertiesVaadooConfiguration implements VaadooConfiguration {

	private final Properties properties;

	public PropertiesVaadooConfiguration(Properties properties) {
		this.properties = properties;
		String toInclude = getPackagesToInclude();
		if (toInclude != null) {
			log.info("Applying code generation to types located in package(s): {}.", toInclude);
		}
	}

	public boolean include(TypeDescription description) {
		String value = getPackagesToInclude();
		return value.trim().isEmpty() || Stream.of(value.split("\\,")).map(String::trim).map(it -> it.concat("."))
				.anyMatch(description.getName()::startsWith);
	}

	private String getPackagesToInclude() {
		return properties.getProperty("bytebuddy.include", "");
	}

	public boolean customAnnotationsEnabled() {
		return Boolean.parseBoolean(properties.getProperty("vaadoo.customAnnotationsEnabled", String.valueOf(true)));
	}

	public String getProperty(String key) {
		return properties.getProperty(key);
	}

	public boolean matches(TypeDescription target) {
		return true;
	}

}