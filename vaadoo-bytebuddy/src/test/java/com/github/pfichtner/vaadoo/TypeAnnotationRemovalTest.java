package com.github.pfichtner.vaadoo;

import static com.github.pfichtner.vaadoo.Buildable.a;
import static com.github.pfichtner.vaadoo.TestClassBuilder.testClass;
import static com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy.config.VaadooConfigurationSupplier.VAADOO_CONFIG;
import static java.lang.String.format;
import static java.lang.System.lineSeparator;
import static java.nio.file.Files.walk;
import static java.util.Comparator.reverseOrder;
import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.pfichtner.vaadoo.TestClassBuilder.AnnotationDefinition;
import com.github.pfichtner.vaadoo.TestClassBuilder.ConstructorDefinition;
import com.github.pfichtner.vaadoo.TestClassBuilder.DefaultParameterDefinition;
import com.github.pfichtner.vaadoo.TestClassBuilder.TypeDefinition;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import net.bytebuddy.dynamic.DynamicType.Unloaded;

class TypeAnnotationRemovalTest {

	File projectRoot;
	Transformer transformer;

	@BeforeEach
	void setup() throws IOException {
		projectRoot = Files.createTempDirectory("project-root").toFile();
		new File(projectRoot, "target/classes").mkdirs();
		writeTo(new File(projectRoot, "pom.xml"), "");
		transformer = new Transformer().projectRoot(projectRoot);
	}

	@AfterEach
	void tearDown() throws IOException {
		try (var paths = walk(projectRoot.toPath())) {
			paths.sorted(reverseOrder()).map(Path::toFile).forEach(File::delete);
		}
	}

	@Test
	void removesNormalAnnotation() throws Exception {
		configure(Map.entry("vaadoo.removeJsr380Annotations", true));

		var anno = NotBlank.class;
		var param = DefaultParameterDefinition.of(String.class, AnnotationDefinition.of(anno));
		var generatedClass = a(testClass("com.example.NormalAnno").thatImplementsValueObject()
				.withConstructor(ConstructorDefinition.of(param)));

		Unloaded<?> transformedClass = transformer.transform(generatedClass);
		String decompiled = Decompiler.decompile(transformedClass.getBytes());

		assertThat(decompiled).doesNotContain(anno.getSimpleName());
	}

	@Test
	void removesTypeUseAnnotation() throws Exception {
		configure(Map.entry("vaadoo.removeJsr380Annotations", true));

		// List<@NotBlank String>
		var anno = NotBlank.class;
		var typeDef = TypeDefinition.of(List.class, String.class, AnnotationDefinition.of(anno));
		var param = DefaultParameterDefinition.of(typeDef);
		var generatedClass = a(testClass("com.example.TypeUseAnno").thatImplementsValueObject()
				.withConstructor(ConstructorDefinition.of(param)));

		Unloaded<?> transformedClass = transformer.transform(generatedClass);
		String decompiled = Decompiler.decompile(transformedClass.getBytes());

		assertThat(decompiled).doesNotContain(anno.getSimpleName());
	}

	@Test
	void removesMultipleTypeUseAnnotations() throws Exception {
		configure(Map.entry("vaadoo.removeJsr380Annotations", true));

		// Map<@NotBlank String, @NotNull Integer>
		var anno1 = NotNull.class;
		var anno2 = NotBlank.class;
		var typeDef = TypeDefinition.of(Map.class, List.of(String.class, Integer.class),
				List.of(List.of(AnnotationDefinition.of(anno2)), List.of(AnnotationDefinition.of(anno1))));

		var param = DefaultParameterDefinition.of(typeDef);
		var generatedClass = a(testClass("com.example.MultiTypeUseAnno").thatImplementsValueObject()
				.withConstructor(ConstructorDefinition.of(param)));

		Unloaded<?> transformedClass = transformer.transform(generatedClass);
		String decompiled = Decompiler.decompile(transformedClass.getBytes());

		assertSoftly(s -> {
			s.assertThat(decompiled).doesNotContain(anno1.getSimpleName());
			s.assertThat(decompiled).doesNotContain(anno2.getSimpleName());
		});
	}

	@SafeVarargs
	private void configure(Entry<String, Object>... entries) throws IOException {
		writeTo(new File(projectRoot, VAADOO_CONFIG), Map.ofEntries(entries));
	}

	private void writeTo(File file, Map<String, Object> data) throws IOException {
		writeTo(file, data.entrySet().stream().map(e -> format("%s=%s", e.getKey(), e.getValue()))
				.collect(joining(lineSeparator())));
	}

	private void writeTo(File file, String text) throws IOException {
		try (FileWriter writer = new FileWriter(file)) {
			writer.write(text);
		}
	}

}
