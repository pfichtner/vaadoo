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
import static com.github.pfichtner.vaadoo.Jsr380Annos.isStandardJr380Anno;
import static com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy.CustomAnnotations.addCustomAnnotations;
import static com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy.PluginUtils.markGenerated;
import static java.lang.String.format;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toList;
import static java.util.stream.IntStream.range;
import static java.util.stream.Stream.concat;
import static java.util.stream.Stream.empty;
import static net.bytebuddy.implementation.MethodCall.invoke;
import static net.bytebuddy.jar.asm.ClassWriter.COMPUTE_FRAMES;
import static net.bytebuddy.jar.asm.ClassWriter.COMPUTE_MAXS;
import static net.bytebuddy.jar.asm.Opcodes.ACC_PRIVATE;
import static net.bytebuddy.jar.asm.Opcodes.ACC_STATIC;
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
import com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy.config.VaadooConfiguration;

import lombok.Value;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodDescription.InDefinedShape;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.SuperMethodCall;
import net.bytebuddy.implementation.bytecode.ByteCodeAppender;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.jar.asm.Type;
import net.bytebuddy.pool.TypePool;

class VaadooImplementor {

	private static final String VALIDATE_METHOD_BASE_NAME = "validate";

	private final Class<? extends Jsr380CodeFragment> jsr380CodeFragmentClass;

	private final boolean customAnnotationsEnabled;

	public VaadooImplementor(VaadooConfiguration configuration) {
		this.jsr380CodeFragmentClass = configuration.jsr380CodeFragmentClass();
		this.customAnnotationsEnabled = configuration.customAnnotationsEnabled();
	}

	JMoleculesTypeBuilder implementVaadoo(JMoleculesTypeBuilder type, Log log) {
		TypeDescription typeDescription = type.getTypeDescription();
		for (InDefinedShape definedShape : typeDescription.getDeclaredMethods()) {
			if (definedShape.isConstructor()) {
				Parameters parameters = Parameters.of(definedShape.getParameters());

				// Generate a unique method name per constructor
				// TODO we could overload (add validate(String,String) works also if there
				// already is a validate())
				String validateMethodName = nonExistingMethodName(typeDescription, VALIDATE_METHOD_BASE_NAME);

				StaticValidateAppender staticValidateAppender = new StaticValidateAppender(validateMethodName,
						parameters, jsr380CodeFragmentClass, customAnnotationsEnabled);

				if (staticValidateAppender.hasInjections()) {
					type = type.mapBuilder(t -> addStaticValidateMethod(t, staticValidateAppender, log));
					type = type.mapBuilder(
							t -> injectCallToValidateIntoConstructor(t, definedShape, validateMethodName, parameters));
					type = type.mapBuilder(t -> optimizeRegex(t,validateMethodName));
				}
			}
		}
		return type;
	}

	private static Builder<?> optimizeRegex(Builder<?> builder, String validateMethodName) {
		return builder.visit(new AsmVisitorWrapper() {

			@Override
			public int mergeWriter(int flags) {
				return flags;
			}

			@Override
			public int mergeReader(int flags) {
				return flags;
			}

			@Override
			public ClassVisitor wrap(TypeDescription instrumentedType, ClassVisitor classVisitor,
					Implementation.Context implementationContext, TypePool typePool,
					FieldList<FieldDescription.InDefinedShape> fields, MethodList<?> methods, int writerFlags,
					int readerFlags) {
				return new PatternRewriteClassVisitor(classVisitor,validateMethodName);
			}

		});
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

	private Builder<?> addStaticValidateMethod(Builder<?> builder, StaticValidateAppender staticValidateAppender,
			Log log) {
		log.info("Implementing static validate method #{}.", staticValidateAppender.validateMethodName);
		return markGenerated(wrap(builder, COMPUTE_FRAMES | COMPUTE_MAXS)
				.defineMethod(staticValidateAppender.validateMethodName, void.class, ACC_PRIVATE | ACC_STATIC)
				.withParameters(staticValidateAppender.parameters.types())
				.intercept(new Implementation.Simple(staticValidateAppender)));
	}

	private static Builder<?> wrap(Builder<?> builder, int flags) {
		return builder.visit(new AsmVisitorWrapper.ForDeclaredMethods().writerFlags(flags));
	}

	private Builder<?> injectCallToValidateIntoConstructor(Builder<?> builder,
			MethodDescription.InDefinedShape constructor, String validateMethodName, Parameters parameters) {
		var validateMethod = named(validateMethodName).and(takesArguments(parameters.types()));
		return builder.constructor(is(constructor)) //
				.intercept(invoke(validateMethod).withAllArguments().andThen(SuperMethodCall.INSTANCE));
	}

	private static class StaticValidateAppender implements ByteCodeAppender {

		private interface InjectionTask {
			void apply(ValidationCodeInjector injector, MethodVisitor mv);
		}

		@Value(staticConstructor = "of")
		private static class Jsr380AnnoInjectionTask implements InjectionTask {
			Parameter parameter;
			Method fragmentMethod;

			@Override
			public void apply(ValidationCodeInjector injector, MethodVisitor mv) {
				try {
					injector.inject(mv, parameter, fragmentMethod);
				} catch (Exception e) {
					throw new RuntimeException(format("Error injecting %s for %s", fragmentMethod, parameter), e);
				}
			}
		}

		@Value(staticConstructor = "of")
		private static class CustomInjectionTask implements InjectionTask {
			Parameter parameter;
			TypeDescription annotation;

			@Override
			public void apply(ValidationCodeInjector injector, MethodVisitor mv) {
				addCustomAnnotations(mv, parameter, annotation);
			}
		}

		private final String validateMethodName;
		private final Parameters parameters;
		private final Class<? extends Jsr380CodeFragment> fragmentClass;
		private final boolean customAnnotationsEnabled;
		private final List<Method> codeFragmentMethods;
		private final String methodDescriptor;
		private final List<InjectionTask> injectionTasks;

		public StaticValidateAppender(String validateMethodName, Parameters parameters,
				Class<? extends Jsr380CodeFragment> fragmentClass, boolean customAnnotationsEnabled) {
			this.validateMethodName = validateMethodName;
			this.parameters = parameters;
			this.fragmentClass = fragmentClass;
			this.customAnnotationsEnabled = customAnnotationsEnabled;
			this.codeFragmentMethods = Arrays.asList(fragmentClass.getMethods());
			this.methodDescriptor = Type.getMethodDescriptor(Type.VOID_TYPE, parameters.types().stream()
					.map(td -> Type.getType(td.asErasure().getDescriptor())).toArray(Type[]::new));
			this.injectionTasks = parameters.stream().flatMap(this::tasksFor).collect(toList());
		}

		private Stream<InjectionTask> tasksFor(Parameter parameter) {
			return Stream.of(parameter.annotations()).flatMap(a -> concat(jsr380(parameter, a), custom(parameter, a)));
		}

		private Stream<InjectionTask> jsr380(Parameter parameter, TypeDescription annotation) {
			return Jsr380Annos.configs.stream() //
					.filter(c -> annotation.equals(c.type())) //
					.map(c -> codeFragmentMethod(c, parameter.type())) //
					.map(f -> Jsr380AnnoInjectionTask.of(parameter, f));
		}

		private Stream<InjectionTask> custom(Parameter parameter, TypeDescription annotation) {
			return customAnnotationsEnabled && isStandardJr380Anno(annotation) //
					? empty()
					: Stream.of(CustomInjectionTask.of(parameter, annotation));
		}

		public boolean hasInjections() {
			return !injectionTasks.isEmpty();
		}

		@Override
		public Size apply(MethodVisitor mv, Implementation.Context context, MethodDescription instrumentedMethod) {
			ValidationCodeInjector injector = new ValidationCodeInjector(fragmentClass, methodDescriptor);
			for (InjectionTask task : injectionTasks) {
				task.apply(injector, mv);
			}
			mv.visitInsn(Opcodes.RETURN);
			return Size.ZERO;
		}

		private Method codeFragmentMethod(ConfigEntry config, TypeDescription actual) {
			TypeDescription[] parameters = new TypeDescription[] { config.anno(), config.resolveSuperType(actual) };
			return codeFragmentMethod(parameters).map(m -> {
				Class<?> supportedType = m.getParameterTypes()[1];
				if (actual.isAssignableTo(supportedType)) {
					return m;
				}
				throw annotationOnTypeNotValid(parameters[0], actual, List.of(supportedType.getName()));
			}).orElseThrow(() -> unsupportedType(parameters));
		}

		private IllegalStateException unsupportedType(TypeDescription... parameters) {
			assert parameters.length >= 2 : "Expected to get 2 parameters, got " + Arrays.toString(parameters);
			List<String> supported = this.codeFragmentMethods.stream() //
					.filter(StaticValidateAppender::isCodeFragmentMethod) //
					.filter(m -> m.getParameterCount() > 1) //
					.filter(m -> parameters[0].isAssignableTo(m.getParameterTypes()[0])) //
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
					.allMatch(i -> descriptions[i].isAssignableTo(classes[i]));
		}

		private static boolean isCodeFragmentMethod(Method method) {
			return "check".equals(method.getName());
		}

	}

}