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

import static java.util.stream.Collectors.joining;
import static org.jmolecules.bytebuddy.PluginUtils.abbreviate;

import java.util.Arrays;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import org.jmolecules.bytebuddy.PluginLogger.Log;

import net.bytebuddy.description.method.MethodDescription.InDefinedShape;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeDescription.Generic;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * A wrapper around a {@link Builder} to allow issuing bytecode manipulations
 * working with JMolecules abstractions like aggregates etc.
 *
 * @author Oliver Drotbohm
 */
class JMoleculesTypeBuilder extends JMoleculesType {

	private final Log logger;
	private final Builder<?> builder;

	JMoleculesTypeBuilder(Log logger, Builder<?> builder) {
		super(builder.toTypeDescription());
		this.logger = logger;
		this.builder = builder;
	}

	/**
	 * Creates a new {@link JMoleculesType} for the given {@link ModuleLogger} and
	 * {@link Builder}.
	 *
	 * @param logger  must not be {@literal null}.
	 * @param builder must not be {@literal null}.
	 * @return
	 */
	public static JMoleculesTypeBuilder of(Log logger, Builder<?> builder) {
		if (logger == null) {
			throw new IllegalArgumentException("PluginLogger must not be null!");
		}
		if (builder == null) {
			throw new IllegalArgumentException("Builder must not be null!");
		}
		return new JMoleculesTypeBuilder(logger, builder);
	}

	public JMoleculesTypeBuilder implement(Class<?> interfaze) {
		if (type.isAssignableTo(interfaze)) {
			return this;
		}
		logger.info("{} - Implement {}.", abbreviate(type), abbreviate(interfaze));
		return mapBuilder((it, log) -> it.implement(interfaze));
	}

	public JMoleculesTypeBuilder implement(Class<?> interfaze, TypeDefinition... generics) {
		logger.info("Implementing {}{}.", abbreviate(interfaze),
				Arrays.stream(generics).map(PluginUtils::abbreviate).collect(joining("<", ", ", ">")));
		TypeDescription loadedType = Generic.Builder.rawType(interfaze).build().asErasure();
		Generic build = Generic.Builder.parameterizedType(loadedType, generics).build();
		return mapBuilder(builder -> builder.implement(build));
	}

	public JMoleculesTypeBuilder map(Function<JMoleculesTypeBuilder, JMoleculesTypeBuilder> function) {
		return function.apply(this);
	}

	public JMoleculesTypeBuilder map(BiFunction<JMoleculesTypeBuilder, Log, JMoleculesTypeBuilder> mapper) {
		return mapper.apply(this, logger);
	}

	public JMoleculesTypeBuilder mapBuilder(Function<Builder<?>, Builder<?>> mapper) {
		return JMoleculesTypeBuilder.of(logger, mapper.apply(builder));
	}

	public JMoleculesTypeBuilder mapBuilder(BiFunction<Builder<?>, Log, Builder<?>> mapper) {
		return JMoleculesTypeBuilder.of(logger, mapper.apply(builder, logger));
	}

	public JMoleculesTypeBuilder mapBuilder(Predicate<JMoleculesTypeBuilder> filter,
			Function<Builder<?>, Builder<?>> mapper) {
		return filter.test(this) ? JMoleculesTypeBuilder.of(logger, mapper.apply(builder)) : this;
	}

	public JMoleculesTypeBuilder map(Predicate<JMoleculesTypeBuilder> filter,
			Function<JMoleculesTypeBuilder, JMoleculesTypeBuilder> mapper) {
		return filter.test(this) ? mapper.apply(this) : this;
	}

	public JMoleculesTypeBuilder mapLogged(Predicate<JMoleculesTypeBuilder> filter,
			BiFunction<JMoleculesTypeBuilder, Log, JMoleculesTypeBuilder> mapper) {
		return filter.test(this) ? mapper.apply(this, logger) : this;
	}

	public Builder<?> conclude() {
		return builder;
	}

	public InDefinedShape tryFindMethod(ElementMatcher<InDefinedShape> matcher) {
		MethodList<InDefinedShape> methods = type.getDeclaredMethods().filter(matcher);
		return methods.isEmpty() ? null : methods.get(0);
	}

}
