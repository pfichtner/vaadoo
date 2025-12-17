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

import static java.util.Optional.empty;
import static net.bytebuddy.matcher.ElementMatchers.nameMatches;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy.ClassWorld;
import com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy.PluginUtils;

import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.annotation.AnnotationList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeDescription.Generic;

/**
 * Default {@link VaadooConfiguration} activated when the current project uses
 * jMolecules' Value Object types or annotations.
 *
 * <p>
 * This configuration is applied automatically when
 * {@code org.jmolecules.ddd.types.ValueObject} is present in the
 * {@link ClassWorld}. Detection is performed by
 * {@link #jMoleculesVaadooConfigurationIfApplicable(ClassWorld)}, which returns
 * an {@link Optional} containing an instance of this configuration only if
 * jMolecules is on the classpath. This ensures that Vaadoo’s
 * jMolecules-specific behavior is enabled only for projects that actually use
 * jMolecules.
 *
 * <p>
 * Once active, this configuration identifies target types that should receive
 * Vaadoo Byte Buddy transformations based on jMolecules conventions:
 *
 * <ul>
 * <li>Types implementing {@code org.jmolecules.ddd.types.ValueObject}</li>
 * <li>Types annotated with {@code org.jmolecules.ddd.annotation.ValueObject}
 * (declared or inherited)</li>
 * <li>Java records (implicitly treated as value objects)</li>
 * <li>Types whose superclasses match any of the above criteria</li>
 * </ul>
 *
 * <p>
 * Certain types are excluded from consideration:
 *
 * <ul>
 * <li>Annotations</li>
 * <li>CGLIB-generated proxy types</li>
 * </ul>
 *
 * <p>
 * This configuration enables all custom annotations
 * ({@link #customAnnotationsEnabled()}) and does not restrict the set of types
 * selected for instrumentation beyond the {@link #matches(TypeDescription)}
 * predicate.
 *
 * <p>
 * Note: This class encapsulates all jMolecules-specific detection logic so that
 * VaadooPlugin does not need to inspect jMolecules types directly.
 */
class DefaultJMoleculesVaadooConfiguration implements VaadooConfiguration {

	private static final String jmoleculesValueObjectInterface = "org.jmolecules.ddd.types.ValueObject";

	static Optional<VaadooConfiguration> jMoleculesVaadooConfigurationIfApplicable(ClassWorld classWorld) {
		return isApplicable(classWorld) //
				? Optional.of(new DefaultJMoleculesVaadooConfiguration()) //
				: empty();
	}

	private static boolean isApplicable(ClassWorld world) {
		return world.isAvailable(jmoleculesValueObjectInterface);
	}

	@Override
	public boolean matches(TypeDescription target) {
		if (target.isAnnotation() || PluginUtils.isCglibProxyType(target)) {
			return false;
		}

		if (implementsValueObject(target)) {
			return true;
		}
		if (hasValueObjectAnnotation(target)) {
			return true;
		}

		Generic superType = target.getSuperClass();
		return target.isRecord()
				|| superType != null && !superType.represents(Object.class) && matches(superType.asErasure());
	}

	@Override
	public KnownFragmentClass jsrFragmentType() {
		return KnownFragmentClass.JDK_ONLY;
	}

	@Override
	public Class<? extends RuntimeException> nullValueExceptionType() {
		return IllegalArgumentException.class;
	}

	private boolean implementsValueObject(TypeDescription target) {
		return !target.getInterfaces().filter(nameMatches(jmoleculesValueObjectInterface)).isEmpty();
	}

	private boolean hasValueObjectAnnotation(TypeDescription target) {
		return Stream.of(target.getDeclaredAnnotations(), target.getInheritedAnnotations()) //
				.flatMap(AnnotationList::stream) //
				.anyMatch(typeIs("org.jmolecules.ddd.annotation.ValueObject"));
	}

	private Predicate<? super AnnotationDescription> typeIs(String annoName) {
		return it -> it.getAnnotationType().getName().equals(annoName);
	}

	@Override
	public boolean include(TypeDescription description) {
		return true;
	}

	@Override
	public boolean customAnnotationsEnabled() {
		return true;
	}

}