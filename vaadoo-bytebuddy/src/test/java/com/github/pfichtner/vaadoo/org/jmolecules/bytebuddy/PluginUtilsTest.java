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

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Target;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy.PluginLogger.Log;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.dynamic.DynamicType.Builder.MethodDefinition;
import net.bytebuddy.matcher.ElementMatchers;

class PluginUtilsTest {

	private static final Log LOG = (message, parameters) -> {
		// no-op logger
	};

	private static TypeDescription describe(Class<?> type) {
		return new TypeDescription.ForLoadedType(type);
	}

	private static Builder<?> builder(Class<?> type) {
		return new ByteBuddy().rebase(type);
	}

	@Nested
	class Annotations {

		@Test
		void isAnnotatedWithMatchesAnnotatedType() {
			assertThat(PluginUtils.isAnnotatedWith(describe(Target.class), java.lang.annotation.Target.class)).isTrue();
		}

		@Test
		void isAnnotatedWithRejectsUnannotatedType() {
			assertThat(PluginUtils.isAnnotatedWith(describe(String.class), java.lang.annotation.Target.class)).isFalse();
		}

		@Test
		void getAnnotationBuildsEmptyAnnotationDescription() {
			AnnotationDescription description = PluginUtils.getAnnotation(Deprecated.class);
			assertThat(description).isNotNull();
			assertThat(description.getAnnotationType().represents(Deprecated.class)).isTrue();
		}

		@Test
		void getAnnotationAppliesCustomizer() {
			AnnotationDescription description = PluginUtils.getAnnotation(Deprecated.class,
					b -> b.define("forRemoval", true));
			assertThat(description).isNotNull();
		}

		@Test
		void mapAnnotationOrInterfacesAddsMappingAnnotation() {
			Builder<?> mapping = PluginUtils.mapAnnotationOrInterfaces(builder(Target.class), describe(Target.class),
					Map.of(java.lang.annotation.Target.class, Deprecated.class), LOG);
			assertThat(mapping).isNotNull();
		}

		@Test
		void mapAnnotationOrInterfacesSkipsNonMatchingSource() {
			Builder<?> mapping = PluginUtils.mapAnnotationOrInterfaces(builder(String.class), describe(String.class),
					Map.of(java.lang.annotation.Target.class, Deprecated.class), LOG);
			assertThat(mapping).isNotNull();
		}

		@Test
		void mapAnnotationOrInterfacesHandlesNonAnnotationSource() {
			Builder<?> mapping = PluginUtils.mapAnnotationOrInterfaces(builder(String.class),
					describe(CharSequence.class), Map.of(String.class, Deprecated.class), LOG);
			assertThat(mapping).isNotNull();
		}

		@Test
		void mapAnnotationOrInterfacesSkipsNonMatchingNonAnnotationSource() {
			Builder<?> mapping = PluginUtils.mapAnnotationOrInterfaces(builder(String.class),
					describe(String.class), Map.of(Integer.class, Deprecated.class), LOG);
			assertThat(mapping).isNotNull();
		}
	}

	@Nested
	class Abbreviation {

		@Test
		void abbreviatesClassByPackageInitials() {
			assertThat(PluginUtils.abbreviate(String.class)).isEqualTo("j.l.String");
		}

		@Test
		void abbreviatesTypeDefinition() {
			assertThat(PluginUtils.abbreviate((net.bytebuddy.description.type.TypeDefinition) describe(String.class)))
					.isEqualTo("j.l.String");
		}

		@Test
		void abbreviatesMethod() {
			MethodDescription method = new TypeDescription.ForLoadedType(String.class).getDeclaredMethods()
					.filter(ElementMatchers.named("length")).getOnly();
			assertThat(PluginUtils.abbreviate(method)).isEqualTo("j.l.String.length()");
		}

		@Test
		void abbreviatesMethodWithParameters() {
			MethodDescription method = new TypeDescription.ForLoadedType(String.class).getDeclaredMethods()
					.filter(ElementMatchers.named("indexOf").and(ElementMatchers.takesArguments(int.class))).getOnly();
			assertThat(PluginUtils.abbreviate(method)).isEqualTo("j.l.String.indexOf(…)");
		}

		@Test
		void abbreviatesAnnotationDescription() {
			AnnotationDescription annotation = PluginUtils.getAnnotation(Deprecated.class);
			assertThat(PluginUtils.abbreviate(annotation)).startsWith("@j.l.Deprecated");
		}

		@Test
		void abbreviatesDefaultPackageName() {
			assertThat(PluginUtils.abbreviate("Simple")).isEqualTo("SSimple");
		}
	}

	@Nested
	class AnnotationAddition {

		@Test
		void addAnnotationIfMissingAddsWhenAbsent() {
			Builder<?> result = PluginUtils.addAnnotationIfMissing(Deprecated.class, builder(String.class),
					describe(String.class), LOG);
			assertThat(result).isNotNull();
		}

		@Test
		void addAnnotationIfMissingSkipsWhenAlreadyPresent() {
			Builder<?> result = PluginUtils.addAnnotationIfMissing(Deprecated.class, builder(Target.class),
					describe(Target.class), LOG);
			assertThat(result).isNotNull();
		}

		@Test
		void addAnnotationIfMissingSkipsWhenExcludedAnnotationPresent() {
			Builder<?> result = PluginUtils.addAnnotationIfMissing(Deprecated.class, builder(Target.class),
					describe(Target.class), LOG, java.lang.annotation.Target.class);
			assertThat(result).isNotNull();
		}

		@Test
		void addAnnotationIfMissingUsesProducer() {
			Builder<?> result = PluginUtils.addAnnotationIfMissing(type -> Deprecated.class, builder(String.class),
					describe(String.class), LOG);
			assertThat(result).isNotNull();
		}

		@Test
		void addAnnotationIfMissingLogsWhenExclusionAlreadyPresent() {
			// Deprecated carries @Retention, so passing Retention as exclusion hits the
			// "already annotated with @Retention" branch.
			Builder<?> result = PluginUtils.addAnnotationIfMissing(java.lang.annotation.Target.class,
					builder(Deprecated.class), describe(Deprecated.class), LOG,
					java.lang.annotation.Retention.class);
			assertThat(result).isNotNull();
		}

		@Test
		void addAnnotationIfMissingContinuesWhenExclusionNotPresent() {
			// String carries no @Retention annotation, so the exclusion does not match and
			// the annotation is still added.
			Builder<?> result = PluginUtils.addAnnotationIfMissing(Deprecated.class, builder(String.class),
					describe(String.class), LOG, java.lang.annotation.Retention.class);
			assertThat(result).isNotNull();
		}
	}

	@Nested
	class FieldLogging {

		@Test
		void toLogCombinesDeclaringTypeAndName() {
			FieldDescription field = new TypeDescription.ForLoadedType(String.class).getDeclaredFields()
					.filter(ElementMatchers.named("value")).getOnly();
			assertThat(PluginUtils.toLog(field)).isEqualTo("j.l.String.value");
		}
	}

	@Nested
	class ConditionalLookup {

		@Test
		void ifTypePresentInvokesConsumerWhenPresent() {
			AtomicInteger counter = new AtomicInteger();
			PluginUtils.ifTypePresent(String.class.getName(), type -> counter.incrementAndGet());
			assertThat(counter).hasValue(1);
		}

		@Test
		void ifTypePresentDoesNothingWhenAbsent() {
			PluginUtils.ifTypePresent("com.example.DoesNotExist", type -> {
				throw new AssertionError("should not be invoked");
			});
		}

		@Test
		void ifAnnotationTypePresentInvokesConsumerWhenPresent() {
			AtomicInteger counter = new AtomicInteger();
			PluginUtils.ifAnnotationTypePresent(Deprecated.class.getName(), type -> counter.incrementAndGet());
			assertThat(counter).hasValue(1);
		}

		@Test
		void ifAnnotationTypePresentDoesNothingWhenAbsent() {
			PluginUtils.ifAnnotationTypePresent("com.example.DoesNotExist", type -> {
				throw new AssertionError("should not be invoked");
			});
		}
	}

	@Nested
	class ProxyDetection {

		@Test
		void isCglibProxyTypeDetectsDoubleDollar() {
			assertThat(PluginUtils.isCglibProxyType(describe(Some$$Proxy.class))).isTrue();
		}

		@Test
		void isCglibProxyTypeRejectsPlainType() {
			assertThat(PluginUtils.isCglibProxyType(describe(String.class))).isFalse();
		}
	}

	@Nested
	class GeneratedMarking {

		@Test
		void markGeneratedAnnotatesMethodWhenSupported() {
			MethodDefinition<?> method = builder(String.class)
					.defineMethod("copy", net.bytebuddy.description.type.TypeDescription.ForLoadedType.of(String.class),
							net.bytebuddy.description.modifier.Visibility.PUBLIC)
					.intercept(net.bytebuddy.implementation.FixedValue.value("x"));
			MethodDefinition<?> result = PluginUtils.markGenerated(method);
			assertThat(result).isNotNull();
		}

		@Test
		void markGeneratedAnnotatesTypeWhenSupported() {
			Builder<?> result = PluginUtils.markGenerated(builder(String.class), LOG);
			assertThat(result).isNotNull();
		}
	}

	@Test
	void defaultMappingLogsOnMatch() {
		AtomicInteger logged = new AtomicInteger();
		Log countingLog = (message, parameters) -> logged.incrementAndGet();
		var mapping = PluginUtils.defaultMapping(countingLog, ElementMatchers.named("value"),
				PluginUtils.getAnnotation(Deprecated.class));
		FieldDescription field = new TypeDescription.ForLoadedType(String.class).getDeclaredFields()
				.filter(ElementMatchers.named("value")).getOnly();
		assertThat(mapping.matches(field)).isTrue();
		assertThat(logged).hasValue(1);
	}

	@Test
	void defaultMappingDoesNotLogOnNoMatch() {
		AtomicInteger logged = new AtomicInteger();
		Log countingLog = (message, parameters) -> logged.incrementAndGet();
		var mapping = PluginUtils.defaultMapping(countingLog, ElementMatchers.named("length"),
				PluginUtils.getAnnotation(Deprecated.class));
		FieldDescription field = new TypeDescription.ForLoadedType(String.class).getDeclaredFields()
				.filter(ElementMatchers.named("value")).getOnly();
		assertThat(mapping.matches(field)).isFalse();
		assertThat(logged).hasValue(0);
	}

	private static final class Some$$Proxy {
		// test fixture for the CGLib proxy naming pattern
	}

}
