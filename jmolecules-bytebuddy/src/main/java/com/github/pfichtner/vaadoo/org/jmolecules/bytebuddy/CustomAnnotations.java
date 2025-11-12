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

import static java.lang.String.format;
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

import java.util.function.Function;

import com.github.pfichtner.vaadoo.NamedPlaceholders;
import com.github.pfichtner.vaadoo.Parameters.Parameter;
import com.github.pfichtner.vaadoo.Resources;
import com.github.pfichtner.vaadoo.ValidationCodeInjector;

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

@NoArgsConstructor(access = PRIVATE)
public final class CustomAnnotations {

	public static void addCustomAnnotations(Parameter parameter, TypeDescription annotation, MethodVisitor mv) {
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
			mv.visitLdcInsn(message == null ? getMessage(parameter, annotation) : message);
			mv.visitMethodInsn(INVOKESPECIAL, "java/lang/IllegalArgumentException", "<init>", "(Ljava/lang/String;)V",
					false);
			mv.visitInsn(ATHROW);
			mv.visitLabel(label0);
			mv.visitFrame(F_APPEND, 1, new Object[] { validatorType }, 0, null);
		}
	}

	private static Object getMessage(Parameter parameter, TypeDescription annotation) {
		Object message = null;
		Object defaultMessage = defaultMessage(annotation);
		if (defaultMessage instanceof String) {
			Function<String, String> rbResolver = Resources::message;
			Function<String, String> paramNameResolver = k -> k.equals(ValidationCodeInjector.NAME) ? parameter.name()
					: k;
			message = NamedPlaceholders.replace((String) defaultMessage, rbResolver.andThen(paramNameResolver));
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

	private static Object defaultMessage(TypeDescription type) {
		MethodList<InDefinedShape> list = type.getDeclaredMethods() //
				.filter(named("message")) //
				.filter(m -> m.getParameters().size() == 1);
		return list.isEmpty() ? null : list.getOnly().getDefaultValue().resolve();
	}

}
