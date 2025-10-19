/*
 * Copyright 2025 ...
 */
package org.jmolecules.bytebuddy;

import static java.lang.String.format;
import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.jmolecules.bytebuddy.PluginUtils.markGenerated;

import org.jmolecules.bytebuddy.PluginLogger.Log;

import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodDescription.InDefinedShape;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.Implementation.Context;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.implementation.SuperMethodCall;
import net.bytebuddy.implementation.bytecode.ByteCodeAppender;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;

class VaadooImplementor {

	private static final String VALIDATE_METHOD_NAME_1 = "validate";
	private static final String VALIDATE_METHOD_NAME_2 = "__vaadoo$__validate";

	JMoleculesTypeBuilder implementVaadoo(JMoleculesTypeBuilder type, Log log) {
		String methodName = nonExistingMethodName(type);
		return type //
				.mapBuilder(t -> addValidationMethod(t, methodName, log))
				.mapBuilder(t -> injectValidationIntoConstructors(t, methodName));
	}

	private static String nonExistingMethodName(JMoleculesTypeBuilder type) {
		if (type.tryFindMethod(target -> hasNoArgsMethodNamed(target, VALIDATE_METHOD_NAME_1)) == null) {
			return VALIDATE_METHOD_NAME_1;
		}

		String baseMethodName = VALIDATE_METHOD_NAME_2;
		String methodName = baseMethodName;
		for (int i = 0;; i++) {
			final String currentName = methodName;
			if (type.tryFindMethod(target -> hasNoArgsMethodNamed(target, currentName)) == null) {
				break;
			}
			methodName = baseMethodName + "_" + i++;
		}
		return methodName;
	}

	private static boolean hasNoArgsMethodNamed(InDefinedShape target, String methodName) {
		return target.getName().equals(methodName) && target.getParameters().size() == 0;
	}

	private Builder<?> addValidationMethod(Builder<?> builder, String methodName, Log log) {
		log.info("Implementing validate method #{}.", methodName);
		
	    return markGenerated(builder.defineMethod(methodName, void.class)
		        .intercept(new Implementation.Simple(new ValidateValueAppender())));
	}

	/**
	 * ByteBuddy appender that emits bytecode for:
	 *
	 * if (this.value == null) {
	 *     throw new IllegalStateException("Vaadoo validation failed: value is null");
	 * }
	 */
	private static class ValidateValueAppender implements ByteCodeAppender {

	    @Override
	    public Size apply(MethodVisitor mv, Context implementationContext,
	                      MethodDescription instrumentedMethod) {
	    	
	    	int maxStack = 3; // base stack requirement
	    	TypeDescription type = implementationContext.getInstrumentedType();

	    	for (FieldDescription.InDefinedShape field : type.getDeclaredFields()) {
	            if (field.isStatic() || field.isSynthetic()) {
	                continue; // skip irrelevant fields
	            }
				Label end = new Label();
				// load `this`
				mv.visitVarInsn(Opcodes.ALOAD, 0);
	            // get `this.<field>`
	            mv.visitFieldInsn(
	                    Opcodes.GETFIELD,
	                    type.getInternalName(),
	                    field.getName(),
	                    field.getType().asErasure().getDescriptor());

				// if not null, jump to end
				mv.visitJumpInsn(Opcodes.IFNONNULL, end);
				// --- throw new IllegalStateException("Vaadoo validation failed: value is null"); ---
				mv.visitTypeInsn(Opcodes.NEW, "java/lang/IllegalStateException"); // create new exception
				mv.visitInsn(Opcodes.DUP); // duplicate for constructor call
	            mv.visitLdcInsn(format("field '%s' is null", field.getName()));
				mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/IllegalStateException", "<init>",
						"(Ljava/lang/String;)V", false);
				mv.visitInsn(Opcodes.ATHROW); // throw it
				// --- end label ---
				mv.visitLabel(end);
				mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
			}
			mv.visitInsn(Opcodes.RETURN);

	        // maximum stack used: ALOAD_0 + GETFIELD + NEW + DUP + LDC + INVOKESPECIAL + ATHROW
	        return new Size(maxStack, instrumentedMethod.getStackSize());
	    }
	}


	private Builder<?> injectValidationIntoConstructors(Builder<?> builder, String methodName) {
		return builder.constructor(any())
				.intercept(SuperMethodCall.INSTANCE.andThen(MethodCall.invoke(named(methodName))));
	}

}
