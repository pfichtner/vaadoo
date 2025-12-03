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
package com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy.PluginLogger.Log;
import com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy.config.VaadooConfiguration;

import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType.Builder;

/**
 * {@code VaadooPlugin} is a custom {@link LoggingPlugin} that extends the
 * concept and design principles of the {@code jMolecules} plugin architecture
 * (e.g. {@code JMoleculesSpringJpaPlugin}, {@code JMoleculesAxonSpringPlugin},
 * {@code JMoleculesSpringDataPlugin}, etc.).
 * <p>
 * This plugin is intentionally maintained as a separate extension that operates
 * on a local copy of the jMolecules codebase rather than being part of the
 * official jMolecules project. The goal of this separation is to allow
 * experimentation and evolution of Vaadoo-specific functionality without
 * introducing dependencies or constraints on the upstream project.
 * <p>
 * Conceptually, {@code VaadooPlugin} could be integrated into the jMolecules
 * ecosystem at any time, should it prove useful or align with jMolecules’
 * long-term design. However, such integration is not guaranteed or required;
 * the plugin is designed to function fully on its own.
 * <p>
 * The plugin applies Vaadoo-specific behavior through
 * {@link VaadooImplementor}. It uses {@link JMoleculesTypeBuilder} to extend
 * the Byte Buddy transformation pipeline for those identified types.
 *
 * <p>
 * <strong>Note:</strong> This class and the Vaadoo extensions are not part of
 * the official jMolecules distribution.
 *
 * @author Peter Fichtner
 */
class VaadooPlugin implements LoggingPlugin {

	private final VaadooConfiguration configuration;
	private final VaadooImplementor vaadooImplementor;

	public VaadooPlugin(VaadooConfiguration configuration) {
		this.configuration = configuration;
		this.vaadooImplementor = new VaadooImplementor(configuration);
	}

	@Override
	public boolean matches(TypeDescription target) {
		return configuration.matches(target);
	}

	@Override
	public Builder<?> apply(Builder<?> builder, TypeDescription type, ClassFileLocator classFileLocator) {
		ClassWorld classWorld = ClassWorld.of(classFileLocator);
		Log log = null;
		if (classWorld.isAvailable("org.jmolecules.bytebuddy.PluginLogger")) {
			log = forwardToJmolecules(type, "vaadoo");
		}
		if (log == null) {
			log = PluginLogger.INSTANCE.getLog(type, "vaadoo");
		}
		return JMoleculesTypeBuilder.of(log, builder).map(__ -> true, this::handleEntity).conclude();
	}

	private Log forwardToJmolecules(TypeDescription type, String name) {
		try {
			ClassLoader jmCL = TypeDescription.class.getClassLoader();
			Class<?> loggerClass = Class.forName("org.jmolecules.bytebuddy.PluginLogger", false, jmCL);
			Object enumInstance = loggerClass.getEnumConstants()[0]; // the real INSTANCE
			Method method = enumInstance.getClass().getMethod("getLog", TypeDescription.class, String.class);
			method.setAccessible(true);
			Object logDelegate = method.invoke(enumInstance, type, name);
			return (Log) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[] { Log.class }, (p, m, a) -> {
				Method target = logDelegate.getClass().getDeclaredMethod(m.getName(), m.getParameterTypes());
				try {
					target.setAccessible(true);
					return target.invoke(logDelegate, a);
				} catch (InvocationTargetException ite) {
					Throwable t = ite.getTargetException();
					if (t instanceof RuntimeException) {
						throw (RuntimeException) t;
					} else if (t instanceof Error) {
						throw (Error) t;
					} else {
						throw new RuntimeException(t);
					}
				}
			});

		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	private JMoleculesTypeBuilder handleEntity(JMoleculesTypeBuilder type) {
		return type.map(vaadooImplementor::implementVaadoo);
	}

}