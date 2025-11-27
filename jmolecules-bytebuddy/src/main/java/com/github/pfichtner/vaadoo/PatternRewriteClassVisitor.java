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

import static net.bytebuddy.jar.asm.Opcodes.AALOAD;
import static net.bytebuddy.jar.asm.Opcodes.ACC_FINAL;
import static net.bytebuddy.jar.asm.Opcodes.ACC_PRIVATE;
import static net.bytebuddy.jar.asm.Opcodes.ACC_STATIC;
import static net.bytebuddy.jar.asm.Opcodes.ACC_SYNTHETIC;
import static net.bytebuddy.jar.asm.Opcodes.ALOAD;
import static net.bytebuddy.jar.asm.Opcodes.ARETURN;
import static net.bytebuddy.jar.asm.Opcodes.ASM9;
import static net.bytebuddy.jar.asm.Opcodes.ASTORE;
import static net.bytebuddy.jar.asm.Opcodes.BIPUSH;
import static net.bytebuddy.jar.asm.Opcodes.CHECKCAST;
import static net.bytebuddy.jar.asm.Opcodes.DUP;
import static net.bytebuddy.jar.asm.Opcodes.GETSTATIC;
import static net.bytebuddy.jar.asm.Opcodes.H_INVOKESTATIC;
import static net.bytebuddy.jar.asm.Opcodes.ICONST_0;
import static net.bytebuddy.jar.asm.Opcodes.ICONST_1;
import static net.bytebuddy.jar.asm.Opcodes.ILOAD;
import static net.bytebuddy.jar.asm.Opcodes.INVOKEINTERFACE;
import static net.bytebuddy.jar.asm.Opcodes.INVOKESPECIAL;
import static net.bytebuddy.jar.asm.Opcodes.INVOKESTATIC;
import static net.bytebuddy.jar.asm.Opcodes.INVOKEVIRTUAL;
import static net.bytebuddy.jar.asm.Opcodes.NEW;
import static net.bytebuddy.jar.asm.Opcodes.PUTSTATIC;
import static net.bytebuddy.jar.asm.Opcodes.RETURN;

import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.Handle;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Type;

public class PatternRewriteClassVisitor extends ClassVisitor {

	private final String validateMethodName;
	private String owner;
	private boolean replaced;

	public PatternRewriteClassVisitor(ClassVisitor cv, String validateMethodName) {
		super(ASM9, cv);
		this.validateMethodName = validateMethodName;
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		this.owner = name;
		super.visit(version, access, name, signature, superName, interfaces);
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
		MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
		return name.equals(validateMethodName) ? new PatternRewriteMethodVisitor(mv) : mv;
	}

	private class PatternRewriteMethodVisitor extends MethodVisitor {

		public PatternRewriteMethodVisitor(MethodVisitor mv) {
			super(ASM9, mv);
		}

		@Override
		public void visitMethodInsn(int opcode, String ownerInternal, String name, String desc, boolean itf) {
			boolean isPatternCompile = opcode == INVOKESTATIC && "java/util/regex/Pattern".equals(ownerInternal)
					&& "compile".equals(name) && "(Ljava/lang/String;I)Ljava/util/regex/Pattern;".equals(desc);

			if (!isPatternCompile) {
				super.visitMethodInsn(opcode, ownerInternal, name, desc, itf);
				return;
			}

			replaced = true;

			// Replace with call to synthetic helper: getCachedPattern(String, int)
			super.visitMethodInsn(INVOKESTATIC, owner, "getCachedPattern",
					"(Ljava/lang/String;I)Ljava/util/regex/Pattern;", false);
		}
	}

	@Override
	public void visitEnd() {
		if (replaced) {
			// --- Add static final field regexpCache ---
			cv.visitField(ACC_PRIVATE | ACC_STATIC | ACC_FINAL | ACC_SYNTHETIC, "regexpCache", "Ljava/util/Map;", null,
					null).visitEnd();

			// --- Add static initializer ---
			MethodVisitor clinit = cv.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
			clinit.visitCode();
			clinit.visitTypeInsn(NEW, "java/util/HashMap");
			clinit.visitInsn(DUP);
			clinit.visitMethodInsn(INVOKESPECIAL, "java/util/HashMap", "<init>", "()V", false);
			clinit.visitFieldInsn(PUTSTATIC, owner, "regexpCache", "Ljava/util/Map;");
			clinit.visitInsn(RETURN);
			clinit.visitMaxs(2, 0);
			clinit.visitEnd();

			// --- Add synthetic helper method ---
			MethodVisitor mv = cv.visitMethod(ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC, "getCachedPattern",
					"(Ljava/lang/String;I)Ljava/util/regex/Pattern;", null, null);
			mv.visitCode();
			// Build key = regex + "\u0000" + flags
			mv.visitVarInsn(ALOAD, 0); // regex
			mv.visitVarInsn(ILOAD, 1); // flags
			mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
			mv.visitInsn(DUP);
			mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
					"(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
			mv.visitLdcInsn("\u0000");
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
					"(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
			mv.visitVarInsn(ILOAD, 1);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;",
					false);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
			mv.visitVarInsn(ASTORE, 2); // key

			// return (Pattern) regexpCache.computeIfAbsent(key, k -> Pattern.compile(regex,
			// flags))
			mv.visitFieldInsn(GETSTATIC, owner, "regexpCache", "Ljava/util/Map;");
			mv.visitVarInsn(ALOAD, 2); // key
			mv.visitInvokeDynamicInsn("apply", "()Ljava/util/function/Function;",
					new Handle(H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
							"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;"
									+ "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;"
									+ "Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)"
									+ "Ljava/lang/invoke/CallSite;",
							false),
					Type.getType("(Ljava/lang/Object;)Ljava/lang/Object;"), new Handle(H_INVOKESTATIC, owner,
							"lambda$0", "(Ljava/lang/String;)Ljava/util/regex/Pattern;", false),
					Type.getType("(Ljava/lang/String;)Ljava/util/regex/Pattern;"));

			mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "computeIfAbsent",
					"(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;", true);
			mv.visitTypeInsn(CHECKCAST, "java/util/regex/Pattern");
			mv.visitInsn(ARETURN);
			mv.visitMaxs(4, 3);
			mv.visitEnd();

			// --- Add lambda$0(String) for invokedynamic ---
			MethodVisitor lambda = cv.visitMethod(ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC, "lambda$0",
					"(Ljava/lang/String;)Ljava/util/regex/Pattern;", null, null);
			lambda.visitCode();

			// Split k using "\u0000" as in previous code
			lambda.visitVarInsn(ALOAD, 0); // load k
			lambda.visitLdcInsn("\u0000");
			lambda.visitIntInsn(BIPUSH, 2);
			lambda.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "split",
					"(Ljava/lang/String;I)[Ljava/lang/String;", false);
			lambda.visitVarInsn(ASTORE, 1); // store parts[]

			lambda.visitVarInsn(ALOAD, 1);
			lambda.visitInsn(ICONST_0);
			lambda.visitInsn(AALOAD); // parts[0]
			lambda.visitVarInsn(ALOAD, 1);
			lambda.visitInsn(ICONST_1);
			lambda.visitInsn(AALOAD); // parts[1]
			lambda.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "parseInt", "(Ljava/lang/String;)I", false);
			lambda.visitMethodInsn(INVOKESTATIC, "java/util/regex/Pattern", "compile",
					"(Ljava/lang/String;I)Ljava/util/regex/Pattern;", false);

			lambda.visitInsn(ARETURN);
			lambda.visitMaxs(3, 2);
			lambda.visitEnd();
		}

		super.visitEnd();
	}

}
