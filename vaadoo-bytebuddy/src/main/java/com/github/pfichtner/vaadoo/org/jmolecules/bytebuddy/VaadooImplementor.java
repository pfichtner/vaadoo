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
import static java.lang.reflect.Modifier.isAbstract;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toList;
import static java.util.stream.IntStream.range;
import static java.util.stream.Stream.concat;
import static java.util.stream.Stream.empty;
import static net.bytebuddy.description.modifier.FieldManifestation.FINAL;
import static net.bytebuddy.description.modifier.Ownership.STATIC;
import static net.bytebuddy.description.modifier.Visibility.PRIVATE;
import static net.bytebuddy.implementation.MethodCall.invoke;
import static net.bytebuddy.jar.asm.ClassWriter.COMPUTE_FRAMES;
import static net.bytebuddy.jar.asm.ClassWriter.COMPUTE_MAXS;
import static net.bytebuddy.jar.asm.Opcodes.AALOAD;
import static net.bytebuddy.jar.asm.Opcodes.AASTORE;
import static net.bytebuddy.jar.asm.Opcodes.ACC_PRIVATE;
import static net.bytebuddy.jar.asm.Opcodes.ACC_STATIC;
import static net.bytebuddy.jar.asm.Opcodes.ACONST_NULL;
import static net.bytebuddy.jar.asm.Opcodes.ALOAD;
import static net.bytebuddy.jar.asm.Opcodes.ANEWARRAY;
import static net.bytebuddy.jar.asm.Opcodes.ARETURN;
import static net.bytebuddy.jar.asm.Opcodes.ARRAYLENGTH;
import static net.bytebuddy.jar.asm.Opcodes.ASM9;
import static net.bytebuddy.jar.asm.Opcodes.ASTORE;
import static net.bytebuddy.jar.asm.Opcodes.BIPUSH;
import static net.bytebuddy.jar.asm.Opcodes.CHECKCAST;
import static net.bytebuddy.jar.asm.Opcodes.DUP;
import static net.bytebuddy.jar.asm.Opcodes.GETSTATIC;
import static net.bytebuddy.jar.asm.Opcodes.GOTO;
import static net.bytebuddy.jar.asm.Opcodes.IALOAD;
import static net.bytebuddy.jar.asm.Opcodes.ICONST_0;
import static net.bytebuddy.jar.asm.Opcodes.ICONST_1;
import static net.bytebuddy.jar.asm.Opcodes.IFNE;
import static net.bytebuddy.jar.asm.Opcodes.IFNULL;
import static net.bytebuddy.jar.asm.Opcodes.IF_ICMPGE;
import static net.bytebuddy.jar.asm.Opcodes.ILOAD;
import static net.bytebuddy.jar.asm.Opcodes.INVOKEINTERFACE;
import static net.bytebuddy.jar.asm.Opcodes.INVOKESPECIAL;
import static net.bytebuddy.jar.asm.Opcodes.INVOKESTATIC;
import static net.bytebuddy.jar.asm.Opcodes.INVOKEVIRTUAL;
import static net.bytebuddy.jar.asm.Opcodes.ISTORE;
import static net.bytebuddy.jar.asm.Opcodes.NEW;
import static net.bytebuddy.jar.asm.Opcodes.POP;
import static net.bytebuddy.jar.asm.Opcodes.PUTSTATIC;
import static net.bytebuddy.jar.asm.Opcodes.RETURN;
import static net.bytebuddy.matcher.ElementMatchers.is;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.github.pfichtner.vaadoo.ConstructorAnnotationRemover;
import com.github.pfichtner.vaadoo.CustomAnnotations;
import com.github.pfichtner.vaadoo.Jsr380Annos;
import com.github.pfichtner.vaadoo.Jsr380Annos.ConfigEntry;
import com.github.pfichtner.vaadoo.Parameters;
import com.github.pfichtner.vaadoo.Parameters.Parameter;
import com.github.pfichtner.vaadoo.ValidationCodeInjector;
import com.github.pfichtner.vaadoo.ValidationCodeInjector.InjectionTask;
import com.github.pfichtner.vaadoo.fragments.Jsr380CodeFragment;
import com.github.pfichtner.vaadoo.fragments.impl.Template;
import com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy.PluginLogger.Log;
import com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy.config.VaadooConfiguration;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Pattern.Flag;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.experimental.Delegate;
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
import net.bytebuddy.implementation.Implementation.Context;
import net.bytebuddy.implementation.SuperMethodCall;
import net.bytebuddy.implementation.bytecode.ByteCodeAppender;
import net.bytebuddy.implementation.bytecode.ByteCodeAppender.Size;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Type;
import net.bytebuddy.pool.TypePool;

class VaadooImplementor {

	private static final String VALIDATE_METHOD_BASE_NAME = "validate";
	private static final String VAADOO$FIND_ANNO_METHOD_NAME = "vaadoo$findAnno";

	private final VaadooConfiguration configuration;

	public VaadooImplementor(VaadooConfiguration configuration) {
		this.configuration = cachedConfiguration(configuration);
	}

	private static final String VALIDATOR_FIELD_PREFIX = "vaadoo$validator$";

	@Value
	private static class ValidatorKey {
		TypeDescription validatorClass;
		AnnotationDescription annotation;
	}

	JMoleculesTypeBuilder implementVaadoo(JMoleculesTypeBuilder type, Log log) {
		TypeDescription typeDescription = type.getTypeDescription();

		Map<ValidatorKey, String> validatorFields = createFieldsForValidators(typeDescription);
		type = injectFields(type, typeDescription, validatorFields);

		List<String> usedMethodNames = new ArrayList<>(typeDescription.getDeclaredMethods().stream()
				.map(MethodDescription.InDefinedShape::getName).collect(toList()));
		Set<String> allGeneratedValidateMethodNames = new HashSet<>();

		for (InDefinedShape definedShape : typeDescription.getDeclaredMethods()) {
			final InDefinedShape finalDefinedShape = definedShape;
			if (finalDefinedShape.isConstructor()) {
				Parameters parameters = Parameters.of(finalDefinedShape.getParameters());
				Implementation.Composable centralValidateImpl = null;

				// We iterate backwards to build the chain so the calls are in the correct
				// order:
				// validate_p1, validate_p2, ...
				for (int i = parameters.count() - 1; i >= 0; i--) {
					Parameter parameter = parameters.parameter(i);
					String validateParamMethodName = nonExistingMethodName(usedMethodNames,
							VALIDATE_METHOD_BASE_NAME + "_" + parameter.name());
					StaticValidateAppender parameterAppender = new StaticValidateAppender(
							typeDescription.getInternalName(), validateParamMethodName, parameter, configuration,
							validatorFields);

					if (parameterAppender.hasInjections()) {
						usedMethodNames.add(validateParamMethodName);
						allGeneratedValidateMethodNames.add(validateParamMethodName);
						type = type.mapBuilder(t -> addStaticValidateMethod(t, parameterAppender, log));

						Implementation.Composable invokeParam = invoke(
								named(validateParamMethodName).and(takesArguments(parameter.type()))).withArgument(i);
						centralValidateImpl = centralValidateImpl == null ? invokeParam
								: invokeParam.andThen(centralValidateImpl);
					}
				}

				if (centralValidateImpl != null) {
					final String centralValidateName = nonExistingMethodName(usedMethodNames,
							VALIDATE_METHOD_BASE_NAME);
					usedMethodNames.add(centralValidateName);
					allGeneratedValidateMethodNames.add(centralValidateName);
					final Implementation.Composable finalCentralValidateImpl = centralValidateImpl;
					type = type.mapBuilder(t -> t.defineMethod(centralValidateName, void.class, ACC_PRIVATE | ACC_STATIC)
							.withParameters(finalDefinedShape.getParameters().asTypeList().asErasures())
							.intercept(finalCentralValidateImpl));

					type = type.mapBuilder(t -> t.visit(new AsmVisitorWrapper.AbstractBase() {
						@Override
						public ClassVisitor wrap(TypeDescription instrumentedType, ClassVisitor cv,
								Implementation.Context implementationContext, TypePool typePool,
								FieldList<FieldDescription.InDefinedShape> fields, MethodList<?> methods,
								int writerFlags, int readerFlags) {
							return new ClassVisitor(ASM9, cv) {
								@Override
								public MethodVisitor visitMethod(int access, String name, String descriptor,
										String signature, String[] exceptions) {
									if ("<init>".equals(name) && descriptor.equals(finalDefinedShape.getDescriptor())) {
										return new MethodVisitor(ASM9,
												super.visitMethod(access, name, descriptor, signature, exceptions)) {
											@Override
											public void visitInsn(int opcode) {
												if (opcode == RETURN) {
													Type[] args = Type
															.getArgumentTypes(finalDefinedShape.getDescriptor());
													int slot = 1;
													for (Type arg : args) {
														mv.visitVarInsn(arg.getOpcode(ILOAD), slot);
														slot += arg.getSize();
													}
													mv.visitMethodInsn(INVOKESTATIC, instrumentedType.getInternalName(),
															centralValidateName, finalDefinedShape.getDescriptor(),
															false);
												}
												super.visitInsn(opcode);
											}
										};
									}
									return super.visitMethod(access, name, descriptor, signature, exceptions);
								}
							};
						}
					}));
				}
			}
		}

		if (configuration.removeJsr380Annotations()) {
			type = type.mapBuilder(t -> wrap(t, cv -> new ConstructorAnnotationRemover(cv, configuration)));
		}

		return type;
	}

	private JMoleculesTypeBuilder injectFields(JMoleculesTypeBuilder type, TypeDescription typeDescription,
			Map<ValidatorKey, String> validatorFields) {
		if (!validatorFields.isEmpty()) {
			for (Map.Entry<ValidatorKey, String> entry : validatorFields.entrySet()) {
				type = type.mapBuilder(
						t -> t.defineField(entry.getValue(), entry.getKey().validatorClass, PRIVATE, STATIC, FINAL));
			}

			type = addStaticInitializer(type);
			for (Map.Entry<ValidatorKey, String> entry : validatorFields.entrySet()) {
				ValidatorKey key = entry.getKey();
				String fieldName = entry.getValue();
				// Find parameter index for this annotation
				// This is a bit slow but only done once at build time
				int paramIdx = -1;
				TypeDescription[] paramTypes = null;
				for (InDefinedShape definedShape : typeDescription.getDeclaredMethods()) {
					if (definedShape.isConstructor()) {
						Parameters parameters = Parameters.of(definedShape.getParameters());
						for (int i = 0; i < parameters.count(); i++) {
							for (AnnotationDescription ad : parameters.parameter(i).annotationDescriptions()) {
								if (ad.equals(key.annotation)) {
									paramIdx = i;
									paramTypes = parameters.types().toArray(new TypeDescription[0]);
									break;
								}
							}
						}
					}
				}

				final int finalParamIdx = paramIdx;
				final TypeDescription[] finalParamTypes = paramTypes;

				type = type.mapBuilder(t -> t.initializer(new ByteCodeAppender() {
					@Override
					public Size apply(MethodVisitor mv, Context context, MethodDescription instrumentedMethod) {
						String validatorType = key.validatorClass.getInternalName();
						String instrumentedInternalName = typeDescription.getInternalName();

						mv.visitTypeInsn(NEW, validatorType);
						mv.visitInsn(DUP);
						mv.visitMethodInsn(INVOKESPECIAL, validatorType, "<init>", "()V", false);
						mv.visitInsn(DUP);

						// Retrieve annotation:
						// vaadoo$findAnno(MyClass.class.getDeclaredConstructor(...).getParameterAnnotations()[paramIdx],
						// Min.class)
						mv.visitLdcInsn(Type.getObjectType(instrumentedInternalName));

						// Load param types array
						if (finalParamTypes == null || finalParamTypes.length == 0) {
							mv.visitInsn(ACONST_NULL);
						} else {
							mv.visitIntInsn(BIPUSH, finalParamTypes.length);
							mv.visitTypeInsn(ANEWARRAY, "java/lang/Class");
							for (int i = 0; i < finalParamTypes.length; i++) {
								mv.visitInsn(DUP);
								mv.visitIntInsn(BIPUSH, i);
								TypeDescription type = finalParamTypes[i];
								if (type.isPrimitive()) {
									String wrapper = type.asBoxed().getInternalName();
									mv.visitFieldInsn(GETSTATIC, wrapper, "TYPE", "Ljava/lang/Class;");
								} else {
									mv.visitLdcInsn(Type.getType(type.getDescriptor()));
								}
								mv.visitInsn(AASTORE);
							}
						}

						mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getDeclaredConstructor",
								"([Ljava/lang/Class;)Ljava/lang/reflect/Constructor;", false);
						mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Constructor", "getParameterAnnotations",
								"()[[Ljava/lang/annotation/Annotation;", false);
						mv.visitIntInsn(BIPUSH, finalParamIdx);
						mv.visitInsn(AALOAD);
						mv.visitLdcInsn(Type.getType(key.annotation.getAnnotationType().getDescriptor()));
						mv.visitMethodInsn(INVOKESTATIC, instrumentedInternalName, VAADOO$FIND_ANNO_METHOD_NAME,
								"([Ljava/lang/annotation/Annotation;Ljava/lang/Class;)Ljava/lang/annotation/Annotation;",
								false);

						mv.visitMethodInsn(INVOKEVIRTUAL, validatorType, "initialize",
								"(Ljava/lang/annotation/Annotation;)V", false);

						mv.visitFieldInsn(PUTSTATIC, instrumentedInternalName, fieldName, "L" + validatorType + ";");
						return new Size(6, 0);
					}
				}));
			}
		}
		return type;
	}

	private JMoleculesTypeBuilder addStaticInitializer(JMoleculesTypeBuilder type) {
		type = type.mapBuilder(t -> wrap(t, COMPUTE_FRAMES)
				.defineMethod(VAADOO$FIND_ANNO_METHOD_NAME, Annotation.class, PRIVATE, STATIC)
				.withParameters(Annotation[].class, Class.class)
				.intercept(new Implementation.Simple(new ByteCodeAppender() {
					@Override
					public Size apply(MethodVisitor mv, Context context, MethodDescription instrumentedMethod) {
						Label loop = new Label();
						Label found = new Label();
						Label notFound = new Label();
						mv.visitVarInsn(ALOAD, 0);
						mv.visitInsn(ARRAYLENGTH);
						mv.visitVarInsn(ISTORE, 2);
						mv.visitInsn(ICONST_0);
						mv.visitVarInsn(ISTORE, 3);
						mv.visitLabel(loop);
						mv.visitVarInsn(ILOAD, 3);
						mv.visitVarInsn(ILOAD, 2);
						mv.visitJumpInsn(IF_ICMPGE, notFound);
						mv.visitVarInsn(ALOAD, 0);
						mv.visitVarInsn(ILOAD, 3);
						mv.visitInsn(AALOAD);
						mv.visitInsn(DUP);
						mv.visitMethodInsn(INVOKEINTERFACE, "java/lang/annotation/Annotation", "annotationType",
								"()Ljava/lang/Class;", true);
						mv.visitVarInsn(ALOAD, 1);
						mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "equals", "(Ljava/lang/Object;)Z", false);
						mv.visitJumpInsn(IFNE, found);
						mv.visitInsn(POP);
						mv.visitIincInsn(3, 1);
						mv.visitJumpInsn(GOTO, loop);
						mv.visitLabel(notFound);
						mv.visitInsn(ACONST_NULL);
						mv.visitInsn(ARETURN);
						mv.visitLabel(found);
						mv.visitInsn(ARETURN);
						return new Size(3, 4);
					}
				})));
		return type;
	}

	private Map<ValidatorKey, String> createFieldsForValidators(TypeDescription typeDescription) {
		Map<ValidatorKey, String> validatorFields = new LinkedHashMap<>();
		int validatorCounter = 0;
		for (InDefinedShape definedShape : typeDescription.getDeclaredMethods()) {
			if (definedShape.isConstructor()) {
				for (Parameter parameter : Parameters.of(definedShape.getParameters())) {
					for (AnnotationDescription annotation : parameter.annotationDescriptions()) {
						TypeDescription annotationType = annotation.getAnnotationType();
						if (!isStandardJr380Anno(annotationType) && isConstraintAnnotation(annotationType)) {
							AnnotationDescription constraint = findConstraintAnnotation(annotationType);
							if (constraint != null) {
								var validatedBy = (TypeDescription[]) constraint.getValue("validatedBy").resolve();
								if (validatedBy != null) {
									for (TypeDescription validatorClass : validatedBy) {
										ValidatorKey key = new ValidatorKey(validatorClass, annotation);
										if (!validatorFields.containsKey(key)) {
											validatorFields.put(key, VALIDATOR_FIELD_PREFIX + validatorCounter++);
										}
									}
								}
							}
						}
					}
				}
			}
		}
		return validatorFields;
	}

	private static AnnotationDescription findConstraintAnnotation(TypeDescription annotation) {
		return annotation.getDeclaredAnnotations().stream()
				.filter(a -> a.getAnnotationType().getName().equals("jakarta.validation.Constraint")
						|| a.getAnnotationType().getName().equals("javax.validation.Constraint"))
				.findFirst().orElse(null);
	}

	private static boolean isConstraintAnnotation(TypeDescription annotationType) {
		return annotationType.getDeclaredAnnotations().stream()
				.anyMatch(a -> a.getAnnotationType().getName().equals("jakarta.validation.Constraint")
						|| a.getAnnotationType().getName().equals("javax.validation.Constraint"));
	}

	private Builder<?> addStaticValidateMethod(Builder<?> builder, StaticValidateAppender staticValidateAppender,
			Log log) {
		log.info("Implementing static validate method #{}.", staticValidateAppender.validateMethodName);
		return markGenerated(wrap(builder, COMPUTE_FRAMES | COMPUTE_MAXS)
				.defineMethod(staticValidateAppender.validateMethodName, void.class, ACC_PRIVATE | ACC_STATIC)
				.withParameters(staticValidateAppender.parameter.type())
				.intercept(new Implementation.Simple(staticValidateAppender)));
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
			public ClassVisitor wrap(TypeDescription instrumentedType, ClassVisitor cv,
					net.bytebuddy.implementation.Implementation.Context context, TypePool typePool,
					FieldList<FieldDescription.InDefinedShape> fields, MethodList<?> methods, int writerFlags,
					int readerFlags) {
				return classVisitorProvider.apply(cv);
			}

		});
	}

	private static VaadooConfiguration cachedConfiguration(VaadooConfiguration configuration) {
		Map<TypeDescription, Boolean> cache = new HashMap<>();
		return (VaadooConfiguration) java.lang.reflect.Proxy.newProxyInstance(
				VaadooConfiguration.class.getClassLoader(), new Class[] { VaadooConfiguration.class }, (p, m, a) -> {
					if (m.getName().equals("include") && a.length == 1) {
						return cache.computeIfAbsent((TypeDescription) a[0], configuration::include);
					}
					return m.invoke(configuration, a);
				});
	}

	private String nonExistingMethodName(Collection<String> methodNames, String base) {
		return Stream.iterate(0, i -> i + 1) //
				.map(i -> (i == 0) ? base : base + "_" + i) //
				.filter(not(methodNames::contains)) //
				.findFirst() //
				.get(); // safe because stream is infinite, will always find a free name
	}

	private static Builder<?> wrap(Builder<?> builder, int flags) {
		return builder.visit(new AsmVisitorWrapper.ForDeclaredMethods().writerFlags(flags));
	}

	private static <T> Predicate<T> not(Predicate<T> predicate) {
		return predicate.negate();
	}

	@RequiredArgsConstructor(staticName = "of")
	private static class StaticValidateAppender implements ByteCodeAppender {

		@Value(staticConstructor = "of")
		private static class Jsr380AnnoInjectionTask implements InjectionTask {
			Parameter parameter;
			Method method;
			AnnotationDescription annotation;

			@Override
			public void apply(ValidationCodeInjector injector, MethodVisitor mv, int argsSize) {
				injector.inject(mv, parameter, method, annotation);
			}
		}

		@Value(staticConstructor = "of")
		private static class CustomInjectionTask implements InjectionTask {
			Parameter parameter;
			TypeDescription annotation;
			String ownerClass;
			Map<ValidatorKey, String> validatorFields;

			@Override
			public void apply(ValidationCodeInjector __, MethodVisitor mv, int argsSize) {
				// We need to build a map of validator class to field name for this SPECIFIC
				// annotation
				Map<TypeDescription, String> fieldsForThisAnno = new HashMap<>();
				AnnotationDescription constraint = findConstraintAnnotation(annotation);
				if (constraint != null) {
					var validatedBy = (TypeDescription[]) constraint.getValue("validatedBy").resolve();
					if (validatedBy != null) {
						// Find the actual AnnotationDescription for this parameter
						AnnotationDescription actualAnno = null;
						for (AnnotationDescription ad : parameter.annotationDescriptions()) {
							if (ad.getAnnotationType().equals(annotation)) {
								actualAnno = ad;
								break;
							}
						}

						if (actualAnno != null) {
							for (TypeDescription validatorClass : validatedBy) {
								String fieldName = validatorFields.get(new ValidatorKey(validatorClass, actualAnno));
								if (fieldName != null) {
									fieldsForThisAnno.put(validatorClass, fieldName);
								}
							}
						}
					}
				}
				addCustomAnnotations(mv, parameter, annotation, ownerClass, fieldsForThisAnno);
			}

			private static AnnotationDescription findConstraintAnnotation(TypeDescription annotation) {
				return annotation.getDeclaredAnnotations().stream()
						.filter(a -> a.getAnnotationType().getName().equals("jakarta.validation.Constraint")
								|| a.getAnnotationType().getName().equals("javax.validation.Constraint"))
						.findFirst().orElse(null);
			}
		}

		private final String ownerClass;
		private final String validateMethodName;
		private final Parameter parameter;
		private final Map<Parameter, Integer> preComputedPatternFlags;
		private final VaadooConfiguration configuration;
		private final List<Method> fragmentMixinsCodeFragmentMethods;
		private final List<Method> codeFragmentMethods;
		private final String methodDescriptor;
		private final List<InjectionTask> injectionTasks;
		private final List<TypeDescription> jsr380RepeatableAnnotationContainers;
		private final Map<ValidatorKey, String> validatorFields;

		public StaticValidateAppender(String ownerClass, String validateMethodName, Parameter parameter,
				VaadooConfiguration configuration, Map<ValidatorKey, String> validatorFields) {
			this.ownerClass = ownerClass;
			this.validateMethodName = validateMethodName;
			this.parameter = new ParameterWithOffsetZero(parameter);
			this.configuration = configuration;
			this.validatorFields = validatorFields;
			this.preComputedPatternFlags = computePatternFlagsDuringBuild(this.parameter);
			this.fragmentMixinsCodeFragmentMethods = configuration.codeFragmentMixins().stream()
					.map(m -> fragmentMethods(m)).flatMap(List::stream).collect(toList());
			this.codeFragmentMethods = fragmentMethods(configuration.jsr380CodeFragmentClass());
			this.methodDescriptor = Type.getMethodDescriptor(Type.VOID_TYPE,
					Type.getType(this.parameter.type().getDescriptor()));
			this.jsr380RepeatableAnnotationContainers = findRepeatableAnnotationContainers();
			this.injectionTasks = tasksFor(this.parameter).collect(toList());
		}

		private static List<Method> fragmentMethods(Class<? extends Jsr380CodeFragment> clazz) {
			return Stream.of(clazz.getMethods()) //
					.filter(m -> m.getDeclaringClass() != Object.class) //
					.filter(m -> !isAbstract(m.getModifiers())) //
					.collect(toList());
		}

		private static Map<Parameter, Integer> computePatternFlagsDuringBuild(Parameter parameter) {
			Map<Parameter, Integer> map = new HashMap<>();
			Object annotationValue = parameter.annotationValue(Type.getType(Pattern.class), "flags");
			map.put(parameter, annotationValue == null //
					? 0
					: Template.bitwiseOr(Stream.of((EnumerationDescription[]) annotationValue) //
							.map(EnumerationDescription::getValue) //
							.map(Flag::valueOf) //
							.toArray(Flag[]::new)));
			return map;
		}

		private Stream<InjectionTask> tasksFor(Parameter parameter) {
			Stream<InjectionTask> fromParam = Stream.of(parameter.annotationDescriptions())
					.flatMap(a -> concat(jsr380(parameter, a.getAnnotationType(), a),
							custom(parameter, a.getAnnotationType())));

			List<List<AnnotationDescription>> genericAnnotations = parameter.genericAnnotations();
			return genericAnnotations.isEmpty() //
					? fromParam //
					: concat(fromParam, handleGenericAnnotations(parameter, genericAnnotations));
		}

		private Stream<InjectionTask> handleGenericAnnotations(Parameter parameter,
				List<List<AnnotationDescription>> genericAnnotations) {
			TypeDescription.Generic genericType = parameter.genericType();

			if (genericType.getSort().isParameterized()) {
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

					return typeArgAnnotations.stream().flatMap(annotation -> {
						TypeDescription annotationType = annotation.getAnnotationType();
						Optional<Method> codeFragmentMethod = isStandardJr380Anno(annotationType)
								? codeFragmentMethod(annotationType, typeArgument.asErasure())
								: Optional.empty();
						return codeFragmentMethod.map(m -> Stream.of(
								new GenericTypeInjectionTask(parameter, typeArgument.asErasure(), m, annotation, i)))
								.orElse(empty());
					});
				});
			} else if (genericType.isArray()) {
				List<AnnotationDescription> typeArgAnnotations = genericAnnotations.get(0);
				if (typeArgAnnotations.isEmpty()) {
					return empty();
				}

				TypeDescription.Generic typeArgument = genericType.getComponentType();
				return typeArgAnnotations.stream().flatMap(annotation -> {
					TypeDescription annotationType = annotation.getAnnotationType();
					if (isStandardJr380Anno(annotationType)) {
						Optional<Method> fragmentMethod = codeFragmentMethod(annotationType, typeArgument.asErasure());
						if (fragmentMethod.isPresent()) {
							return Stream.of(new GenericTypeInjectionTask(parameter, typeArgument.asErasure(),
									fragmentMethod.get(), annotation, 0));
						}
					}
					return empty();
				});
			}

			return empty();
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
					: Stream.of(CustomInjectionTask.of(parameter, annotation, ownerClass, validatorFields));
		}

		public boolean hasInjections() {
			return !injectionTasks.isEmpty();
		}

		@Override
		public Size apply(MethodVisitor mv, Implementation.Context context, MethodDescription instrumentedMethod) {
			int argsSize = (int) parameter.type().getStackSize().getSize();
			ValidationCodeInjector injector = new ValidationCodeInjector(configuration.jsr380CodeFragmentClass(),
					methodDescriptor, preComputedPatternFlags, configuration.nullValueExceptionTypeInternalName())
					.withLocalsOffset(4);
			for (InjectionTask task : injectionTasks) {
				task.apply(injector, mv, argsSize);
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
			TypeDescription validatedType;
			Method method;
			AnnotationDescription annotation;
			int index;

			public GenericTypeInjectionTask(Parameter parameter, TypeDescription validatedType, Method method,
					AnnotationDescription annotation, int index) {
				this.parameter = parameter;
				this.validatedType = validatedType;
				this.method = method;
				this.annotation = annotation;
				this.index = index;
			}

			@Override
			public void apply(ValidationCodeInjector injector, MethodVisitor mv, int argsSize) {
				injector.injectGenericType(mv, parameter, validatedType, method, annotation, index);
			}

		}

		private static class ParameterWithOffsetZero implements Parameter {

			@Delegate
			private final Parameter delegate;

			public ParameterWithOffsetZero(Parameter delegate) {
				this.delegate = delegate;
			}

			@Override
			public int offset() {
				return 0;
			}

		}

		private static class SyntheticElementParameter implements Parameter {

			private final TypeDescription type;

			public SyntheticElementParameter(TypeDescription type) {
				this.type = type;
			}

			@Override
			public int index() {
				return 0;
			}

			@Override
			public String name() {
				return "element";
			}

			@Override
			public TypeDescription type() {
				return type;
			}

			@Override
			public int offset() {
				return 0;
			}

			@Override
			public AnnotationDescription[] annotationDescriptions() {
				return new AnnotationDescription[0];
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

			@Override
			public TypeDescription.Generic genericType() {
				return type.asGenericType();
			}

			@Override
			public Map<String, Integer> placeholderValues() {
				return emptyMap();
			}
		}

	}

}
