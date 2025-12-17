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

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

import java.util.EnumSet;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

import com.github.pfichtner.vaadoo.fragments.Jsr380CodeFragment;

import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.description.type.TypeDescription;

@Slf4j
class PropertiesVaadooConfiguration implements VaadooConfiguration {

	static final String VAADOO_JSR380_CODE_FRAGMENT_TYPE = "vaadoo.jsr380CodeFragmentType";
	static final String VAADOO_JSR380_CODE_FRAGMENT_CLASS = "vaadoo.jsr380CodeFragmentClass";
	static final String VAADOO_CODE_FRAGMENT_MIXINS = "vaadoo.codeFragmentMixins";
	static final String VAADOO_NON_NULL_EXCEPTION_TYPE = "vaadoo.nonNullExceptionType";
	static final String VAADOO_CUSTOM_ANNOTATIONS = "vaadoo.customAnnotations";
	static final String VAADOO_REGEX_OPTIMIZATION = "vaadoo.regexOptimization";
	static final String VAADOO_REMOVE_JSR380_ANNOTATIONS = "vaadoo.removeJsr380Annotations";

	private final Properties properties;

	PropertiesVaadooConfiguration(Properties properties) {
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
		return isEnabled(VAADOO_CUSTOM_ANNOTATIONS, true);
	}

	@Override
	public boolean regexOptimizationEnabled() {
		return isEnabled(VAADOO_REGEX_OPTIMIZATION, true);
	}

	@Override
	public boolean removeJsr380Annotations() {
		return isEnabled(VAADOO_REMOVE_JSR380_ANNOTATIONS, true);
	}

	private boolean isEnabled(String key, boolean defaultValue) {
		return Boolean.parseBoolean(properties.getProperty(key, String.valueOf(defaultValue)));
	}

	public String getProperty(String key) {
		return properties.getProperty(key);
	}

	public boolean matches(TypeDescription target) {
		return true;
	}

	@Override
	public KnownFragmentClass jsrFragmentType() {
		String fragmentType = properties.getProperty(VAADOO_JSR380_CODE_FRAGMENT_TYPE);
		return fragmentType == null ? VaadooConfiguration.super.jsrFragmentType()
				: EnumSet.allOf(KnownFragmentClass.class).stream() //
						.filter(e -> e.name().equalsIgnoreCase(fragmentType)).findFirst().orElse(null);
	}

	@Override
	public Class<? extends Jsr380CodeFragment> jsr380CodeFragmentClass() {
		String fragmentClass = properties.getProperty(VAADOO_JSR380_CODE_FRAGMENT_CLASS);
		return fragmentClass == null //
				? VaadooConfiguration.super.jsr380CodeFragmentClass() //
				: loadClass(fragmentClass);
	}

	@SuppressWarnings("unchecked")
	private Class<? extends Jsr380CodeFragment> loadClass(String fragmentClass) {
		try {
			return (Class<? extends Jsr380CodeFragment>) Class.forName(fragmentClass);
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public List<Class<? extends Jsr380CodeFragment>> codeFragmentMixins() {
		String value = getProperty(VAADOO_CODE_FRAGMENT_MIXINS);
		return value == null || value.trim().isEmpty() //
				? emptyList() //
				: Stream.of(value.split("\\,")).map(String::trim).map(it -> loadClass(it)).collect(toList());
	}

	@Override
	public String nullValueExceptionTypeInternalName() {
		String nullExceptionType = getProperty(VAADOO_NON_NULL_EXCEPTION_TYPE);
		return nullExceptionType == null //
				? VaadooConfiguration.super.nullValueExceptionTypeInternalName() //
				: nullExceptionType.replace('.', '/');
	}

}