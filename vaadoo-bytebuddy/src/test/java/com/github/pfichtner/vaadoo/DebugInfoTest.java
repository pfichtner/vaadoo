package com.github.pfichtner.vaadoo;

import static com.github.pfichtner.vaadoo.Buildable.a;
import static com.github.pfichtner.vaadoo.TestClassBuilder.testClass;
import static net.bytebuddy.jar.asm.Opcodes.ASM9;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import com.github.pfichtner.vaadoo.TestClassBuilder.AnnotationDefinition;
import com.github.pfichtner.vaadoo.TestClassBuilder.ConstructorDefinition;
import com.github.pfichtner.vaadoo.TestClassBuilder.DefaultParameterDefinition;
import com.github.pfichtner.vaadoo.testclasses.RealClassWithLineNumbers;

import jakarta.validation.constraints.Pattern;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType.Unloaded;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.pool.TypePool;

class DebugInfoTest {

	@Test
	void transformedRealClassShouldHaveLineNumbersInConstructor() throws Exception {
		var typePool = TypePool.Default.of(RealClassWithLineNumbers.class.getClassLoader());
		var typeDescription = typePool.describe(RealClassWithLineNumbers.class.getName()).resolve();
		var unloaded = new ByteBuddy().redefine(typeDescription,
				ClassFileLocator.ForClassLoader.of(RealClassWithLineNumbers.class.getClassLoader())).make();

		var lineNumbers = lineNumbers(unloaded, n -> "<init>".equals(n));
		assertThat(lineNumbers).describedAs("Line numbers should be present in the instrumented constructor")
				.containsExactly(7, 8, 9);
	}

	@Test
	void transformedRealClassShouldHaveLineNumbersInCentralValidateMethod() throws Exception {
		var typePool = TypePool.Default.of(RealClassWithLineNumbers.class.getClassLoader());
		var typeDescription = typePool.describe(RealClassWithLineNumbers.class.getName()).resolve();
		var unloaded = new ByteBuddy().redefine(typeDescription,
				ClassFileLocator.ForClassLoader.of(RealClassWithLineNumbers.class.getClassLoader())).make();

		var lineNumbers = lineNumbers(unloaded, n -> "validate".equals(n));
		assertThat(lineNumbers).describedAs("Line numbers should be present in the central validate method")
				.containsExactly(7);
	}

	@Test
	void patternOptimizationMethodsShouldHaveLineNumbers() throws Exception {
		var constructor = ConstructorDefinition.of(DefaultParameterDefinition.of(String.class,
				AnnotationDefinition.of(Pattern.class, Map.of("regexp", "\\d*"))));
		var unloaded = a(testClass("com.example.PatternTest").thatImplementsValueObject().withConstructor(constructor));

		var lineNumbers = lineNumbers(unloaded, n -> n.equals("getCachedPattern") || n.startsWith("lambda$"));
		assertThat(lineNumbers).describedAs("Line numbers should be present in pattern optimization methods")
				.isEmpty();
	}

	@Test
	void generatedValidateMethodShouldHaveCorrectLineNumbers() throws Exception {
		var typePool = TypePool.Default.of(RealClassWithLineNumbers.class.getClassLoader());
		var typeDescription = typePool.describe(RealClassWithLineNumbers.class.getName()).resolve();
		var unloaded = new ByteBuddy().redefine(typeDescription,
				ClassFileLocator.ForClassLoader.of(RealClassWithLineNumbers.class.getClassLoader())).make();

		var lineNumbers = lineNumbers(unloaded, n -> n.startsWith("validate_"));
		// In RealClassWithLineNumbers.java, the constructor starts at line 7
		assertThat(lineNumbers).contains(7);
	}

	private List<Integer> lineNumbers(Unloaded<?> unloaded, Predicate<String> methodNamePredicate) throws Exception {
		Transformer transformer = new Transformer();
		var transformed = transformer.transform(unloaded);
		byte[] bytes = transformed.getBytes();

		List<Integer> lineNumbers = new ArrayList<>();
		new ClassReader(bytes).accept(lineNumberVisitor(methodNamePredicate, lineNumbers), 0);
		return lineNumbers;
	}

	private ClassVisitor lineNumberVisitor(Predicate<String> methodNamePredicate, List<Integer> lineNumbers) {
		return new ClassVisitor(ASM9) {
			@Override
			public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
					String[] exceptions) {
				if (methodNamePredicate.test(name)) {
					return new MethodVisitor(api) {
						@Override
						public void visitLineNumber(int line, net.bytebuddy.jar.asm.Label start) {
							lineNumbers.add(line);
						}
					};
				}
				return null;
			}
		};
	}
}
