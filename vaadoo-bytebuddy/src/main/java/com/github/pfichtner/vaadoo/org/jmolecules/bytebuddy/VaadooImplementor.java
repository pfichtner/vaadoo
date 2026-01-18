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

import static com.github.pfichtner.vaadoo.CustomAnnotations.addCustomAnnotations;
import static com.github.pfichtner.vaadoo.Jsr380Annos.annotationOnTypeNotValid;
import static com.github.pfichtner.vaadoo.Jsr380Annos.findRepeatableAnnotationContainers;
import static com.github.pfichtner.vaadoo.Jsr380Annos.isStandardJr380Anno;
import static com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy.PluginUtils.markGenerated;
import static java.lang.String.format;
import static java.lang.reflect.Modifier.isAbstract;
import static java.util.Collections.emptyList;
import static java.util.function.Function.identity;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.IntStream.range;
import static java.util.stream.Stream.concat;
import static java.util.stream.Stream.empty;
import static net.bytebuddy.implementation.MethodCall.invoke;
import static net.bytebuddy.jar.asm.ClassWriter.COMPUTE_FRAMES;
import static net.bytebuddy.jar.asm.ClassWriter.COMPUTE_MAXS;
import static net.bytebuddy.jar.asm.Opcodes.ACC_PRIVATE;
import static net.bytebuddy.jar.asm.Opcodes.ACC_STATIC;
import static net.bytebuddy.jar.asm.Opcodes.ALOAD;
import static net.bytebuddy.jar.asm.Opcodes.ASTORE;
import static net.bytebuddy.jar.asm.Opcodes.GOTO;
import static net.bytebuddy.jar.asm.Opcodes.IFNE;
import static net.bytebuddy.jar.asm.Opcodes.IFNULL;
import static net.bytebuddy.jar.asm.Opcodes.INVOKEINTERFACE;
import static net.bytebuddy.jar.asm.Opcodes.RETURN;
import static net.bytebuddy.matcher.ElementMatchers.is;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import com.github.pfichtner.vaadoo.ConstructorAnnotationRemover;
import com.github.pfichtner.vaadoo.Jsr380Annos;
import com.github.pfichtner.vaadoo.Jsr380Annos.ConfigEntry;
import com.github.pfichtner.vaadoo.Parameters;
import com.github.pfichtner.vaadoo.Parameters.Parameter;
import com.github.pfichtner.vaadoo.PatternRewriteClassVisitor;
import com.github.pfichtner.vaadoo.ValidationCodeInjector;
import com.github.pfichtner.vaadoo.fragments.Jsr380CodeFragment;
import com.github.pfichtner.vaadoo.fragments.impl.Template;
import com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy.PluginLogger.Log;
import com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy.config.CachedVaadooConfiguration;
import com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy.config.VaadooConfiguration;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Pattern.Flag;
import lombok.Value;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.enumeration.EnumerationDescription;
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
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Type;
import net.bytebuddy.pool.TypePool;

class VaadooImplementor {

	private static final String VALIDATE_METHOD_BASE_NAME = "validate";

	private final VaadooConfiguration configuration;

	public VaadooImplementor(VaadooConfiguration configuration) {
		this.configuration = new CachedVaadooConfiguration(configuration);
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
						parameters, configuration);

				if (staticValidateAppender.hasInjections()) {
					type = type.mapBuilder(t -> addStaticValidateMethod(t, staticValidateAppender, log));
					type = type.mapBuilder(
							t -> injectCallToValidateIntoConstructor(t, definedShape, validateMethodName, parameters));

					if (configuration.regexOptimizationEnabled()) {
						type = type
								.mapBuilder(t -> wrap(t, cv -> new PatternRewriteClassVisitor(cv, validateMethodName)));
					}

					if (configuration.removeJsr380Annotations()) {
						type = type.mapBuilder(t -> wrap(t, ConstructorAnnotationRemover::new));
					}
				}
			}
		}
		return type;
	}

	private Builder<?> wrap(Builder<?> builder, Function<ClassVisitor, ClassVisitor> classVisitorProvider) {
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
			public ClassVisitor wrap(TypeDescription instrumentedType, ClassVisitor cv, Implementation.Context context,
					TypePool typePool, FieldList<FieldDescription.InDefinedShape> fields, MethodList<?> methods,
					int writerFlags, int readerFlags) {
				return classVisitorProvider.apply(cv);
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
			AnnotationDescription annotationDescription;

			@Override
			public void apply(ValidationCodeInjector injector, MethodVisitor mv) {
				try {
					@SuppressWarnings("unchecked")
					Class<? extends Jsr380CodeFragment> clazz = (Class<? extends Jsr380CodeFragment>) fragmentMethod
							.getDeclaringClass();
					injector.useFragmentClass(clazz).inject(mv, parameter, fragmentMethod, annotationDescription);
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
			public void apply(ValidationCodeInjector __, MethodVisitor mv) {
				addCustomAnnotations(mv, parameter, annotation);
			}
		}

		private final String validateMethodName;
		private final Parameters parameters;
		private final Map<Parameter, Integer> preComputedPatternFlags;
		private final VaadooConfiguration configuration;
		private final List<Method> fragmentMixinsCodeFragmentMethods;
		private final List<Method> codeFragmentMethods;
		private final String methodDescriptor;
		private final List<InjectionTask> injectionTasks;
		private final List<TypeDescription> jsr380RepeatableAnnotationContainers;

		public StaticValidateAppender(String validateMethodName, Parameters parameters,
				VaadooConfiguration configuration) {
			this.validateMethodName = validateMethodName;
			this.parameters = parameters;
			this.configuration = configuration;
			this.preComputedPatternFlags = computePatternFlagsDuringBuild(parameters);
			this.fragmentMixinsCodeFragmentMethods = configuration.codeFragmentMixins().stream()
					.map(m -> fragmentMethods(m)).flatMap(List::stream).collect(toList());
			this.codeFragmentMethods = fragmentMethods(configuration.jsr380CodeFragmentClass());
			this.methodDescriptor = Type.getMethodDescriptor(Type.VOID_TYPE, parameters.types().stream()
					.map(td -> Type.getType(td.asErasure().getDescriptor())).toArray(Type[]::new));
			this.jsr380RepeatableAnnotationContainers = findRepeatableAnnotationContainers();
			this.injectionTasks = parameters.stream().flatMap(this::tasksFor).collect(toList());
		}

		private static List<Method> fragmentMethods(Class<? extends Jsr380CodeFragment> clazz) {
			return Stream.of(clazz.getMethods()) //
					.filter(m -> m.getDeclaringClass() != Object.class) //
					.filter(m -> !isAbstract(m.getModifiers())) //
					.collect(toList());
		}

		private static Map<Parameter, Integer> computePatternFlagsDuringBuild(Parameters parameters) {
			return parameters.stream().collect(toMap(identity(), p -> {
				Object annotationValue = p.annotationValue(Type.getType(Pattern.class), "flags");
				return annotationValue == null //
						? 0
						: Template.bitwiseOr(Stream.of((EnumerationDescription[]) annotationValue)
								.map(EnumerationDescription::getValue) //
								.map(Flag::valueOf) //
								.toArray(Flag[]::new));
			}));
		}

		private Stream<InjectionTask> tasksFor(Parameter parameter) {
			Stream<InjectionTask> fromParam = Stream.of(parameter.annotations())
					.flatMap(a -> concat(jsr380(parameter, a, null), custom(parameter, a)));

			List<List<AnnotationDescription>> genericAnnotations = parameter.genericAnnotations();
			return genericAnnotations.isEmpty() //
					? fromParam //
					: concat(fromParam, handleGenericAnnotations(parameter, genericAnnotations));
		}

		private Stream<InjectionTask> handleGenericAnnotations(Parameter parameter,
				List<List<AnnotationDescription>> genericAnnotations) {
			TypeDescription.Generic genericType = parameter.genericType();

			// Only process if this is a parameterized type
			if (!genericType.getSort().isParameterized()) {
				return empty();
			}

			List<TypeDescription.Generic> typeArguments = genericType.getTypeArguments();
			return range(0, genericAnnotations.size()).boxed().flatMap(i -> {
				List<AnnotationDescription> typeArgAnnotations = genericAnnotations.get(i);
				if (typeArgAnnotations.isEmpty() || i >= typeArguments.size()) {
					return empty();
				}

				TypeDescription.Generic typeArgument = typeArguments.get(i);
				if (typeArgument == null) {
					return empty();
				}

				// Create injection tasks for each annotation on the type argument
				return typeArgAnnotations.stream().flatMap(annotation -> {
					TypeDescription annotationType = annotation.getAnnotationType();
					if (isStandardJr380Anno(annotationType)) {
						Optional<Method> fragmentMethod = codeFragmentMethod(annotationType, typeArgument.asErasure());
						if (fragmentMethod.isPresent()) {
							return Stream.of(new GenericTypeInjectionTask(parameter, typeArgument.asErasure(),
									fragmentMethod.get(), annotation));
						}
					}
					return empty();
				});
			});
		}

		private Stream<InjectionTask> jsr380(Parameter parameter, TypeDescription annotation,
				AnnotationDescription annotationDescription) {
			return isRepeatableAnnotationContainer(annotation)
					? Stream.of(extractRepeatableAnnotations(parameter, annotation))
							.flatMap(d -> jsr380(parameter, d.getAnnotationType(), d))
					: Jsr380Annos.configs.stream() //
							.filter(c -> annotation.equals(c.type())) //
							.map(c -> codeFragmentMethod(c, parameter.type())) //
							.map(f -> Jsr380AnnoInjectionTask.of(parameter, f, annotationDescription));
		}

		private boolean isRepeatableAnnotationContainer(TypeDescription annotation) {
			return jsr380RepeatableAnnotationContainers.contains(annotation);
		}

		private AnnotationDescription[] extractRepeatableAnnotations(Parameter parameter, TypeDescription annotation) {
			// Convert the annotation type name to a Type and extract the "value" attribute
			// Handle both '.' and '$' separators for inner classes
			String annotationName = annotation.getName();
			String internalName = annotationName.replace('.', '/');
			Type annotationType = Type.getObjectType(internalName);
			Object value = parameter.annotationValue(annotationType, "value");
			return value instanceof AnnotationDescription[] ? (AnnotationDescription[]) value
					: new AnnotationDescription[0];
		}

		private Stream<InjectionTask> custom(Parameter parameter, TypeDescription annotation) {
			return configuration.customAnnotationsEnabled() && isStandardJr380Anno(annotation) //
					? empty()
					: Stream.of(CustomInjectionTask.of(parameter, annotation));
		}

		public boolean hasInjections() {
			return !injectionTasks.isEmpty();
		}

		@Override
		public Size apply(MethodVisitor mv, Implementation.Context context, MethodDescription instrumentedMethod) {
			ValidationCodeInjector injector = new ValidationCodeInjector(configuration.jsr380CodeFragmentClass(),
					methodDescriptor, preComputedPatternFlags, configuration.nullValueExceptionTypeInternalName());
			for (InjectionTask task : injectionTasks) {
				task.apply(injector, mv);
			}
			mv.visitInsn(RETURN);
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
			return concat(fragmentMixinsCodeFragmentMethods.stream(), codeFragmentMethods.stream()) //
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

		private static class GenericTypeInjectionTask implements InjectionTask {
			Parameter parameter;
			TypeDescription elementType;
			Method fragmentMethod;
			AnnotationDescription annotation;

			GenericTypeInjectionTask(Parameter parameter, TypeDescription elementType, Method fragmentMethod,
					AnnotationDescription annotation) {
				this.parameter = parameter;
				this.elementType = elementType;
				this.fragmentMethod = fragmentMethod;
				this.annotation = annotation;
			}

			@Override
			public void apply(ValidationCodeInjector injector, MethodVisitor mv) {
				try {
					generateIterationWithValidation(injector, mv, parameter, annotation);
				} catch (Exception e) {
					throw new RuntimeException(format("Error injecting generic type %s for %s", elementType, parameter),
							e);
				}
			}

			private void generateIterationWithValidation(ValidationCodeInjector injector, MethodVisitor mv,
					Parameter containerParam, AnnotationDescription annotation) {
				Label ifNullLabel = new Label();

				// Generate: if (parameter != null)
				mv.visitVarInsn(ALOAD, containerParam.offset());
				mv.visitJumpInsn(IFNULL, ifNullLabel);

				// Generate: for (Object e : parameter)
				generateForEachLoopWithValidation(injector, mv, containerParam, annotation);

				mv.visitLabel(ifNullLabel);
			}

			private void generateForEachLoopWithValidation(ValidationCodeInjector injector, MethodVisitor mv,
					Parameter containerParam, AnnotationDescription annotation) {
				// Load the container and get its iterator
				mv.visitVarInsn(ALOAD, containerParam.offset());
				mv.visitMethodInsn(INVOKEINTERFACE, "java/lang/Iterable", "iterator", "()Ljava/util/Iterator;", true);

				// Store iterator in a local variable
				int iteratorVar = containerParam.offset() + 1;
				mv.visitVarInsn(ASTORE, iteratorVar);

				Label loopStart = new Label();
				Label loopTest = new Label();

				// Jump to loop condition
				mv.visitJumpInsn(GOTO, loopTest);

				// Loop body label
				mv.visitLabel(loopStart);

				// Load iterator and get next element
				mv.visitVarInsn(ALOAD, iteratorVar);
				mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;", true);

				// Store element in local variable
				int elementVar = iteratorVar + 1;
				mv.visitVarInsn(ASTORE, elementVar);

				// Use the injector to call the fragment method with the element
				// Create a synthetic parameter representing the element
				SyntheticElementParameter elementParam = new SyntheticElementParameter(elementVar, elementType);

				// Call the fragment method using the injector
				@SuppressWarnings("unchecked")
				Class<? extends Jsr380CodeFragment> clazz = (Class<? extends Jsr380CodeFragment>) fragmentMethod
						.getDeclaringClass();
				injector.useFragmentClass(clazz).inject(mv, elementParam, fragmentMethod, annotation);

				// Loop condition: check if hasNext()
				mv.visitLabel(loopTest);
				mv.visitVarInsn(ALOAD, iteratorVar);
				mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true);
				mv.visitJumpInsn(IFNE, loopStart);
			}
		}

		private static class SyntheticElementParameter implements Parameters.Parameter {
			private final int offset;
			private final TypeDescription type;

			SyntheticElementParameter(int offset, TypeDescription type) {
				this.offset = offset;
				this.type = type;
			}

			@Override
			public int index() {
				return 0; // Synthetic parameter, no real index
			}

			@Override
			public int offset() {
				return offset;
			}

			@Override
			public TypeDescription type() {
				return type;
			}

			@Override
			public String name() {
				return "element";
			}

			@Override
			public TypeDescription.Generic genericType() {
				return type.asGenericType();
			}

			@Override
			public TypeDescription[] annotations() {
				return new TypeDescription[0];
			}

			@Override
			public Object annotationValue(Type annotation, String name) {
				return null;
			}

			@Override
			public List<List<AnnotationDescription>> genericAnnotations() {
				return emptyList();
			}

			@Override
			public Object genericAnnotationValue(Type annotation, String name) {
				return null;
			}
		}

	}

}