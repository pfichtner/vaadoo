package com.github.pfichtner.vaadoo;

import static net.bytebuddy.jar.asm.ClassWriter.COMPUTE_FRAMES;
import static net.bytebuddy.jar.asm.Opcodes.ACC_PRIVATE;
import static net.bytebuddy.jar.asm.Opcodes.ACC_STATIC;
import static net.bytebuddy.jar.asm.Opcodes.ASM9;
import static net.bytebuddy.jar.asm.Opcodes.ILOAD;
import static net.bytebuddy.jar.asm.Opcodes.INVOKESPECIAL;
import static net.bytebuddy.jar.asm.Opcodes.INVOKESTATIC;
import static net.bytebuddy.jar.asm.Opcodes.RETURN;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.field.FieldDescription.InDefinedShape;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.Implementation.Context;
import net.bytebuddy.jar.asm.AnnotationVisitor;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Type;
import net.bytebuddy.pool.TypePool;

public class AddValidationToConstructors implements AsmVisitorWrapper {

	private final CodeEmitter codeEmitter;

	public AddValidationToConstructors(CodeEmitter codeEmitter) {
		this.codeEmitter = codeEmitter;
	}

	@Override
	public int mergeReader(int flags) {
		return flags;
	}

	@Override
	public int mergeWriter(int flags) {
		return flags | COMPUTE_FRAMES;
	}

	@Override
	public ClassVisitor wrap(TypeDescription instrumentedType, ClassVisitor classVisitor, Context implementationContext,
			TypePool typePool, FieldList<InDefinedShape> fields, MethodList<?> methods, int writerFlags,
			int readerFlags) {
		return new ClassVisitor(ASM9, classVisitor) {

			private String className;
			private final List<ConstructorVisitor> constructorVisitors = new ArrayList<>();
			private final List<String> methodNames = new ArrayList<>();

			@Override
			public void visit(int version, int access, String name, String signature, String superName,
					String[] interfaces) {
				this.className = name;
				super.visit(version, access, name, signature, superName, interfaces);
				methods.stream().map(MethodDescription::getName).forEach(methodNames::add);
			}

			@Override
			public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
					String[] exceptions) {
				MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
				if (!"<init>".equals(name)) {
					return mv;
				}

				ConstructorVisitor constructorVisitor = new ConstructorVisitor(ASM9, mv, className, descriptor,
						methodNames);
				constructorVisitors.add(constructorVisitor);
				return constructorVisitor;
			}

			@Override
			public void visitEnd() {
				super.visitEnd();
				for (ConstructorVisitor visitor : constructorVisitors) {
					if (visitor.methodAddedName != null) {
						addValidateMethod(visitor.methodAddedName, visitor.methodDescriptor, visitor.parameterInfos);
					}
				}
			}

			private void addValidateMethod(String name, String signature, Map<Integer, ParameterInfo> parameterInfos) {
				MethodVisitor mv = cv.visitMethod(ACC_PRIVATE | ACC_STATIC, name, signature, null, null);
				mv.visitCode();

				for (ParameterInfo parameter : parameterInfos.values()) {
					if (parameter.hasAnnotation(Type.getDescriptor(NotNull.class))) {
						codeEmitter.addNotNullCheck(mv, parameter);
					} else if (parameter.hasAnnotation(Type.getDescriptor(Null.class))) {
						codeEmitter.addNullCheck(mv, parameter);
					} else if (parameter.hasAnnotation(Type.getDescriptor(NotBlank.class))) {
						// TODO check if type is CharSequence
						codeEmitter.addNotNullCheck(mv, parameter);
						codeEmitter.addNotBlankCheck(mv, parameter);
					} else if (parameter.hasAnnotation(Type.getDescriptor(NotEmpty.class))) {
						// TODO check if type is CharSequence/Collection/Map/Array
						codeEmitter.addNotNullCheck(mv, parameter);
						codeEmitter.addNotEmptyCheck(mv, parameter);
					} else if (parameter.hasAnnotation(Type.getDescriptor(AssertTrue.class))) {
						// TODO check if type is boolean/Boolean
						codeEmitter.addIsTrueCheck(mv, parameter);
					} else if (parameter.hasAnnotation(Type.getDescriptor(AssertFalse.class))) {
						// TODO check if type is boolean/Boolean
						codeEmitter.addIsFalseCheck(mv, parameter);
					} else if (parameter.hasAnnotation(Type.getDescriptor(Min.class))) {
						// TODO check if type is BigDecimal, BigInteger, byte, short, int, long and
						// their respective wrappers
						codeEmitter.addMinCheck(mv, parameter);
					}
				}

				mv.visitInsn(RETURN);
				mv.visitMaxs(parameterInfos.entrySet().size(), parameterInfos.entrySet().size());
				mv.visitEnd();
			}

		};
	}

	private static class ConstructorVisitor extends MethodVisitor {

		private final String className;
		private final String methodDescriptor;
		private final List<String> methodNames;
		private final Map<Integer, ParameterInfo> parameterInfos = new TreeMap<>();
		private String methodAddedName;

		public ConstructorVisitor(int api, MethodVisitor methodVisitor, String className, String methodDescriptor,
				List<String> methodNames) {
			super(api, methodVisitor);
			this.className = className;
			this.methodDescriptor = methodDescriptor;
			this.methodNames = methodNames;
		}

		@Override
		public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
			ParameterInfo parameterInfo = parameterInfo(parameter);
			parameterInfo.addAnnotation(descriptor);
			return new AnnotationVisitor(ASM9) {
				@Override
				public void visit(String name, Object value) {
					parameterInfo.addAnnotationValue(name, value);
					super.visit(name, value);
				}
			};
		}

		@Override
		public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
			super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
			if (opcode == INVOKESPECIAL && name.equals("<init>")) {
				addValidateMethodCall(Type.getArgumentTypes(methodDescriptor));
			}
		}

		private void addValidateMethodCall(Type[] methodAddedArgumentTypes) {
			if (methodAddedArgumentTypes.length > 0) {
				int index = 1;
				for (Type argType : methodAddedArgumentTypes) {
					mv.visitVarInsn(argType.getOpcode(ILOAD), index);
					index += argType.getSize();
				}
				methodAddedName = constructMethodName();
				methodNames.add(methodAddedName);
				mv.visitMethodInsn(INVOKESTATIC, className, methodAddedName,
						Type.getMethodDescriptor(Type.VOID_TYPE, methodAddedArgumentTypes), false);
			}
		}

		private String constructMethodName() {
			String methodName = "validate";
			for (int i = 1; methodNames.contains(methodName); i++) {
				methodName = "validate$" + i;
			}
			return methodName;
		}

		@Override
		public void visitLocalVariable(String name, String descriptor, String signature, Label start, Label end,
				int index) {
			int parameter = index - 1;
			if (parameter >= 0) {
				parameterInfo(parameter).name(name).type(Type.getType(descriptor));
			}
			super.visitLocalVariable(name, descriptor, signature, start, end, index);
		}

		private ParameterInfo parameterInfo(int parameter) {
			return parameterInfos.computeIfAbsent(parameter, idx -> new ParameterInfo(idx.intValue()));
		}

	}

}