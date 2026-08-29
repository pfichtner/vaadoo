package com.github.pfichtner.vaadoo;

import static net.bytebuddy.jar.asm.Opcodes.ACC_FINAL;
import static net.bytebuddy.jar.asm.Opcodes.ACC_PRIVATE;
import static net.bytebuddy.jar.asm.Opcodes.ACC_PUBLIC;
import static net.bytebuddy.jar.asm.Opcodes.ACC_STATIC;
import static net.bytebuddy.jar.asm.Opcodes.ACC_SYNTHETIC;
import static net.bytebuddy.jar.asm.Opcodes.ACONST_NULL;
import static net.bytebuddy.jar.asm.Opcodes.ALOAD;
import static net.bytebuddy.jar.asm.Opcodes.ARETURN;
import static net.bytebuddy.jar.asm.Opcodes.ASM9;
import static net.bytebuddy.jar.asm.Opcodes.ASTORE;
import static net.bytebuddy.jar.asm.Opcodes.DUP;
import static net.bytebuddy.jar.asm.Opcodes.H_INVOKESTATIC;
import static net.bytebuddy.jar.asm.Opcodes.IFEQ;
import static net.bytebuddy.jar.asm.Opcodes.INVOKESPECIAL;
import static net.bytebuddy.jar.asm.Opcodes.INVOKESTATIC;
import static net.bytebuddy.jar.asm.Opcodes.INVOKEVIRTUAL;
import static net.bytebuddy.jar.asm.Opcodes.NEW;
import static net.bytebuddy.jar.asm.Opcodes.PUTSTATIC;
import static net.bytebuddy.jar.asm.Opcodes.RETURN;

import java.util.Collection;
import java.util.Map;

import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.Handle;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Type;

public class CustomAnnotationValidatorClassVisitor extends ClassVisitor {

	private final Collection<CustomValidatorInfo> validators;
	private String owner;

	public CustomAnnotationValidatorClassVisitor(ClassVisitor cv, Collection<CustomValidatorInfo> validators) {
		super(ASM9, cv);
		this.validators = validators;
	}

	private boolean clinitFound;

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		this.owner = name;
		super.visit(version, access, name, signature, superName, interfaces);
		for (CustomValidatorInfo info : validators) {
			cv.visitField(ACC_PRIVATE | ACC_STATIC | ACC_FINAL | ACC_SYNTHETIC, info.getFieldName(),
					"L" + info.getValidatorClass().getInternalName() + ";", null, null).visitEnd();
			addHandlerMethod(info);
		}
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
		if ("<clinit>".equals(name)) {
			clinitFound = true;
			MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
			return new MethodVisitor(ASM9, mv) {
				@Override
				public void visitInsn(int opcode) {
					if (opcode == RETURN) {
						initializeValidators(this);
					}
					super.visitInsn(opcode);
				}
			};
		}
		return super.visitMethod(access, name, desc, signature, exceptions);
	}

	@Override
	public void visitEnd() {
		if (!clinitFound && !validators.isEmpty()) {
			MethodVisitor mv = cv.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
			mv.visitCode();
			initializeValidators(mv);
			mv.visitInsn(RETURN);
			mv.visitMaxs(0, 0);
			mv.visitEnd();
		}
		super.visitEnd();
	}

	private void addHandlerMethod(CustomValidatorInfo info) {
		String methodName = info.getFieldName() + "$handler";
		MethodVisitor mv = cv.visitMethod(ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC, methodName,
				"(Ljava/lang/Object;Ljava/lang/reflect/Method;[Ljava/lang/Object;)Ljava/lang/Object;", null, null);
		mv.visitCode();
		mv.visitVarInsn(ALOAD, 1); // Method
		mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Method", "getName", "()Ljava/lang/String;", false);
		mv.visitVarInsn(ASTORE, 3); // name

		for (Map.Entry<String, Object> entry : info.getAnnotationValues().entrySet()) {
			mv.visitVarInsn(ALOAD, 3);
			mv.visitLdcInsn(entry.getKey());
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
			Label next = new Label();
			mv.visitJumpInsn(IFEQ, next);

			injectValue(mv, entry.getValue());
			mv.visitInsn(ARETURN);
			mv.visitLabel(next);
		}

		mv.visitInsn(ACONST_NULL);
		mv.visitInsn(ARETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
	}

	private void injectValue(MethodVisitor mv, Object value) {
		if (value instanceof String) {
			mv.visitLdcInsn(value);
		} else if (value instanceof Integer) {
			mv.visitLdcInsn(value);
			mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
		} else if (value instanceof Boolean) {
			mv.visitLdcInsn(((Boolean) value) ? 1 : 0);
			mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
		} else if (value instanceof Long) {
			mv.visitLdcInsn(value);
			mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
		} else if (value instanceof Double) {
			mv.visitLdcInsn(value);
			mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
		} else if (value instanceof Float) {
			mv.visitLdcInsn(value);
			mv.visitMethodInsn(INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
		} else if (value instanceof net.bytebuddy.description.enumeration.EnumerationDescription) {
			net.bytebuddy.description.enumeration.EnumerationDescription enumValue = (net.bytebuddy.description.enumeration.EnumerationDescription) value;
			mv.visitFieldInsn(net.bytebuddy.jar.asm.Opcodes.GETSTATIC, enumValue.getEnumerationType().getInternalName(),
					enumValue.getValue(), enumValue.getEnumerationType().getDescriptor());
		} else if (value instanceof net.bytebuddy.description.type.TypeDescription) {
			mv.visitLdcInsn(
					Type.getObjectType(((net.bytebuddy.description.type.TypeDescription) value).getInternalName()));
		} else if (value instanceof net.bytebuddy.description.type.TypeDescription[]) {
			net.bytebuddy.description.type.TypeDescription[] array = (net.bytebuddy.description.type.TypeDescription[]) value;
			mv.visitLdcInsn(array.length);
			mv.visitTypeInsn(net.bytebuddy.jar.asm.Opcodes.ANEWARRAY, "java/lang/Class");
			for (int i = 0; i < array.length; i++) {
				mv.visitInsn(DUP);
				mv.visitLdcInsn(i);
				mv.visitLdcInsn(Type.getObjectType(array[i].getInternalName()));
				mv.visitInsn(net.bytebuddy.jar.asm.Opcodes.AASTORE);
			}
		} else if (value instanceof net.bytebuddy.description.enumeration.EnumerationDescription[]) {
			net.bytebuddy.description.enumeration.EnumerationDescription[] array = (net.bytebuddy.description.enumeration.EnumerationDescription[]) value;
			String enumInternalName = array.length > 0 ? array[0].getEnumerationType().getInternalName() : "java/lang/Enum";
			mv.visitLdcInsn(array.length);
			mv.visitTypeInsn(net.bytebuddy.jar.asm.Opcodes.ANEWARRAY, enumInternalName);
			for (int i = 0; i < array.length; i++) {
				mv.visitInsn(DUP);
				mv.visitLdcInsn(i);
				mv.visitFieldInsn(net.bytebuddy.jar.asm.Opcodes.GETSTATIC,
						array[i].getEnumerationType().getInternalName(), array[i].getValue(),
						array[i].getEnumerationType().getDescriptor());
				mv.visitInsn(net.bytebuddy.jar.asm.Opcodes.AASTORE);
			}
		} else if (value instanceof String[]) {
			String[] array = (String[]) value;
			mv.visitLdcInsn(array.length);
			mv.visitTypeInsn(net.bytebuddy.jar.asm.Opcodes.ANEWARRAY, "java/lang/String");
			for (int i = 0; i < array.length; i++) {
				mv.visitInsn(DUP);
				mv.visitLdcInsn(i);
				mv.visitLdcInsn(array[i]);
				mv.visitInsn(net.bytebuddy.jar.asm.Opcodes.AASTORE);
			}
		} else if (value instanceof int[]) {
			int[] array = (int[]) value;
			mv.visitLdcInsn(array.length);
			mv.visitIntInsn(net.bytebuddy.jar.asm.Opcodes.NEWARRAY, net.bytebuddy.jar.asm.Opcodes.T_INT);
			for (int i = 0; i < array.length; i++) {
				mv.visitInsn(DUP);
				mv.visitLdcInsn(i);
				mv.visitLdcInsn(array[i]);
				mv.visitInsn(net.bytebuddy.jar.asm.Opcodes.IASTORE);
			}
		} else if (value instanceof long[]) {
			long[] array = (long[]) value;
			mv.visitLdcInsn(array.length);
			mv.visitIntInsn(net.bytebuddy.jar.asm.Opcodes.NEWARRAY, net.bytebuddy.jar.asm.Opcodes.T_LONG);
			for (int i = 0; i < array.length; i++) {
				mv.visitInsn(DUP);
				mv.visitLdcInsn(i);
				mv.visitLdcInsn(array[i]);
				mv.visitInsn(net.bytebuddy.jar.asm.Opcodes.LASTORE);
			}
		} else if (value instanceof boolean[]) {
			boolean[] array = (boolean[]) value;
			mv.visitLdcInsn(array.length);
			mv.visitIntInsn(net.bytebuddy.jar.asm.Opcodes.NEWARRAY, net.bytebuddy.jar.asm.Opcodes.T_BOOLEAN);
			for (int i = 0; i < array.length; i++) {
				mv.visitInsn(DUP);
				mv.visitLdcInsn(i);
				mv.visitLdcInsn(array[i] ? 1 : 0);
				mv.visitInsn(net.bytebuddy.jar.asm.Opcodes.BASTORE);
			}
		} else if (value instanceof double[]) {
			double[] array = (double[]) value;
			mv.visitLdcInsn(array.length);
			mv.visitIntInsn(net.bytebuddy.jar.asm.Opcodes.NEWARRAY, net.bytebuddy.jar.asm.Opcodes.T_DOUBLE);
			for (int i = 0; i < array.length; i++) {
				mv.visitInsn(DUP);
				mv.visitLdcInsn(i);
				mv.visitLdcInsn(array[i]);
				mv.visitInsn(net.bytebuddy.jar.asm.Opcodes.DASTORE);
			}
		} else if (value instanceof float[]) {
			float[] array = (float[]) value;
			mv.visitLdcInsn(array.length);
			mv.visitIntInsn(net.bytebuddy.jar.asm.Opcodes.NEWARRAY, net.bytebuddy.jar.asm.Opcodes.T_FLOAT);
			for (int i = 0; i < array.length; i++) {
				mv.visitInsn(DUP);
				mv.visitLdcInsn(i);
				mv.visitLdcInsn(array[i]);
				mv.visitInsn(net.bytebuddy.jar.asm.Opcodes.FASTORE);
			}
		} else if (value == null) {
			mv.visitInsn(ACONST_NULL);
		} else {
			// TODO handle arrays if needed
			mv.visitLdcInsn(value.toString());
		}
	}

	private void initializeValidators(MethodVisitor mv) {
		for (CustomValidatorInfo info : validators) {
			String validatorType = info.getValidatorClass().getInternalName();
			mv.visitTypeInsn(NEW, validatorType);
			mv.visitInsn(DUP);
			mv.visitMethodInsn(INVOKESPECIAL, validatorType, "<init>", "()V", false);
			mv.visitInsn(DUP); // DUP for initialize call

			// loader
			mv.visitLdcInsn(Type.getObjectType(owner));
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getClassLoader", "()Ljava/lang/ClassLoader;", false);

			// interfaces
			mv.visitInsn(net.bytebuddy.jar.asm.Opcodes.ICONST_1);
			mv.visitTypeInsn(net.bytebuddy.jar.asm.Opcodes.ANEWARRAY, "java/lang/Class");
			mv.visitInsn(DUP);
			mv.visitInsn(net.bytebuddy.jar.asm.Opcodes.ICONST_0);
			mv.visitLdcInsn(Type.getObjectType(info.getAnnotationType().getInternalName()));
			mv.visitInsn(net.bytebuddy.jar.asm.Opcodes.AASTORE);

			// InvocationHandler via LambdaMetafactory
			mv.visitInvokeDynamicInsn("invoke", "()Ljava/lang/reflect/InvocationHandler;",
					new Handle(H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
							"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
							false),
					Type.getType("(Ljava/lang/Object;Ljava/lang/reflect/Method;[Ljava/lang/Object;)Ljava/lang/Object;"),
					new Handle(H_INVOKESTATIC, owner, info.getFieldName() + "$handler",
							"(Ljava/lang/Object;Ljava/lang/reflect/Method;[Ljava/lang/Object;)Ljava/lang/Object;",
							false),
					Type.getType("(Ljava/lang/Object;Ljava/lang/reflect/Method;[Ljava/lang/Object;)Ljava/lang/Object;"));

			mv.visitMethodInsn(INVOKESTATIC, "java/lang/reflect/Proxy", "newProxyInstance",
					"(Ljava/lang/ClassLoader;[Ljava/lang/Class;Ljava/lang/reflect/InvocationHandler;)Ljava/lang/Object;",
					false);
			mv.visitTypeInsn(net.bytebuddy.jar.asm.Opcodes.CHECKCAST, info.getAnnotationType().getInternalName());

			// call initialize
			mv.visitMethodInsn(INVOKEVIRTUAL, validatorType, "initialize", "(Ljava/lang/annotation/Annotation;)V",
					false);

			// store in field
			mv.visitFieldInsn(PUTSTATIC, owner, info.getFieldName(), "L" + validatorType + ";");
		}
	}

}
