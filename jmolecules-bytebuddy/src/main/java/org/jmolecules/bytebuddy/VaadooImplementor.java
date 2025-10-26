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
package org.jmolecules.bytebuddy;

import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toList;
import static net.bytebuddy.matcher.ElementMatchers.is;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.jmolecules.bytebuddy.PluginUtils.markGenerated;

import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Stream;

import org.jmolecules.bytebuddy.PluginLogger.Log;
import org.jmolecules.bytebuddy.vaadoo.MethodInjector;
import org.jmolecules.bytebuddy.vaadoo.Parameters;
import org.jmolecules.bytebuddy.vaadoo.Parameters.Parameter;

import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodDescription.InDefinedShape;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.implementation.SuperMethodCall;
import net.bytebuddy.implementation.bytecode.ByteCodeAppender;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.jar.asm.Type;

class VaadooImplementor {

	private static final String VALIDATE_METHOD_BASE_NAME = "validate";

	JMoleculesTypeBuilder implementVaadoo(JMoleculesTypeBuilder type, Log log) {
		TypeDescription typeDescription = type.getTypeDescription();

		// Loop over all constructors
		for (InDefinedShape constructor : typeDescription.getDeclaredMethods().stream()
				.filter(MethodDescription::isConstructor).collect(toList())) {
			// Extract constructor parameter types
			Parameters parameters = Parameters.of(constructor.getParameters());

			// Generate a unique method name per constructor
			// TODO we could overload (add validate(String,String) works also if there
			// already is a validate())
			String validateMethodName = nonExistingMethodName(typeDescription, VALIDATE_METHOD_BASE_NAME);

			// Add static validate method
			type = type.mapBuilder(t -> addStaticValidationMethod(t, validateMethodName, parameters, log));

			// Inject call into this constructor
			type = type.mapBuilder(t -> injectValidationIntoConstructor(t, constructor, validateMethodName));
		}

		return type;
	}

	private static String nonExistingMethodName(TypeDescription typeDescription, String base) {
		List<String> methodNames = typeDescription.getDeclaredMethods().stream()
				.map(MethodDescription.InDefinedShape::getName).collect(toList());
		return Stream.iterate(0, i -> i + 1) //
				.map(i -> (i == 0) ? base : base + "_" + i) //
				.filter(not(methodNames::contains)) //
				.findFirst() //
				.get(); // safe because stream is infinite, will always find a free name
	}

	private Builder<?> addStaticValidationMethod(Builder<?> builder, String validateMethodName, Parameters parameters,
			Log log) {
		log.info("Implementing static validate method #{}.", validateMethodName);
		return markGenerated(
				builder.defineMethod(validateMethodName, void.class, Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC)
						.withParameters(parameters.types()).intercept(
								new Implementation.Simple(new StaticValidateAppender(parameters, validateMethodName))));
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
		private final String validateMethodName;

		@Override
		public Size apply(MethodVisitor mv, Implementation.Context context, MethodDescription instrumentedMethod) {
			String methodDescriptor = Type.getMethodDescriptor(Type.VOID_TYPE,
					parameters.types().stream().map(TypeDescription::getName).map(Type::getObjectType).toArray(Type[]::new));
			for (Parameter parameter : parameters) {
				emitCheckNotNullInline(mv, parameter, validateMethodName, methodDescriptor);
			}
			mv.visitInsn(Opcodes.RETURN);
		    int maxStack = 4; // or compute dynamically based on emitted instructions
		    return new Size(maxStack, parameters.count());
		}

	}

	private static void emitCheckNotNullInline(MethodVisitor targetMv, Parameter parameter, String validateMethodName,
			String signatureOfTargetMethod) {
		MethodInjector methodInjector = new MethodInjector(
				org.jmolecules.bytebuddy.vaadoo.fragments.impl.JdkOnlyCodeFragment.class, signatureOfTargetMethod);
		try {
			Method method = org.jmolecules.bytebuddy.vaadoo.fragments.impl.JdkOnlyCodeFragment.class.getMethod("check",
					NotNull.class, Object.class);
			methodInjector.inject(targetMv, parameter, method);
		} catch (NoSuchMethodException | SecurityException e) {
			throw new RuntimeException(e);
		}

	}

}