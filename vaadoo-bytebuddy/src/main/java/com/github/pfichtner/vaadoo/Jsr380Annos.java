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
package com.github.pfichtner.vaadoo;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Negative;
import jakarta.validation.constraints.NegativeOrZero;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.jar.asm.Type;

public class Jsr380Annos {

	public static interface ConfigEntry {

		TypeDescription anno();

		TypeDescription type();

		TypeDescription resolveSuperType(TypeDescription actual);

	}

	private static class DefaultConfigEntry implements ConfigEntry {

		private final TypeDescription anno;
		private final TypeDescription type;

		public DefaultConfigEntry(Class<? extends Annotation> anno) {
			this.anno = type(anno);
			this.type = type(anno);
		}

		@Override
		public TypeDescription anno() {
			return anno;
		}

		@Override
		public TypeDescription type() {
			return type;
		}

		@Override
		public TypeDescription resolveSuperType(TypeDescription actual) {
			return actual;
		}

		static TypeDescription type(Class<?> clazz) {
			return TypeDescription.ForLoadedType.of(clazz);
		}

		static List<TypeDescription> descriptors(Class<?>... classses) {
			return Stream.of(classses).map(DefaultConfigEntry::type).collect(toList());
		}

	}

	private static class MultiClassConfigEntry extends DefaultConfigEntry {

		private final List<TypeDescription> supportedSuperTypes;

		public MultiClassConfigEntry(Class<? extends Annotation> anno, Class<?>... supportedSuperTypes) {
			super(anno);
			this.supportedSuperTypes = descriptors(supportedSuperTypes);
		}

		@Override
		public TypeDescription resolveSuperType(TypeDescription actual) {
			return superType(actual, supportedSuperTypes).orElseThrow(() -> annotationOnTypeNotValid(anno(), actual,
					supportedSuperTypes.stream().map(TypeDescription::getActualName).collect(toList())));
		}

	}

	private static class SingleClassConfigEntry extends DefaultConfigEntry {

		private final TypeDescription superType;

		public SingleClassConfigEntry(Class<? extends Annotation> anno, Class<?> superType) {
			super(anno);
			this.superType = TypeDescription.ForLoadedType.of(superType);
		}

		@Override
		public TypeDescription resolveSuperType(TypeDescription actual) {
			return superType;
		}

	}

	// TODO Possible checks during compile time: (but all these checks could be a
	// separate project as well,
	// https://mvnrepository.com/artifact/org.hibernate.validator/hibernate-validator-annotation-processor)
	// errors
	// - Annotations on unsupported types, e.g. @Past on String <-- TODO already
	// handled, right!?
	// - @Pattern: Is the pattern valid (compile it)
	// - @Size: Is min >= 0
	// - @Min: Is there a @Max that is < @Min's value
	// - @Max: Is there a @Min that is < @Max's value
	// - @NotNull: Is there also @Null
	// - @Null: Is there also @NotNull
	// warnings
	// - @NotNull: Annotations that checks for null as well like @NotBlank @NotEmpty
	// - @Null: most (all?) other annotations doesn't make sense
	public static final List<ConfigEntry> configs = List.of( //
			new SingleClassConfigEntry(Null.class, Object.class), //
			new SingleClassConfigEntry(NotNull.class, Object.class), //
			new SingleClassConfigEntry(NotBlank.class, CharSequence.class), //
			new MultiClassConfigEntry(NotEmpty.class, CharSequence.class, Collection.class, Map.class, Object[].class), //
			new MultiClassConfigEntry(Size.class, CharSequence.class, Collection.class, Map.class, Object[].class), //
			new SingleClassConfigEntry(Pattern.class, CharSequence.class), //
			new SingleClassConfigEntry(Email.class, CharSequence.class), //
			new DefaultConfigEntry(AssertTrue.class), //
			new DefaultConfigEntry(AssertFalse.class), //
			new DefaultConfigEntry(Min.class), //
			new DefaultConfigEntry(Max.class), //
			new DefaultConfigEntry(Digits.class), //
			new DefaultConfigEntry(Positive.class), //
			new DefaultConfigEntry(PositiveOrZero.class), //
			new DefaultConfigEntry(Negative.class), //
			new DefaultConfigEntry(NegativeOrZero.class), //
			new DefaultConfigEntry(DecimalMin.class), //
			new DefaultConfigEntry(DecimalMax.class), //
			new DefaultConfigEntry(Future.class), //
			new DefaultConfigEntry(FutureOrPresent.class), //
			new DefaultConfigEntry(Past.class), //
			new DefaultConfigEntry(PastOrPresent.class) //
	);

	private static Optional<TypeDescription> superType(TypeDescription classToCheck, List<TypeDescription> superTypes) {
		return superTypes.stream().filter(t -> t.isAssignableFrom(classToCheck)).findFirst();
	}

	public static IllegalStateException annotationOnTypeNotValid(TypeDescription anno, TypeDescription type,
			List<String> valids) {
		return new IllegalStateException(format("Annotation %s on type %s not allowed, allowed only on types: %s",
				anno.getName(), type.getName(), valids));
	}

	public static boolean isStandardJr380Anno(TypeDescription type) {
		return configs.stream().map(ConfigEntry::anno).anyMatch(type::equals);
	}

	public static boolean isStandardJr380Anno(Type type) {
		return configs.stream().map(ConfigEntry::anno).map(TypeDescription::getDescriptor)
				.anyMatch(type.getDescriptor()::equals);
	}

}