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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy.config.VaadooConfiguration;
import com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy.config.VaadooConfigurationSupplier;

import net.bytebuddy.build.Plugin.WithPreprocessor;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType.Builder;

/**
 * @author Oliver Drotbohm
 * @author Simon Zambrovski
 * @author Peter Fichtner
 */
public class JMoleculesPlugin implements LoggingPlugin, WithPreprocessor {

	private final Map<ClassFileLocator, List<LoggingPlugin>> globalPlugins = new HashMap<>();
	private final Map<TypeDescription, List<? extends LoggingPlugin>> delegates = new HashMap<>();
	private final VaadooConfigurationSupplier configurationSupplier;

	public JMoleculesPlugin(File outputFolder) {
		this.configurationSupplier = new VaadooConfigurationSupplier(outputFolder);
	}

	@Override
	public void onPreprocess(TypeDescription typeDescription, ClassFileLocator classFileLocator) {
		ClassWorld world = ClassWorld.of(classFileLocator);
		VaadooConfiguration configuration = configurationSupplier.configuration(world);
		if (!configuration.include(typeDescription) || PluginUtils.isCglibProxyType(typeDescription)) {
			return;
		}

		List<LoggingPlugin> plugins = globalPlugins.computeIfAbsent(classFileLocator, locator -> {
			return Stream.of( //
					vaadooPlugin(configuration) //
			).flatMap(identity()).collect(toList());
		});

		delegates.computeIfAbsent(typeDescription, it -> plugins.stream().filter(p -> p.matches(it)).peek(plugin -> {
			if (plugin instanceof WithPreprocessor) {
				((WithPreprocessor) plugin).onPreprocess(it, classFileLocator);
			}
		}).collect(toList()));
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

	private Stream<LoggingPlugin> vaadooPlugin(VaadooConfiguration configuration) {
		return Stream.of(new VaadooPlugin(configuration));
	}

}
