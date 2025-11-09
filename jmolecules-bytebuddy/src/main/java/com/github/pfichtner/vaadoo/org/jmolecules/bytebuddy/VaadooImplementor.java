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

import static com.github.pfichtner.vaadoo.Jsr380Annos.annotationOnTypeNotValid;
import static com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy.PluginUtils.markGenerated;
import static java.lang.String.format;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toList;
import static java.util.stream.IntStream.range;
import static net.bytebuddy.jar.asm.ClassWriter.COMPUTE_FRAMES;
import static net.bytebuddy.jar.asm.ClassWriter.COMPUTE_MAXS;
import static net.bytebuddy.jar.asm.Opcodes.ACC_PRIVATE;
import static net.bytebuddy.jar.asm.Opcodes.ACC_STATIC;
import static net.bytebuddy.jar.asm.Type.VOID_TYPE;
import static net.bytebuddy.matcher.ElementMatchers.is;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.github.pfichtner.vaadoo.Jsr380Annos;
import com.github.pfichtner.vaadoo.Jsr380Annos.ConfigEntry;
import com.github.pfichtner.vaadoo.Parameters;
import com.github.pfichtner.vaadoo.Parameters.Parameter;
import com.github.pfichtner.vaadoo.ValidationCodeInjector;
import com.github.pfichtner.vaadoo.fragments.Jsr380CodeFragment;
import com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy.PluginLogger.Log;

import net.bytebuddy.asm.AsmVisitorWrapper;
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

	private static final Class<? extends Jsr380CodeFragment> FRAGMENT_CLASS = com.github.pfichtner.vaadoo.fragments.impl.JdkOnlyCodeFragment.class;

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
			type = type.mapBuilder(t -> addStaticValidateMethod(t, validateMethodName, parameters, log));

			// Inject call into this constructor
			type = type.mapBuilder(
					t -> injectCallToValidateIntoConstructor(t, constructor, validateMethodName, parameters));
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

	private Builder<?> addStaticValidateMethod(Builder<?> builder, String validateMethodName, Parameters parameters,
			Log log) {
		log.info("Implementing static validate method #{}.", validateMethodName);
		return markGenerated(wrap(builder, COMPUTE_FRAMES | COMPUTE_MAXS)
				.defineMethod(validateMethodName, void.class, ACC_PRIVATE | ACC_STATIC)
				.withParameters(parameters.types()).intercept( //
						new Implementation.Simple(
								new StaticValidateAppender(parameters, validateMethodName, FRAGMENT_CLASS))));
	}

	private static Builder<?> wrap(Builder<?> builder, int flags) {
		return builder.visit(new AsmVisitorWrapper.ForDeclaredMethods().writerFlags(flags));
	}

	private Builder<?> injectCallToValidateIntoConstructor(Builder<?> builder,
			MethodDescription.InDefinedShape constructor, String validateMethodName, Parameters parameters) {
		return builder.constructor(is(constructor)) //
				.intercept( //
						MethodCall.invoke(named(validateMethodName).and(takesArguments(parameters.types()))) //
								.withAllArguments().andThen( //
										SuperMethodCall.INSTANCE //
								));
	}

	private static class StaticValidateAppender implements ByteCodeAppender {

		private final Parameters parameters;
		private final String validateMethodName;
		private final Class<? extends Jsr380CodeFragment> fragmentClass;
		private final List<Method> codeFragmentMethods;

		public StaticValidateAppender(Parameters parameters, String validateMethodName,
				Class<? extends Jsr380CodeFragment> fragmentClass) {
			this.parameters = parameters;
			this.validateMethodName = validateMethodName;
			this.fragmentClass = fragmentClass;
			this.codeFragmentMethods = Arrays.asList(fragmentClass.getMethods());
		}

		@Override
		public Size apply(MethodVisitor mv, Implementation.Context context, MethodDescription instrumentedMethod) {
			String methodDescriptor = Type.getMethodDescriptor(Type.VOID_TYPE, parameters.types().stream()
					.map(td -> Type.getType(td.asErasure().getDescriptor())).toArray(Type[]::new));

			ValidationCodeInjector injector = new ValidationCodeInjector(fragmentClass, methodDescriptor);

			for (Parameter parameter : parameters) {
				for (TypeDescription annotation : parameter.annotations()) {
					for (ConfigEntry config : Jsr380Annos.configs) {
						if (annotation.equals(config.type())) {
							Method codeFragmentMethod = codeFragmentMethod(config, parameter.type());
							try {
								injector.inject(mv, parameter, codeFragmentMethod);
							} catch (Exception e) {
								throw new RuntimeException(
										format("Error injecting %s for %s", codeFragmentMethod, parameter), e);
							}
						}
					}
					// TODO support custom annotations
//					if (customAnnotationsEnabled) {
//					}
				}
			}

			mv.visitInsn(Opcodes.RETURN);
			return Size.ZERO;
		}

		private Method codeFragmentMethod(ConfigEntry config, TypeDescription actual) {
			TypeDescription[] parameters = new TypeDescription[] { config.anno(), config.resolveSuperType(actual) };
			return codeFragmentMethod(parameters).map(m -> {
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
					.filter(StaticValidateAppender::isCodeFragmentMethod) //
					.filter(m -> m.getParameterCount() > 1) //
					.filter(m -> parameters[0].represents(m.getParameterTypes()[0])) //
					.map(m -> m.getParameterTypes()[1].getName()) //
					.collect(toList());
			return annotationOnTypeNotValid(parameters[0], parameters[1], supported);
		}

		private Optional<Method> codeFragmentMethod(TypeDescription... parameters) {
			return codeFragmentMethods.stream() //
					.filter(StaticValidateAppender::isCodeFragmentMethod) //
					.filter(m -> equals(parameters, m.getParameterTypes())) //
					.findFirst();
		}

		private static boolean equals(TypeDescription[] descriptions, Class<?>[] classes) {
			return classes.length == descriptions.length && range(0, classes.length) //
					.allMatch(i -> descriptions[i].represents(classes[i]));
		}

		private static boolean isCodeFragmentMethod(Method method) {
			return "check".equals(method.getName());
		}

	}

}