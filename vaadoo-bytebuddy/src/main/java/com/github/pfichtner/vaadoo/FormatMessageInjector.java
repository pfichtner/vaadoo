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

import static net.bytebuddy.jar.asm.Opcodes.AASTORE;
import static net.bytebuddy.jar.asm.Opcodes.ALOAD;
import static net.bytebuddy.jar.asm.Opcodes.ANEWARRAY;
import static net.bytebuddy.jar.asm.Opcodes.DLOAD;
import static net.bytebuddy.jar.asm.Opcodes.DUP;
import static net.bytebuddy.jar.asm.Opcodes.FLOAD;
import static net.bytebuddy.jar.asm.Opcodes.ICONST_0;
import static net.bytebuddy.jar.asm.Opcodes.ICONST_1;
import static net.bytebuddy.jar.asm.Opcodes.ILOAD;
import static net.bytebuddy.jar.asm.Opcodes.INVOKESTATIC;
import static net.bytebuddy.jar.asm.Opcodes.LLOAD;

import com.github.pfichtner.vaadoo.Parameters.Parameter;

import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Type;

public final class FormatMessageInjector {

	private FormatMessageInjector() {
		super();
	}

	public static void injectFormatMessage(MethodVisitor mv, Parameter parameter) {
		injectFormatMessage(mv, Type.getType(parameter.type().asErasure().getDescriptor()), parameter.offset());
	}

	public static void injectFormatMessage(MethodVisitor mv, Type type, int varIndex) {
		mv.visitInsn(ICONST_1);
		mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
		mv.visitInsn(DUP);
		mv.visitInsn(ICONST_0);
		loadParameterValue(mv, type, varIndex);
		mv.visitInsn(AASTORE);
		mv.visitMethodInsn(INVOKESTATIC, "java/lang/String", "format",
				"(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;", false);
	}

	public static void loadParameterValue(MethodVisitor mv, Type paramType, int varIndex) {
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
		case Type.ARRAY:
			mv.visitVarInsn(ALOAD, varIndex);
			mv.visitMethodInsn(INVOKESTATIC, "java/util/Arrays", "deepToString",
					"([Ljava/lang/Object;)Ljava/lang/String;", false);
			break;
		default:
			mv.visitVarInsn(ALOAD, varIndex);
			break;
		}
	}

}
