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

import static com.github.pfichtner.vaadoo.Jsr380Annos.isStandardJr380Anno;
import static net.bytebuddy.jar.asm.Opcodes.ASM9;

import net.bytebuddy.jar.asm.AnnotationVisitor;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Type;

public class ConstructorAnnotationRemover extends ClassVisitor {

	public ConstructorAnnotationRemover(ClassVisitor cv) {
		super(ASM9, cv);
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
			String[] exceptions) {
		MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
		if ("<init>".equals(name)) {
			return new MethodVisitor(api, mv) {
				@Override
				public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
					// TODO We should remove annotations that are annotated by Constraint (custom
					// validator) as well
					return shouldPurge(descriptor) ? null : super.visitAnnotation(descriptor, visible);
				}

				@Override
				public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
					// TODO We should remove annotations that are annotated by Constraint (custom
					// validator) as well
					return shouldPurge(descriptor) ? null
							: super.visitParameterAnnotation(parameter, descriptor, visible);
				}

				private boolean shouldPurge(String descriptor) {
					return isStandardJr380Anno(Type.getType(descriptor));
				}
			};
		}

		return mv;
	}
}
