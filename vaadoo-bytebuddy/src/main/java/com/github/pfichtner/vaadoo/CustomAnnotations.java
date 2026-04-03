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
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static lombok.AccessLevel.PRIVATE;
import static net.bytebuddy.jar.asm.Opcodes.AASTORE;
import static net.bytebuddy.jar.asm.Opcodes.ACONST_NULL;
import static net.bytebuddy.jar.asm.Opcodes.ALOAD;
import static net.bytebuddy.jar.asm.Opcodes.ANEWARRAY;
import static net.bytebuddy.jar.asm.Opcodes.ATHROW;
import static net.bytebuddy.jar.asm.Opcodes.DLOAD;
import static net.bytebuddy.jar.asm.Opcodes.DUP;
import static net.bytebuddy.jar.asm.Opcodes.FLOAD;
import static net.bytebuddy.jar.asm.Opcodes.F_APPEND;
import static net.bytebuddy.jar.asm.Opcodes.ICONST_0;
import static net.bytebuddy.jar.asm.Opcodes.ICONST_1;
import static net.bytebuddy.jar.asm.Opcodes.IFNE;
import static net.bytebuddy.jar.asm.Opcodes.ILOAD;
import static net.bytebuddy.jar.asm.Opcodes.INVOKESPECIAL;
import static net.bytebuddy.jar.asm.Opcodes.INVOKESTATIC;
import static net.bytebuddy.jar.asm.Opcodes.INVOKEVIRTUAL;
import static net.bytebuddy.jar.asm.Opcodes.LLOAD;
import static net.bytebuddy.jar.asm.Opcodes.NEW;
import static net.bytebuddy.jar.asm.Type.BOOLEAN_TYPE;
import static net.bytebuddy.jar.asm.Type.getMethodDescriptor;
import static net.bytebuddy.jar.asm.Type.getObjectType;
import static net.bytebuddy.jar.asm.Type.getType;
import static net.bytebuddy.matcher.ElementMatchers.named;

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

	public static void addCustomAnnotations(MethodVisitor mv, Parameter parameter, TypeDescription annotation) {
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
			mv.visitTypeInsn(NEW, validatorType);
			mv.visitInsn(DUP);
			mv.visitMethodInsn(INVOKESPECIAL, validatorType, "<init>", "()V", false);
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
			mv.visitInsn(ICONST_1);
			mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
			mv.visitInsn(DUP);
			mv.visitInsn(ICONST_0);
			loadParameterValue(mv, parameter);
			mv.visitInsn(AASTORE);
			mv.visitMethodInsn(INVOKESTATIC, "java/lang/String", "format",
					"(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;", false);
			mv.visitMethodInsn(INVOKESPECIAL, "java/lang/IllegalArgumentException", "<init>", "(Ljava/lang/String;)V",
					false);
			mv.visitInsn(ATHROW);
			mv.visitLabel(label0);
			mv.visitFrame(F_APPEND, 1, new Object[] { validatorType }, 0, null);
		}
	}

	private static final Pattern annoMethodPattern = Pattern.compile("anno\\.(\\w+)\\(\\)");

	private static void loadParameterValue(MethodVisitor mv, Parameter parameter) {
		Type paramType = Type.getType(parameter.type().getDescriptor());
		int varIndex = parameter.offset();
		switch (paramType.getSort()) {
		case Type.BOOLEAN:
			mv.visitVarInsn(ILOAD, varIndex);
			mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
			break;
		case Type.BYTE:
			mv.visitVarInsn(ILOAD, varIndex);
			mv.visitMethodInsn(INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false);
			break;
		case Type.CHAR:
			mv.visitVarInsn(ILOAD, varIndex);
			mv.visitMethodInsn(INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false);
			break;
		case Type.SHORT:
			mv.visitVarInsn(ILOAD, varIndex);
			mv.visitMethodInsn(INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", false);
			break;
		case Type.INT:
			mv.visitVarInsn(ILOAD, varIndex);
			mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
			break;
		case Type.LONG:
			mv.visitVarInsn(LLOAD, varIndex);
			mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
			break;
		case Type.FLOAT:
			mv.visitVarInsn(FLOAD, varIndex);
			mv.visitMethodInsn(INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
			break;
		case Type.DOUBLE:
			mv.visitVarInsn(DLOAD, varIndex);
			mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
			break;
		default:
			mv.visitVarInsn(ALOAD, varIndex);
			break;
		}
	}

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
		TypeList.Generic typeArguments = validatorClass.getInterfaces().stream() //
				.filter(i -> i.asErasure().equals(ForLoadedType.of(ConstraintValidator.class))) //
				.findFirst().orElseThrow().getTypeArguments();
		if (typeArguments.size() == 2) {
			return typeArguments.get(1).asErasure();
		}
		throw new IllegalArgumentException(
				format("Expect %s to have 2 generic types but found %s", validatorClass, typeArguments));
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
