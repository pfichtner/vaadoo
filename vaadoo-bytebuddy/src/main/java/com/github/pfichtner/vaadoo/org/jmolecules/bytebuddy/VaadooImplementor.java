/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the \"License\");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an \"AS IS\" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy;

import static com.github.pfichtner.vaadoo.ByteBuddyUtil.toTypes;
import static java.lang.reflect.Modifier.isStatic;
import static java.util.Arrays.stream;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static net.bytebuddy.jar.asm.ClassWriter.COMPUTE_FRAMES;
import static net.bytebuddy.jar.asm.ClassWriter.COMPUTE_MAXS;
import static net.bytebuddy.jar.asm.Opcodes.AALOAD;
import static net.bytebuddy.jar.asm.Opcodes.ACC_PRIVATE;
import static net.bytebuddy.jar.asm.Opcodes.ACC_STATIC;
import static net.bytebuddy.jar.asm.Opcodes.ACONST_NULL;
import static net.bytebuddy.jar.asm.Opcodes.ALOAD;
import static net.bytebuddy.jar.asm.Opcodes.ASTORE;
import static net.bytebuddy.jar.asm.Opcodes.CHECKCAST;
import static net.bytebuddy.jar.asm.Opcodes.GOTO;
import static net.bytebuddy.jar.asm.Opcodes.IALOAD;
import static net.bytebuddy.jar.asm.Opcodes.ICONST_0;
import static net.bytebuddy.jar.asm.Opcodes.IFNE;
import static net.bytebuddy.jar.asm.Opcodes.IF_ICMPGE;
import static net.bytebuddy.jar.asm.Opcodes.ILOAD;
import static net.bytebuddy.jar.asm.Opcodes.INVOKEINTERFACE;
import static net.bytebuddy.jar.asm.Opcodes.ISTORE;
import static net.bytebuddy.jar.asm.Opcodes.RETURN;
import static net.bytebuddy.matcher.ElementMatchers.is;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import com.github.pfichtner.vaadoo.AsmUtil;
import com.github.pfichtner.vaadoo.ConstructorAnnotationRemover;
import com.github.pfichtner.vaadoo.CustomAnnotations;
import com.github.pfichtner.vaadoo.Jsr380Annos;
import com.github.pfichtner.vaadoo.Jsr380Annos.ConfigEntry;
import com.github.pfichtner.vaadoo.PatternRewriteClassVisitor;
import com.github.pfichtner.vaadoo.Parameters;
import com.github.pfichtner.vaadoo.Parameters.Parameter;
import com.github.pfichtner.vaadoo.ValidationCodeInjector;
import com.github.pfichtner.vaadoo.fragments.Jsr380CodeFragment;
import com.github.pfichtner.vaadoo.fragments.impl.Template;
import com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy.PluginLogger.Log;
import com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy.config.CachedVaadooConfiguration;
import com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy.config.VaadooConfiguration;

import jakarta.validation.constraints.Pattern;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.experimental.Delegate;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.annotation.AnnotationValue;
import net.bytebuddy.description.enumeration.EnumerationDescription;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodDescription.InDefinedShape;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeList;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.implementation.SuperMethodCall;
import net.bytebuddy.implementation.bytecode.ByteCodeAppender;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Type;
import net.bytebuddy.pool.TypePool;

class VaadooImplementor {

	private static final String VALIDATE_METHOD_BASE_NAME = "validate";

	private final VaadooConfiguration configuration;

	public VaadooImplementor(VaadooConfiguration configuration) {
		this.configuration = CachedVaadooConfiguration.cachedConfiguration(configuration);
	}

	JMoleculesTypeBuilder implementVaadoo(JMoleculesTypeBuilder type, Log log, ClassReader cr) {
		TypeDescription typeDescription = type.getTypeDescription();
		List<String> usedMethodNames = new ArrayList<>(typeDescription.getDeclaredMethods().stream()
				.map(MethodDescription.InDefinedShape::getName).collect(toList()));
		Set<String> allGeneratedValidateMethodNames = new HashSet<>();
		int firstTargetLineNumber = -1;

		for (InDefinedShape definedShape : typeDescription.getDeclaredMethods()) {
			if (definedShape.isConstructor()) {
				int targetLineNumber = AsmUtil.firstLineNumber(cr, definedShape.getInternalName(),
						definedShape.getDescriptor());
				if (firstTargetLineNumber == -1) {
					firstTargetLineNumber = targetLineNumber;
				}

				Parameters parameters = Parameters.of(definedShape.getParameters(), typeDescription);
				Implementation.Composable centralValidateImpl = null;

				// We iterate backwards to build the chain so the calls are in the correct
				// order:
				// validate_p1, validate_p2, ...
				for (int i = parameters.count() - 1; i >= 0; i--) {
					Parameter parameter = parameters.parameter(i);
					String validateParamMethodName = nonExistingMethodName(usedMethodNames,
							VALIDATE_METHOD_BASE_NAME + "_" + parameter.name());
					StaticValidateAppender parameterAppender = new StaticValidateAppender(validateParamMethodName,
							parameter, configuration, targetLineNumber);

					if (parameterAppender.hasInjections()) {
						usedMethodNames.add(validateParamMethodName);
						allGeneratedValidateMethodNames.add(validateParamMethodName);
						type = type.mapBuilder(t -> addStaticValidateMethod(t, parameterAppender));

						Implementation.Composable invokeParam = MethodCall.invoke(
								named(validateParamMethodName).and(takesArguments(parameter.type()))).withArgument(i);
						centralValidateImpl = centralValidateImpl == null ? invokeParam
								: invokeParam.andThen(centralValidateImpl);
					}
				}

				if (centralValidateImpl != null) {
					String centralValidateName = nonExistingMethodName(usedMethodNames, VALIDATE_METHOD_BASE_NAME);
					usedMethodNames.add(centralValidateName);
					allGeneratedValidateMethodNames.add(centralValidateName);

					final Implementation finalCentralImpl = new Implementation.Compound(
							new Implementation.Simple((mv, context, instrumentedMethod) -> {
								Label label = new Label();
								mv.visitLabel(label);
								if (targetLineNumber != -1) {
									mv.visitLineNumber(targetLineNumber, label);
								}
								return new ByteCodeAppender.Size(0, 0);
							}), (Implementation) centralValidateImpl);
					type = type.mapBuilder(t -> markGenerated(wrap(t, COMPUTE_FRAMES | COMPUTE_MAXS)
							.defineMethod(centralValidateName, void.class, ACC_PRIVATE | ACC_STATIC)
							.withParameters(parameters.types()).intercept(finalCentralImpl)));

					type = type.mapBuilder(t -> t.constructor(is(definedShape))
							.intercept(MethodCall.invoke(named(centralValidateName).and(takesArguments(parameters.types())))
									.withAllArguments().andThen(SuperMethodCall.INSTANCE)));
				}
			}
		}

		if (!allGeneratedValidateMethodNames.isEmpty()) {
			final int patternTargetLineNumber = firstTargetLineNumber;
			type = type.mapBuilder(t -> t.visit(new AsmVisitorWrapper.AbstractBase() {
				@Override
				public ClassVisitor wrap(TypeDescription instrumentedType, ClassVisitor classVisitor,
						Implementation.Context implementationContext, TypePool typePool,
						FieldList<FieldDescription.InDefinedShape> fields, MethodList<?> methods, int writerFlags,
						int readerFlags) {
					ClassVisitor cv = new ConstructorAnnotationRemover(classVisitor, configuration);
					if (configuration.regexOptimizationEnabled()) {
						cv = new PatternRewriteClassVisitor(cv, allGeneratedValidateMethodNames,
								patternTargetLineNumber);
					}
					return cv;
				}
			}));
		}

		return type;
	}

	private String nonExistingMethodName(List<String> usedMethodNames, String baseName) {
		String methodName = baseName;
		int i = 0;
		while (usedMethodNames.contains(methodName)) {
			methodName = baseName + i++;
		}
		return methodName;
	}

	private static Builder<?> markGenerated(Builder<?> builder) {
		return builder;
	}

	private static Builder<?> addStaticValidateMethod(Builder<?> builder, StaticValidateAppender staticValidateAppender) {
		return markGenerated(wrap(builder, COMPUTE_FRAMES | COMPUTE_MAXS)
				.defineMethod(staticValidateAppender.validateMethodName, void.class, ACC_PRIVATE | ACC_STATIC)
				.withParameter(staticValidateAppender.parameter.type(), staticValidateAppender.parameter.name())
				.intercept(new Implementation.Simple(staticValidateAppender)));
	}

	private static Builder<?> wrap(Builder<?> builder, int flags) {
		return builder.visit(new AsmVisitorWrapper.ForDeclaredMethods().writerFlags(flags));
	}

	@EqualsAndHashCode
	@RequiredArgsConstructor
	private static class ParameterWithOffsetZero implements Parameter {
		@Delegate
		private final Parameter delegate;

		@Override
		public int offset() {
			return 0;
		}

		@Override
		public AnnotationDescription annotation(TypeDescription type) {
			return delegate.annotation(type);
		}
	}

	private static class StaticValidateAppender implements ByteCodeAppender {

		private final String validateMethodName;
		private final Parameter parameter;
		private final VaadooConfiguration configuration;
		private final Map<Parameter, Integer> preComputedPatternFlags;
		private final String methodDescriptor;

		private final List<Method> codeFragmentMethods;
		private final List<InjectionTask> injectionTasks;
		private final List<TypeDescription> jsr380RepeatableAnnotationContainers;
		private final int targetLineNumber;

		public StaticValidateAppender(String validateMethodName, Parameter parameter,
				VaadooConfiguration configuration, int targetLineNumber) {
			this.validateMethodName = validateMethodName;
			this.parameter = new ParameterWithOffsetZero(parameter);
			this.configuration = configuration;
			this.targetLineNumber = targetLineNumber;
			this.preComputedPatternFlags = computePatternFlagsDuringBuild(this.parameter);
			this.jsr380RepeatableAnnotationContainers = Jsr380Annos.findRepeatableAnnotationContainers();
			this.codeFragmentMethods = fragmentMethods(configuration.jsr380CodeFragmentClass());
			this.methodDescriptor = Type.getMethodDescriptor(Type.VOID_TYPE,
					Type.getType(this.parameter.type().getDescriptor()));
			this.injectionTasks = tasksFor(this.parameter);
		}

		private Map<Parameter, Integer> computePatternFlagsDuringBuild(Parameter parameter) {
			Map<Parameter, Integer> map = new HashMap<>();
			Object annotationValue = parameter.annotationValue(Type.getType(Pattern.class), "flags");
			map.put(parameter, annotationValue == null //
					? 0
					: Template.bitwiseOr(Stream.of((EnumerationDescription[]) annotationValue) //
							.map(EnumerationDescription::getValue) //
							.map(jakarta.validation.constraints.Pattern.Flag::valueOf) //
							.toArray(jakarta.validation.constraints.Pattern.Flag[]::new)));
			return map;
		}

		private List<Method> fragmentMethods(Class<?> fragmentClass) {
			return Stream.of(fragmentClass.getMethods()).filter(m -> !isStatic(m.getModifiers())).collect(toList());
		}

		private List<InjectionTask> tasksFor(Parameter parameter) {
			List<InjectionTask> tasks = new ArrayList<>();
			Arrays.stream(parameter.annotations()).flatMap(a -> jsr380(parameter, a)).forEach(tasks::add);
			Arrays.stream(parameter.annotations()).flatMap(a -> custom(parameter, a)).forEach(tasks::add);

			List<List<AnnotationDescription>> genericAnnotations = parameter.genericAnnotations();
			for (int i = 0; i < genericAnnotations.size(); i++) {
				final int index = i;
				for (AnnotationDescription annotation : genericAnnotations.get(i)) {
					Jsr380Annos.configs.stream()
							.filter(c -> annotation.getAnnotationType().asErasure().equals(c.anno()))
							.forEach(c -> tasks.add(GenericTypeInjectionTask.of(parameter, c, annotation, index)));
				}
			}
			return tasks;
		}

		private Stream<InjectionTask> custom(Parameter parameter, TypeDescription annotationType) {
			return configuration.customAnnotationsEnabled()
					? Stream.of(new CustomAnnoInjectionTask(parameter, annotationType))
					: Stream.empty();
		}

		private Stream<InjectionTask> jsr380(Parameter parameter, TypeDescription annotationType) {
			AnnotationDescription annotation = stream(parameter.annotations())
					.filter(a -> a.equals(annotationType)).findFirst().map(a -> parameter.annotation(a)).orElse(null);
			if (jsr380RepeatableAnnotationContainers.contains(annotationType)) {
				AnnotationValue<?, ?> value = annotation.getValue("value");
				return stream(value.resolve(AnnotationDescription[].class)).flatMap(a -> jsr380(parameter, a));
			}

			return Jsr380Annos.configs.stream() //
					.filter(c -> annotationType.equals(c.anno())) //
					.map(c -> codeFragmentMethod(c, parameter.type())) //
					.map(f -> Jsr380AnnoInjectionTask.of(parameter, f, annotation));
		}

		private Stream<InjectionTask> jsr380(Parameter parameter, AnnotationDescription annotation) {
			TypeDescription annotationType = annotation.getAnnotationType();
			return Jsr380Annos.configs.stream() //
					.filter(c -> annotationType.equals(c.anno())) //
					.map(c -> codeFragmentMethod(c, parameter.type())) //
					.map(f -> Jsr380AnnoInjectionTask.of(parameter, f, annotation));
		}

		private Method codeFragmentMethod(ConfigEntry config, TypeDescription actual) {
			return codeFragmentMethod(config, actual, codeFragmentMethods);
		}

		private static Class<?> loadClass(String name) {
			switch (name) {
			case "boolean": return boolean.class;
			case "byte": return byte.class;
			case "char": return char.class;
			case "short": return short.class;
			case "int": return int.class;
			case "long": return long.class;
			case "float": return float.class;
			case "double": return double.class;
			case "void": return void.class;
			default:
				try {
					return Class.forName(name);
				} catch (ClassNotFoundException e) {
					throw new IllegalStateException(e);
				}
			}
		}

		public boolean hasInjections() {
			return !injectionTasks.isEmpty();
		}

		@Override
		public Size apply(MethodVisitor mv, Implementation.Context context, MethodDescription instrumentedMethod) {
			Label label = new Label();
			mv.visitLabel(label);
			if (targetLineNumber != -1) {
				mv.visitLineNumber(targetLineNumber, label);
			}
			int argsSize = (int) parameter.type().getStackSize().getSize();
			ValidationCodeInjector injector = new ValidationCodeInjector(configuration.jsr380CodeFragmentClass(),
					methodDescriptor, preComputedPatternFlags, configuration.nullValueExceptionTypeInternalName())
					.withTargetLineNumber(targetLineNumber).withLocalsOffset(20);
			for (InjectionTask task : injectionTasks) {
				task.apply(injector, mv, argsSize);
			}
			mv.visitInsn(RETURN);
			return Size.ZERO;
		}

		private interface InjectionTask {
			void apply(ValidationCodeInjector injector, MethodVisitor mv, int argsSize);
		}

		@Value(staticConstructor = "of")
		private static class Jsr380AnnoInjectionTask implements InjectionTask {
			Parameter parameter;
			Method fragmentMethod;
			AnnotationDescription annotationDescription;

			@Override
			public void apply(ValidationCodeInjector injector, MethodVisitor mv, int argsSize) {
				injector.inject(mv, parameter, fragmentMethod, annotationDescription);
			}
		}

		@Value
		private class CustomAnnoInjectionTask implements InjectionTask {
			Parameter parameter;
			TypeDescription annotation;

			@Override
			public void apply(ValidationCodeInjector injector, MethodVisitor mv, int argsSize) {
				CustomAnnotations.addCustomAnnotations(mv, parameter, annotation);
			}
		}

		@Value(staticConstructor = "of")
		private static class GenericTypeInjectionTask implements InjectionTask {
			Parameter parameter;
			ConfigEntry config;
			AnnotationDescription annotation;
			int index;

			@Override
			public void apply(ValidationCodeInjector injector, MethodVisitor mv, int argsSize) {
				TypeDescription type = parameter.type();
				if (type.isAssignableTo(Map.class)) {
					generateMapLoopWithValidation(injector, mv, parameter, annotation, argsSize);
				} else if (type.isArray()) {
					generateArrayLoopWithValidation(injector, mv, parameter, annotation, argsSize);
				} else if (type.isAssignableTo(Iterable.class)) {
					generateForEachLoopWithValidation(injector, mv, parameter, annotation, argsSize);
				}
			}

			private void generateArrayLoopWithValidation(ValidationCodeInjector injector, MethodVisitor mv,
					Parameter containerParam, AnnotationDescription annotation, int argsSize) {
				mv.visitVarInsn(ALOAD, containerParam.offset());
				mv.visitInsn(net.bytebuddy.jar.asm.Opcodes.ARRAYLENGTH);
				int lengthVar = argsSize;
				mv.visitVarInsn(ISTORE, lengthVar);
				int indexVar = lengthVar + 1;
				mv.visitInsn(ICONST_0);
				mv.visitVarInsn(ISTORE, indexVar);
				int elementVar = indexVar + 1;
				mv.visitInsn(ACONST_NULL);
				mv.visitVarInsn(ASTORE, elementVar);

				Label loopStart = new Label();
				Label loopEnd = new Label();
				mv.visitLabel(loopStart);
				mv.visitVarInsn(ILOAD, indexVar);
				mv.visitVarInsn(ILOAD, lengthVar);
				mv.visitJumpInsn(IF_ICMPGE, loopEnd);
				mv.visitVarInsn(ALOAD, containerParam.offset());
				mv.visitVarInsn(ILOAD, indexVar);

				TypeDescription elementType = containerParam.type().getComponentType();
				if (elementType.isPrimitive()) {
					Type primitiveType = Type.getType(elementType.getDescriptor());
					mv.visitInsn(primitiveType.getOpcode(IALOAD));
					mv.visitVarInsn(primitiveType.getOpcode(ISTORE), elementVar);
				} else {
					mv.visitInsn(AALOAD);
					mv.visitVarInsn(ASTORE, elementVar);
				}
				injectValidation(injector, mv, containerParam, annotation, elementType, elementVar, Map.of("index", indexVar));
				mv.visitIincInsn(indexVar, 1);
				mv.visitJumpInsn(GOTO, loopStart);
				mv.visitLabel(loopEnd);
			}

			private void generateMapLoopWithValidation(ValidationCodeInjector injector, MethodVisitor mv,
					Parameter containerParam, AnnotationDescription annotation, int argsSize) {
				mv.visitVarInsn(ALOAD, containerParam.offset());
				mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "entrySet", "()Ljava/util/Set;", true);
				mv.visitMethodInsn(INVOKEINTERFACE, "java/lang/Iterable", "iterator", "()Ljava/util/Iterator;", true);
				int iteratorVar = argsSize;
				mv.visitVarInsn(ASTORE, iteratorVar);
				int entryVar = iteratorVar + 1;
				mv.visitInsn(ACONST_NULL);
				mv.visitVarInsn(ASTORE, entryVar);
				int keyVar = entryVar + 1;
				mv.visitInsn(ACONST_NULL);
				mv.visitVarInsn(ASTORE, keyVar);
				int elementVar = keyVar + 1;
				mv.visitInsn(ACONST_NULL);
				mv.visitVarInsn(ASTORE, elementVar);

				Label loopStart = new Label();
				Label loopTest = new Label();
				mv.visitJumpInsn(GOTO, loopTest);
				mv.visitLabel(loopStart);
				mv.visitVarInsn(ALOAD, iteratorVar);
				mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;", true);
				mv.visitTypeInsn(CHECKCAST, "java/util/Map$Entry");
				mv.visitVarInsn(ASTORE, entryVar);
				mv.visitVarInsn(ALOAD, entryVar);
				mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map$Entry", "getKey", "()Ljava/lang/Object;", true);
				mv.visitVarInsn(ASTORE, keyVar);
				TypeDescription elementType;
				TypeList.Generic typeArguments = containerParam.genericType().getTypeArguments();
				if (index == 0) {
					elementType = typeArguments.isEmpty() ? TypeDescription.ForLoadedType.of(Object.class)
							: typeArguments.get(0).asErasure();
					mv.visitVarInsn(ALOAD, keyVar);
				} else {
					elementType = typeArguments.size() <= 1 ? TypeDescription.ForLoadedType.of(Object.class)
							: typeArguments.get(1).asErasure();
					mv.visitVarInsn(ALOAD, entryVar);
					mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map$Entry", "getValue", "()Ljava/lang/Object;", true);
				}
				if (!elementType.equals(TypeDescription.ForLoadedType.of(Object.class))) {
					mv.visitTypeInsn(CHECKCAST, elementType.asErasure().getInternalName());
				}
				mv.visitVarInsn(ASTORE, elementVar);
				injectValidation(injector, mv, containerParam, annotation, elementType, elementVar, Map.of("key", keyVar));
				mv.visitLabel(loopTest);
				mv.visitVarInsn(ALOAD, iteratorVar);
				mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true);
				mv.visitJumpInsn(IFNE, loopStart);
			}

			private void generateForEachLoopWithValidation(ValidationCodeInjector injector, MethodVisitor mv,
					Parameter containerParam, AnnotationDescription annotation, int argsSize) {
				mv.visitVarInsn(ALOAD, containerParam.offset());
				mv.visitMethodInsn(INVOKEINTERFACE, "java/lang/Iterable", "iterator", "()Ljava/util/Iterator;", true);
				int iteratorVar = argsSize;
				mv.visitVarInsn(ASTORE, iteratorVar);
				int indexVar = iteratorVar + 1;
				mv.visitInsn(ICONST_0);
				mv.visitVarInsn(ISTORE, indexVar);
				int elementVar = indexVar + 1;
				mv.visitInsn(ACONST_NULL);
				mv.visitVarInsn(ASTORE, elementVar);

				Label loopStart = new Label();
				Label loopTest = new Label();
				mv.visitJumpInsn(GOTO, loopTest);
				mv.visitLabel(loopStart);
				mv.visitVarInsn(ALOAD, iteratorVar);
				mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;", true);
				TypeList.Generic typeArguments = containerParam.genericType().getTypeArguments();
				TypeDescription elementType = typeArguments.isEmpty() ? TypeDescription.ForLoadedType.of(Object.class)
						: typeArguments.get(0).asErasure();
				if (!elementType.equals(TypeDescription.ForLoadedType.of(Object.class))) {
					mv.visitTypeInsn(CHECKCAST, elementType.asErasure().getInternalName());
				}
				mv.visitVarInsn(ASTORE, elementVar);
				injectValidation(injector, mv, containerParam, annotation, elementType, elementVar, Map.of("index", indexVar));
				mv.visitIincInsn(indexVar, 1);
				mv.visitLabel(loopTest);
				mv.visitVarInsn(ALOAD, iteratorVar);
				mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true);
				mv.visitJumpInsn(IFNE, loopStart);
			}

			private void injectValidation(ValidationCodeInjector injector, MethodVisitor mv, Parameter containerParam,
					AnnotationDescription annotation, TypeDescription elementType, int elementVar,
					Map<String, Integer> placeholderValues) {
				String suffix;
				if (containerParam.type().isAssignableTo(Map.class)) {
					suffix = index == 0 ? "[key={key}]" : "[value for key={key}]";
				} else if (placeholderValues.containsKey("index")) {
					suffix = "[{index}]";
				} else {
					suffix = "[]";
				}
				String name = containerParam.name() + suffix;

				Map<String, Integer> cumulativePlaceholders = new HashMap<>(containerParam.placeholderValues());
				cumulativePlaceholders.putAll(placeholderValues);
				SyntheticElementParameter elementParam = new SyntheticElementParameter(name, elementVar, elementType, cumulativePlaceholders);
				Method fragmentMethod = StaticValidateAppender.codeFragmentMethod(config, elementType, injector.fragmentClassMethods());
				Class<? extends Jsr380CodeFragment> clazz = (Class<? extends Jsr380CodeFragment>) fragmentMethod.getDeclaringClass();
				injector.useFragmentClass(clazz).withLocalsOffset(injector.localsOffset() + 5).inject(mv, elementParam, fragmentMethod, annotation);
			}
		}

		private List<Method> fragmentClassMethods() {
			return codeFragmentMethods;
		}

		private static Method codeFragmentMethod(ConfigEntry config, TypeDescription actual, List<Method> codeFragmentMethods) {
			List<Method> matches = codeFragmentMethods.stream()
					.filter(m -> "check".equals(m.getName()) && m.getParameterCount() == 2)
					.filter(m -> m.getParameterTypes()[0].getName().equals(config.anno().getName()))
					.collect(toList());

			return matches.stream().filter(m -> actual.isAssignableTo(m.getParameterTypes()[1])).findFirst()
					.orElseThrow(() -> Jsr380Annos.annotationOnTypeNotValid(config.anno(), actual,
							matches.stream().map(m -> TypeDescription.ForLoadedType.of(m.getParameterTypes()[1]).getActualName())
									.collect(toList())));
		}

		private static class SyntheticElementParameter implements Parameters.Parameter {
			private final String name;
			private final int offset;
			private final TypeDescription type;
			private final Map<String, Integer> placeholderValues;

			public SyntheticElementParameter(String name, int offset, TypeDescription type,
					Map<String, Integer> placeholderValues) {
				this.name = name;
				this.offset = offset;
				this.type = type;
				this.placeholderValues = placeholderValues;
			}

			@Override public int index() { return 0; }
			@Override public String name() { return name; }
			@Override public TypeDescription type() { return type; }
			@Override public int offset() { return offset; }
			@Override public TypeDescription[] annotations() { return new TypeDescription[0]; }
			@Override public Object annotationValue(Type annotation, String name) { return null; }
			@Override public List<List<AnnotationDescription>> genericAnnotations() { return emptyList(); }
			@Override public Object genericAnnotationValue(Type annotation, String name) { return null; }
			@Override public TypeDescription.Generic genericType() { return type.asGenericType(); }
			@Override public Map<String, Integer> placeholderValues() { return placeholderValues; }
			@Override public AnnotationDescription annotation(TypeDescription type) { return null; }
		}
	}
}
