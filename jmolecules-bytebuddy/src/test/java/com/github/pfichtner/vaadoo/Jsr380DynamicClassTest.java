package com.github.pfichtner.vaadoo;

import static com.github.pfichtner.vaadoo.ApprovalUtil.approveTransformed;
import static com.github.pfichtner.vaadoo.Transformer.newInstance;
import static com.github.pfichtner.vaadoo.Transformer.transformClass;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.github.pfichtner.vaadoo.TestClassBuilder.ConstructorDefinition;
import com.github.pfichtner.vaadoo.TestClassBuilder.MethodDefinition;
import com.github.pfichtner.vaadoo.TestClassBuilder.ParameterDefinition;

import jakarta.validation.constraints.NotNull;
import net.bytebuddy.dynamic.DynamicType.Unloaded;

class Jsr380DynamicClassTest {

	ConstructorDefinition notNullObject = new ConstructorDefinition(
			List.of(new ParameterDefinition(Object.class, List.of(NotNull.class))));
	Object[] nullArg = new Object[] { null };

	@Test
	void noArg() throws Exception {
		var constructor = new ConstructorDefinition(emptyList());
		Unloaded<Object> testClass = new TestClassBuilder("com.example.Generated").implementsValueObject()
				.constructor(constructor).build();
		approveTransformed(constructor.getParameters(), testClass);
	}

	@Test
	void implementingValueObjectAndAnnotatedByValueObjectIsTheSame() throws Exception {
		var transformed1 = transformClass(new TestClassBuilder("com.example.Generated").implementsValueObject()
				.constructor(notNullObject).build());
		var transformed2 = transformClass(new TestClassBuilder("com.example.Generated").annotatedByValueObject()
				.constructor(notNullObject).build());
		var e1 = assertThrows(RuntimeException.class, () -> newInstance(transformed1, nullArg));
		var e2 = assertThrows(RuntimeException.class, () -> newInstance(transformed2, nullArg));
		assertThat(e1).isExactlyInstanceOf(e2.getClass()).hasMessage(e2.getMessage());
	}

	@Test
	void implementingEntityDoesNotAddBytecode() throws Exception {
		var transformed = transformClass(new TestClassBuilder("com.example.Generated")
				.withInterface(org.jmolecules.ddd.types.Entity.class).constructor(notNullObject).build());
		newInstance(transformed, nullArg);
	}

	@Test
	void alreadyHasValidateMethod() throws Exception {
		Unloaded<Object> testClass = new TestClassBuilder("com.example.Generated").implementsValueObject()
				.constructor(notNullObject).method(new MethodDefinition("validate", emptyList())).build();
		approveTransformed(notNullObject.getParameters(), testClass);
	}

}
