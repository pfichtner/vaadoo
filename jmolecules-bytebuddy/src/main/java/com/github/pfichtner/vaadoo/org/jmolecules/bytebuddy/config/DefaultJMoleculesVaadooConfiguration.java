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

public class DefaultJMoleculesVaadooConfiguration implements VaadooConfiguration {

	private static final String jmoleculesValueObjectInterface = "org.jmolecules.ddd.types.ValueObject";

	public static Optional<VaadooConfiguration> tryCreate(ClassWorld classWorld) {
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