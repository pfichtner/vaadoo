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
package org.jmolecules.bytebuddy;

import static java.lang.String.format;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toList;
import static java.util.stream.IntStream.range;
import static net.bytebuddy.matcher.ElementMatchers.is;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.jmolecules.bytebuddy.PluginUtils.markGenerated;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.jmolecules.bytebuddy.PluginLogger.Log;
import org.jmolecules.bytebuddy.vaadoo.Parameters;
import org.jmolecules.bytebuddy.vaadoo.Parameters.Parameter;
import org.jmolecules.bytebuddy.vaadoo.ValidationCodeInjector;
import org.jmolecules.bytebuddy.vaadoo.fragments.impl.JdkOnlyCodeFragment;

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
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodDescription.InDefinedShape;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.implementation.SuperMethodCall;
import net.bytebuddy.implementation.bytecode.ByteCodeAppender;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.jar.asm.Type;

class VaadooImplementor {

	private static final String VALIDATE_METHOD_BASE_NAME = "validate";

	private static class ConfigEntry {

		private final TypeDescription anno;
		private final TypeDescription type;

		public ConfigEntry(Class<? extends Annotation> anno) {
			this.anno = TypeDescription.ForLoadedType.of(anno);
			this.type = this.anno;
		}

		TypeDescription anno() {
			return anno;
		}

		TypeDescription type() {
			return type;
		}

		TypeDescription resolveSuperType(TypeDescription actual) {
			return actual;
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
	private static final List<ConfigEntry> configs = List.of( //
			new FixedClassConfigEntry(Null.class, Object.class), //
			new FixedClassConfigEntry(NotNull.class, Object.class), //
			new FixedClassConfigEntry(NotBlank.class, CharSequence.class), //
			new ConfigEntry(NotEmpty.class) {
				List<TypeDescription> validTypes = Stream
						.of(CharSequence.class, Collection.class, Map.class, Object[].class)
						.map(TypeDescription.ForLoadedType::of).collect(toList());

				@Override
				TypeDescription resolveSuperType(TypeDescription actual) {
					return superType(actual, validTypes).orElseThrow(() -> annotationOnTypeNotValid(anno(), actual,
							validTypes.stream().map(TypeDescription::getActualName).collect(toList())));
				};
			}, //
			new ConfigEntry(Size.class) {
				List<TypeDescription> validTypes = Stream
						.of(CharSequence.class, Collection.class, Map.class, Object[].class)
						.map(TypeDescription.ForLoadedType::of).collect(toList());

				@Override
				TypeDescription resolveSuperType(TypeDescription actual) {
					return superType(actual, validTypes).orElseThrow(() -> annotationOnTypeNotValid(anno(), actual,
							validTypes.stream().map(TypeDescription::getActualName).collect(toList())));
				}

			}, //
			new FixedClassConfigEntry(Pattern.class, CharSequence.class), //
			new FixedClassConfigEntry(Email.class, CharSequence.class), //
			new ConfigEntry(AssertTrue.class), //
			new ConfigEntry(AssertFalse.class), //
			new ConfigEntry(Min.class), //
			new ConfigEntry(Max.class), //
			new ConfigEntry(Digits.class), //
			new ConfigEntry(Positive.class), //
			new ConfigEntry(PositiveOrZero.class), //
			new ConfigEntry(Negative.class), //
			new ConfigEntry(NegativeOrZero.class), //
			new ConfigEntry(DecimalMin.class), //
			new ConfigEntry(DecimalMax.class), //
			new ConfigEntry(Future.class), //
			new ConfigEntry(FutureOrPresent.class), //
			new ConfigEntry(Past.class), //
			new ConfigEntry(PastOrPresent.class) //
	);

	private static final Class<JdkOnlyCodeFragment> FRAGMENT_CLASS = org.jmolecules.bytebuddy.vaadoo.fragments.impl.JdkOnlyCodeFragment.class;

	private static Optional<TypeDescription> superType(TypeDescription classToCheck, List<TypeDescription> superTypes) {
		return superTypes.stream().filter(t -> t.isAssignableFrom(classToCheck)).findFirst();
	}

	private static IllegalStateException annotationOnTypeNotValid(TypeDescription anno, TypeDescription type,
			List<String> valids) {
		return new IllegalStateException(format("Annotation %s on type %s not allowed, allowed only on types: %s",
				anno.getName(), type.getName(), valids));
	}

	private static class FixedClassConfigEntry extends ConfigEntry {

		private final TypeDescription superType;

		public FixedClassConfigEntry(Class<? extends Annotation> anno, Class<?> superType) {
			super(anno);
			this.superType = TypeDescription.ForLoadedType.of(superType);
		}

		@Override
		TypeDescription resolveSuperType(TypeDescription actual) {
			return superType;
		}

	}

	JMoleculesTypeBuilder implementVaadoo(JMoleculesTypeBuilder type, Log log) {
		TypeDescription typeDescription = type.getTypeDescription();

		// Loop over all constructors
		for (InDefinedShape constructor : typeDescription.getDeclaredMethods().stream()
				.filter(MethodDescription::isConstructor).collect(toList())) {
			// Extract constructor parameter types
			Parameters parameters = Parameters.of(constructor.getParameters());

			// Generate a unique method name per constructor
			// TODO we could overload (add validate(String,String) works also if there
			// already is a validate())
			String validateMethodName = nonExistingMethodName(typeDescription, VALIDATE_METHOD_BASE_NAME);

			// Add static validate method
			type = type.mapBuilder(t -> addStaticValidationMethod(t, validateMethodName, parameters, log));

			// Inject call into this constructor
			type = type.mapBuilder(t -> injectValidationIntoConstructor(t, constructor, validateMethodName));
		}

		return type;
	}

	private static String nonExistingMethodName(TypeDescription typeDescription, String base) {
		List<String> methodNames = typeDescription.getDeclaredMethods().stream()
				.map(MethodDescription.InDefinedShape::getName).collect(toList());
		return Stream.iterate(0, i -> i + 1) //
				.map(i -> (i == 0) ? base : base + "_" + i) //
				.filter(not(methodNames::contains)) //
				.findFirst() //
				.get(); // safe because stream is infinite, will always find a free name
	}

	private Builder<?> addStaticValidationMethod(Builder<?> builder, String validateMethodName, Parameters parameters,
			Log log) {
		log.info("Implementing static validate method #{}.", validateMethodName);
		return markGenerated(
				builder.defineMethod(validateMethodName, void.class, Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC)
						.withParameters(parameters.types()).intercept(new Implementation.Simple(
								new StaticValidateAppender(parameters, validateMethodName, FRAGMENT_CLASS))));
	}

	private Builder<?> injectValidationIntoConstructor(Builder<?> builder, MethodDescription.InDefinedShape constructor,
			String validateMethodName) {
		return builder.constructor(is(constructor)) //
				.intercept(MethodCall.invoke(named(validateMethodName)).withAllArguments().andThen( //
						SuperMethodCall.INSTANCE //
				));
	}

	private static class StaticValidateAppender implements ByteCodeAppender {

		private final Parameters parameters;
		private final String validateMethodName;
		private final Class<JdkOnlyCodeFragment> fragmentClass;
		private final List<Method> codeFragmentMethods;

		public StaticValidateAppender(Parameters parameters, String validateMethodName,
				Class<JdkOnlyCodeFragment> fragmentClass) {
			this.parameters = parameters;
			this.validateMethodName = validateMethodName;
			this.fragmentClass = fragmentClass;
			this.codeFragmentMethods = Arrays.asList(fragmentClass.getMethods());
		}

		@Override
		public Size apply(MethodVisitor mv, Implementation.Context context, MethodDescription instrumentedMethod) {
			String methodDescriptor = Type.getMethodDescriptor(Type.VOID_TYPE, parameters.types().stream()
					.map(TypeDescription::getName).map(Type::getObjectType).toArray(Type[]::new));

			ValidationCodeInjector injector = new ValidationCodeInjector(fragmentClass, methodDescriptor);

			for (Parameter parameter : parameters) {
				for (TypeDescription annotation : parameter.annotations()) {
					for (ConfigEntry config : configs) {
						if (annotation.equals(config.type())) {
							injector.inject(mv, parameter, checkMethod(config, parameter.type()));
						}
					}
					// TODO support custom annotations
//					if (customAnnotationsEnabled) {
//					}
				}
			}

			mv.visitInsn(Opcodes.RETURN);
			int maxStack = 4; // or compute dynamically based on emitted instructions
			return new Size(maxStack, parameters.count());
		}

		private Method checkMethod(ConfigEntry config, TypeDescription actual) {
			TypeDescription[] parameters = new TypeDescription[] { config.anno(), config.resolveSuperType(actual) };
			return checkMethod(parameters).map(m -> {
				var supportedType = m.getParameterTypes()[1];
				if (actual.isAssignableTo(supportedType)) {
					return m;
				}
				throw annotationOnTypeNotValid(parameters[0], actual, List.of(supportedType.getName()));
			}).orElseThrow(() -> unsupportedType(parameters));
		}

		private IllegalStateException unsupportedType(TypeDescription... parameters) {
			assert parameters.length >= 2 : "Expected to get 2 parameters, got " + Arrays.toString(parameters);
			var supported = this.codeFragmentMethods.stream() //
					.filter(this::isCheckMethod) //
					.filter(m -> m.getParameterCount() > 1) //
					.filter(m -> parameters[0].represents(m.getParameterTypes()[0])) //
					.map(m -> m.getParameterTypes()[1].getName()) //
					.collect(toList());
			return annotationOnTypeNotValid(parameters[0], parameters[1], supported);
		}

		private Optional<Method> checkMethod(TypeDescription... parameters) {
			return codeFragmentMethods.stream() //
					.filter(this::isCheckMethod) //
					.filter(m -> representsAll(parameters, m.getParameterTypes())) //
					.findFirst();
		}

		private static boolean representsAll(TypeDescription[] descriptions, Class<?>[] classes) {
			return classes.length == descriptions.length && range(0, classes.length) //
					.allMatch(i -> descriptions[i].represents(classes[i]));
		}

		private boolean isCheckMethod(Method method) {
			return "check".equals(method.getName());
		}

	}

}