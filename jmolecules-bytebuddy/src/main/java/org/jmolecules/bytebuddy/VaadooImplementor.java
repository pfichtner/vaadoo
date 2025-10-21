/*
 * Copyright 2025 ...
 */
package org.jmolecules.bytebuddy;

import static java.lang.String.format;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toList;
import static net.bytebuddy.matcher.ElementMatchers.is;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.jmolecules.bytebuddy.PluginUtils.markGenerated;

import java.util.List;
import java.util.stream.Stream;

import org.jmolecules.bytebuddy.Parameters.Parameter;
import org.jmolecules.bytebuddy.PluginLogger.Log;

import lombok.RequiredArgsConstructor;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodDescription.InDefinedShape;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.implementation.SuperMethodCall;
import net.bytebuddy.implementation.bytecode.ByteCodeAppender;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;

class VaadooImplementor {

	private static final String VALIDATE_METHOD_BASE_NAME = "validate";

	JMoleculesTypeBuilder implementVaadoo(JMoleculesTypeBuilder type, Log log) {
		TypeDescription typeDescription = type.getTypeDescription();

		// Loop over all constructors
		for (InDefinedShape constructor : typeDescription.getDeclaredMethods().stream()
				.filter(MethodDescription::isConstructor).collect(toList())) {

			// Generate a unique method name per constructor
			// TODO we could overload (add validate(String,String) works also if there
			// already is a validate())
			String validateMethodName = nonExistingMethodName(typeDescription, VALIDATE_METHOD_BASE_NAME);

			// Extract constructor parameter types
			Parameters parameters = Parameters.of(constructor.getParameters());
			// Add static validate method
			type = type.mapBuilder(t -> addStaticValidationMethod(t, validateMethodName, parameters, log));

			// Inject call into this constructor
			type = type.mapBuilder(t -> injectValidationIntoConstructor(t, constructor, validateMethodName));
		}

		return type;
	}

	private static String nonExistingMethodName(TypeDescription typeDescription, String base) {
		List<String> methodNames = typeDescription.getDeclaredMethods().stream()
				.map(MethodDescription.InDefinedShape::getName).toList();
		return Stream.iterate(0, i -> i + 1) //
				.map(i -> (i == 0) ? base : base + "_" + i) //
				.filter(not(methodNames::contains)) //
				.findFirst() //
				.get(); // safe because stream is infinite, will always find a free name
	}

	private Builder<?> addStaticValidationMethod(Builder<?> builder, String methodName, Parameters parameters,
			Log log) {
		log.info("Implementing static validate method #{}.", methodName);
		return markGenerated(builder.defineMethod(methodName, void.class, Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC)
				.withParameters(parameters.types())
				.intercept(new Implementation.Simple(new StaticValidateAppender(parameters))));
	}

	private Builder<?> injectValidationIntoConstructor(Builder<?> builder, MethodDescription.InDefinedShape constructor,
			String validateMethodName) {
		return builder.constructor(is(constructor)) //
				.intercept(MethodCall.invoke(named(validateMethodName)).withAllArguments().andThen( //
						SuperMethodCall.INSTANCE //
				));
	}

	/**
	 * Emits static validate(...) method for constructor parameters. For each
	 * parameter: if null -> throw IllegalStateException("parameter X is null")
	 */
	@RequiredArgsConstructor
	private static class StaticValidateAppender implements ByteCodeAppender {

		private final Parameters parameters;

		@Override
		public Size apply(MethodVisitor mv, Implementation.Context context, MethodDescription instrumentedMethod) {
			int maxStack = 3;
			for (Parameter parameter : parameters) {
				Label end = new Label();

				// Load parameter i (0-based for static method)
				mv.visitVarInsn(Opcodes.ALOAD, parameter.index());

				// If not null, jump over
				mv.visitJumpInsn(Opcodes.IFNONNULL, end);

				// Else throw new IllegalStateException("parameter X is null")
				mv.visitTypeInsn(Opcodes.NEW, "java/lang/IllegalStateException");
				mv.visitInsn(Opcodes.DUP);
				mv.visitLdcInsn(format("parameter '%s' is null", parameter.name()));
				mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/IllegalStateException", "<init>",
						"(Ljava/lang/String;)V", false);
				mv.visitInsn(Opcodes.ATHROW);

				mv.visitLabel(end);
				mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
			}

			mv.visitInsn(Opcodes.RETURN);
			return new Size(maxStack, 0);
		}

	}

}