/*
 * Copyright 2025 ...
 */
package org.jmolecules.bytebuddy;

import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.named;

import org.jmolecules.bytebuddy.PluginLogger.Log;

import net.bytebuddy.description.method.MethodDescription.InDefinedShape;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.implementation.ExceptionMethod;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.implementation.SuperMethodCall;

class VaadooImplementor {

	private static final String VALIDATE_METHOD_NAME_1 = "validate";
	private static final String VALIDATE_METHOD_NAME_2 = "__vaadoo$__validate";

	JMoleculesTypeBuilder implementVaadoo(JMoleculesTypeBuilder type, Log log) {
		String methodName = nonExistingMethodName(type);
		return type //
				.mapBuilder(t -> addValidationMethod(t, methodName))
				.mapBuilder(t -> injectValidationIntoConstructors(t, methodName));
	}

	private static String nonExistingMethodName(JMoleculesTypeBuilder type) {
		if (type.tryFindMethod(target -> isValidateMethod(target, VALIDATE_METHOD_NAME_1)) == null) {
			return VALIDATE_METHOD_NAME_1;
		}

		String baseMethodName = VALIDATE_METHOD_NAME_2;
		String methodName = baseMethodName;
		for (int i = 0;; i++) {
			final String currentName = methodName;
			if (type.tryFindMethod(target -> isValidateMethod(target, currentName)) == null) {
				break;
			}
			methodName = baseMethodName + "_" + i++;
		}
		return methodName;
	}

	private static boolean isValidateMethod(InDefinedShape target, String methodName) {
		return target.getName().equals(methodName) && target.getParameters().size() == 0;
	}

	private Builder<?> addValidationMethod(Builder<?> builder, String methodName) {
		return builder.defineMethod(methodName, void.class).intercept(ExceptionMethod
				.throwing(IllegalStateException.class, "Vaadoo validation failed: override or disable this method"));
	}

	private Builder<?> injectValidationIntoConstructors(Builder<?> builder, String methodName) {
		return builder.constructor(any())
				.intercept(SuperMethodCall.INSTANCE.andThen(MethodCall.invoke(named(methodName))));
	}

}
