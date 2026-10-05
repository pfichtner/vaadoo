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

import static com.github.pfichtner.vaadoo.FormatMessageInjector.injectFormatMessage;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static lombok.AccessLevel.PRIVATE;
import static net.bytebuddy.jar.asm.Opcodes.ACONST_NULL;
import static net.bytebuddy.jar.asm.Opcodes.ALOAD;
import static net.bytebuddy.jar.asm.Opcodes.ATHROW;
import static net.bytebuddy.jar.asm.Opcodes.DUP;
import static net.bytebuddy.jar.asm.Opcodes.F_APPEND;
import static net.bytebuddy.jar.asm.Opcodes.IFNE;
import static net.bytebuddy.jar.asm.Opcodes.INVOKESPECIAL;
import static net.bytebuddy.jar.asm.Opcodes.INVOKEVIRTUAL;
import static net.bytebuddy.jar.asm.Opcodes.NEW;
import static net.bytebuddy.jar.asm.Type.BOOLEAN_TYPE;
import static net.bytebuddy.jar.asm.Type.getMethodDescriptor;
import static net.bytebuddy.jar.asm.Type.getObjectType;
import static net.bytebuddy.jar.asm.Type.getType;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import java.util.Arrays;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.pfichtner.vaadoo.Parameters.Parameter;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.NoArgsConstructor;
import net.bytebuddy.description.method.MethodDescription.InDefinedShape;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeDescription.ForLoadedType;
import net.bytebuddy.description.type.TypeList;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Type;

@NoArgsConstructor(access = PRIVATE)
public final class CustomAnnotations {

	public static void addCustomAnnotations(MethodVisitor mv, Parameter parameter, TypeDescription annotation,
			String ownerInternalName) {
		addCustomAnnotations(mv, parameter, annotation, ownerInternalName, null);
	}

	public static void addCustomAnnotations(MethodVisitor mv, Parameter parameter, TypeDescription annotation,
			String ownerInternalName, Function<CustomValidatorInfo, String> fieldNameResolver) {
		var contraint = annotation.getDeclaredAnnotations().ofType(Constraint.class);
		if (contraint == null) {
			return;
		}
		var validatedBy = contraint.getValue("validatedBy");
		if (validatedBy == null) {
			return;
		}
		var validatorClasses = (TypeDescription[]) validatedBy.resolve();
		if (validatorClasses == null) {
			return;
		}

		for (TypeDescription validatorClass : validatorClasses) {
			String validatorType = validatorClass.getInternalName();

			String fieldName = null;
			if (fieldNameResolver != null && overridesInitialize(validatorClass, annotation)) {
				CustomValidatorInfo info = new CustomValidatorInfo(validatorClass, annotation,
						annotationValues(parameter, annotation), null);
				fieldName = fieldNameResolver.apply(info);
			}

			if (fieldName != null) {
				if (mv != null) {
					mv.visitFieldInsn(net.bytebuddy.jar.asm.Opcodes.GETSTATIC, ownerInternalName, fieldName,
							"L" + validatorType + ";");
				}
			} else {
				if (mv != null) {
					mv.visitTypeInsn(NEW, validatorType);
					mv.visitInsn(DUP);
					mv.visitMethodInsn(INVOKESPECIAL, validatorType, "<init>", "()V", false);
				}
			}

			if (mv != null) {
				mv.visitVarInsn(ALOAD, parameter.index());
				mv.visitInsn(ACONST_NULL);
				String descriptor = getMethodDescriptor(BOOLEAN_TYPE,
						getObjectType(typeThatGetsValidated(validatorClass).getInternalName()),
						getType(ConstraintValidatorContext.class));
				mv.visitMethodInsn(INVOKEVIRTUAL, validatorType, "isValid", descriptor, false);
				Label label0 = new Label();
				mv.visitJumpInsn(IFNE, label0);
				mv.visitTypeInsn(NEW, "java/lang/IllegalArgumentException");
				mv.visitInsn(DUP);
				var message = parameter.annotationValue(getObjectType(annotation.getInternalName()), "message");
				mv.visitLdcInsn(getMessage(parameter, annotation, message));
				injectFormatMessage(mv, parameter);
				mv.visitMethodInsn(INVOKESPECIAL, "java/lang/IllegalArgumentException", "<init>", "(Ljava/lang/String;)V",
						false);
				mv.visitInsn(ATHROW);
				mv.visitLabel(label0);
			}
		}
	}

	private static java.util.Map<String, Object> annotationValues(Parameter parameter, TypeDescription annotation) {
		java.util.Map<String, Object> values = new java.util.HashMap<>();
		for (InDefinedShape method : annotation.getDeclaredMethods()) {
			String name = method.getName();
			Object value = parameter.annotationValue(getObjectType(annotation.getInternalName()), name);
			if (value == null) {
				value = method.getDefaultValue().resolve();
			}
			values.put(name, value);
		}
		return values;
	}

	public static boolean overridesInitialize(TypeDescription validatorClass, TypeDescription annotationType) {
		TypeDescription current = validatorClass;
		while (current != null && !current.represents(Object.class)) {
			if (!current.getDeclaredMethods().filter(named("initialize").and(takesArguments(annotationType))).isEmpty()) {
				return !current.represents(ConstraintValidator.class);
			}
			current = current.getSuperClass() == null ? null : current.getSuperClass().asErasure();
		}
		return false;
	}

	private static final Pattern annoMethodPattern = Pattern.compile("anno\\.(\\w+)\\(\\)");

	private static Object getMessage(Parameter parameter, TypeDescription annotation, Object message) {
		Object defaultMessage = defaultMessage(annotation);
		if (message != null && message.equals(defaultMessage)) {
			Function<String, String> rbResolver = Resources::message;
			Function<String, String> paramNameResolver = k -> k.equals(ValidationCodeInjector.NAME) ? parameter.name()
					: k;

			Function<String, String> annotationValueResolver = k -> {
				Matcher matcher = annoMethodPattern.matcher(k);
				Object annotationValue = null;
				if (matcher.matches()) {
					Type objectType = Type.getObjectType(annotation.getInternalName());
					annotationValue = parameter.annotationValue(objectType, matcher.group(1));
				}
				return annotationValue == null //
						? k //
						: annotationValue.getClass().isArray() //
								? Arrays.stream((Object[]) annotationValue).map(Object::toString)
										.collect(joining(", ", "[", "]")) //
								: annotationValue.toString();
			};

			message = NamedPlaceholders.replace((String) defaultMessage,
					rbResolver.andThen(annotationValueResolver).andThen(paramNameResolver));
		}
		return message == null ? format("%s not valid", parameter.name()) : message;
	}

	private static TypeDescription typeThatGetsValidated(TypeDefinition validatorClass) {
		TypeDescription.Generic constraintValidator = validatorClass.asGenericType();
		while (constraintValidator != null) {
			for (TypeDescription.Generic iface : constraintValidator.getInterfaces()) {
				if (iface.asErasure().equals(ForLoadedType.of(ConstraintValidator.class))) {
					TypeList.Generic typeArguments = iface.getTypeArguments();
					if (typeArguments.size() == 2) {
						return typeArguments.get(1).asErasure();
					}
					throw new IllegalArgumentException("Expected ConstraintValidator<A, B> but found " + typeArguments);
				}
			}
			constraintValidator = constraintValidator.getSuperClass();
		}
		throw new IllegalStateException("No ConstraintValidator found in hierarchy of " + validatorClass);
	}

	private static InDefinedShape defaultMessageMethod(TypeDescription annotation) {
		MethodList<InDefinedShape> messageMethod = annotation.getDeclaredMethods().filter(named("message"));
		return messageMethod.size() == 1 ? requireNonNull(messageMethod.getOnly()) : null;
	}

	private static Object defaultMessage(TypeDescription annotation) {
		InDefinedShape messageMethod = defaultMessageMethod(annotation);
		return messageMethod == null ? null : messageMethod.getDefaultValue().resolve();
	}

}
