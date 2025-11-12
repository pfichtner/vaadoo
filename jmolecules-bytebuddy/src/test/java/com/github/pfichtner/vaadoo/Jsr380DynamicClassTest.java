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

	TestClassBuilder testClass = new TestClassBuilder("com.example.Generated");
	TestClassBuilder classAnnotatedByValueObject = testClass.annotatedByValueObject();
	TestClassBuilder classThatImplementsValueObject = testClass.thatImplementsValueObject();

	ConstructorDefinition notNullObject = new ConstructorDefinition(
			List.of(new ParameterDefinition(Object.class, List.of(NotNull.class))));
	Object[] nullArg = new Object[] { null };

	static Unloaded<Object> a(TestClassBuilder builder) {
		return builder.build();
	}

	@Test
	void noArg() throws Exception {
		var constructor = new ConstructorDefinition(emptyList());
		var unloaded = a(testClass.thatImplementsValueObject().withConstructor(constructor));
		approveTransformed(constructor.params(), unloaded);
	}

	@Test
	void implementingValueObjectAndAnnotatedByValueObjectIsTheSame() throws Exception {
		var transformed1 = transformClass(a(classThatImplementsValueObject.withConstructor(notNullObject)));
		var transformed2 = transformClass(a(classAnnotatedByValueObject.withConstructor(notNullObject)));
		var e1 = assertThrows(RuntimeException.class, () -> newInstance(transformed1, nullArg));
		var e2 = assertThrows(RuntimeException.class, () -> newInstance(transformed2, nullArg));
		assertThat(e1).isExactlyInstanceOf(e2.getClass()).hasMessage(e2.getMessage());
	}

	@Test
	void implementingEntityDoesNotAddBytecode() throws Exception {
		var transformed = transformClass(
				a(testClass.withInterface(org.jmolecules.ddd.types.Entity.class).withConstructor(notNullObject)));
		newInstance(transformed, nullArg);
	}

	@Test
	void alreadyHasValidateMethod() throws Exception {
		var unloaded = a(testClass.thatImplementsValueObject().withConstructor(notNullObject)
				.withMethod(new MethodDefinition("validate", emptyList())));
		approveTransformed(notNullObject.params(), unloaded);
	}

}
