/*
 * Copyright 2025 ...
 */
package org.jmolecules.bytebuddy;

import static java.util.stream.Collectors.joining;
import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.jmolecules.bytebuddy.PluginUtils.markGenerated;

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
		
	    String fieldNames = builder.toTypeDescription().getDeclaredFields().stream()
	            .map(net.bytebuddy.description.field.FieldDescription.InDefinedShape::getName)
	            .sorted()
	            .collect(joining(", "));

	    String message = String.format(
	        "Vaadoo validation failed: override or disable this method. Fields: [%s]",
	        fieldNames
	    );
		
		return markGenerated(builder.defineMethod(methodName, void.class).intercept(ExceptionMethod
				.throwing(IllegalStateException.class, message)));
	}

	private Builder<?> injectValidationIntoConstructors(Builder<?> builder, String methodName) {
		return builder.constructor(any())
				.intercept(SuperMethodCall.INSTANCE.andThen(MethodCall.invoke(named(methodName))));
	}

}
