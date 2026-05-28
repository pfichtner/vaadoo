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
import static net.bytebuddy.jar.asm.Opcodes.ATHROW;
import static net.bytebuddy.jar.asm.Opcodes.DUP;
import static net.bytebuddy.jar.asm.Opcodes.GETSTATIC;
import static net.bytebuddy.jar.asm.Opcodes.IFNE;
import static net.bytebuddy.jar.asm.Opcodes.ILOAD;
import static net.bytebuddy.jar.asm.Opcodes.INVOKESPECIAL;
import static net.bytebuddy.jar.asm.Opcodes.INVOKESTATIC;
import static net.bytebuddy.jar.asm.Opcodes.INVOKEVIRTUAL;
import static net.bytebuddy.jar.asm.Opcodes.NEW;
import static net.bytebuddy.jar.asm.Type.BOOLEAN_TYPE;
import static net.bytebuddy.jar.asm.Type.getMethodDescriptor;
import static net.bytebuddy.jar.asm.Type.getObjectType;
import static net.bytebuddy.jar.asm.Type.getType;
import static net.bytebuddy.matcher.ElementMatchers.named;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.pfichtner.vaadoo.Parameters.Parameter;

import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.method.MethodDescription.InDefinedShape;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeDescription.Generic;
import net.bytebuddy.description.type.TypeList;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Type;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = PRIVATE)
public final class CustomAnnotations {

	public static void addCustomAnnotations(MethodVisitor mv, Parameter parameter, TypeDescription annotation,
			String ownerClass, Map<TypeDescription, String> validatorFields) {
		AnnotationDescription constraint = findConstraintAnnotation(annotation);
		if (constraint == null) {
			return;
		}
		var validatedBy = constraint.getValue("validatedBy");
		if (validatedBy == null) {
			return;
		}
		var validatorClasses = (TypeDescription[]) validatedBy.resolve();
		if (validatorClasses == null) {
			return;
		}

		for (TypeDescription validatorClass : validatorClasses) {
			String validatorType = validatorClass.getInternalName();
			String fieldName = validatorFields == null ? null : validatorFields.get(validatorClass);
			if (fieldName == null) {
				mv.visitTypeInsn(NEW, validatorType);
				mv.visitInsn(DUP);
				mv.visitMethodInsn(INVOKESPECIAL, validatorType, "<init>", "()V", false);
			} else {
				mv.visitFieldInsn(GETSTATIC, ownerClass, fieldName, "L" + validatorType + ";");
			}

			Type type = Type.getType(parameter.type().getDescriptor());
			mv.visitVarInsn(type.getOpcode(ILOAD), 0);
			if (parameter.type().isPrimitive()) {
				TypeDescription boxed = parameter.type().asBoxed();
				mv.visitMethodInsn(INVOKESTATIC, boxed.getInternalName(), "valueOf",
						"(" + type.getDescriptor() + ")L" + boxed.getInternalName() + ";", false);
			}

			mv.visitInsn(ACONST_NULL);
			Generic validatorInterface = findValidatorInterface(validatorClass);
			TypeDescription validatedType = validatorInterface.getTypeArguments().get(1).asErasure();
			String contextTypeName = validatorInterface.asErasure().getName().startsWith("jakarta")
					? "jakarta.validation.ConstraintValidatorContext"
					: "javax.validation.ConstraintValidatorContext";
			String descriptor = getMethodDescriptor(BOOLEAN_TYPE, getObjectType(validatedType.getInternalName()),
					getObjectType(contextTypeName.replace('.', '/')));
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

	private static AnnotationDescription findConstraintAnnotation(TypeDescription annotation) {
		return annotation.getDeclaredAnnotations().stream()
				.filter(a -> a.getAnnotationType().getName().equals("jakarta.validation.Constraint")
						|| a.getAnnotationType().getName().equals("javax.validation.Constraint"))
				.findFirst().orElse(null);
	}

	private static Generic findValidatorInterface(TypeDescription validatorClass) {
		Generic constraintValidator = validatorClass.asGenericType();
		while (constraintValidator != null) {
			for (Generic iface : constraintValidator.getInterfaces()) {
				String name = iface.asErasure().getName();
				if (name.equals("jakarta.validation.ConstraintValidator")
						|| name.equals("javax.validation.ConstraintValidator")) {
					return iface;
				}
			}
			constraintValidator = constraintValidator.getSuperClass();
		}
		throw new IllegalStateException("No ConstraintValidator found in hierarchy of " + validatorClass);
	}

	private static final Pattern annoMethodPattern = Pattern.compile("anno\\.(\\w+)\\(\\)");

	private static Object getMessage(Parameter parameter, TypeDescription annotation, Object message) {
		Object msgTemplate = message != null ? message : defaultMessage(annotation);
		if (msgTemplate instanceof String) {
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

			return NamedPlaceholders.replace((String) msgTemplate,
					rbResolver.andThen(annotationValueResolver).andThen(paramNameResolver));
		}
		return msgTemplate == null ? format("%s not valid", parameter.name()) : msgTemplate;
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
