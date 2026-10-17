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
package com.github.pfichtner.vaadoo;

import static com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy.config.VaadooConfigurationSupplier.VAADOO_CONFIG;
import static java.lang.String.format;
import static java.lang.System.lineSeparator;
import static java.nio.file.Files.walk;
import static java.util.Comparator.reverseOrder;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static net.jqwik.api.ShrinkingMode.OFF;
import static org.approvaltests.Approvals.settings;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import org.approvaltests.ApprovalSettings;
import org.assertj.core.api.SoftAssertionsProvider.ThrowingRunnable;

import com.github.pfichtner.vaadoo.TestClassBuilder.AnnotationDefinition;
import com.github.pfichtner.vaadoo.TestClassBuilder.DefaultParameterDefinition;
import com.github.pfichtner.vaadoo.TestClassBuilder.ParameterDefinition;
import com.github.pfichtner.vaadoo.testclasses.custom.ComplexReproduction;
import com.github.pfichtner.vaadoo.testclasses.custom.MinReproduction;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class CustomValidatorInitializePBTest {

	static final Transformer transformer = new Transformer();

	@Provide
	Arbitrary<List<ParameterDefinition>> constructorParameters() {
		return parameterConfigGen().list().ofMinSize(1).ofMaxSize(3);
	}

	Arbitrary<ParameterDefinition> parameterConfigGen() {
		return Arbitraries.oneOf(
				Arbitraries.integers().between(0, 100).map(min -> DefaultParameterDefinition.of(Integer.class,
						AnnotationDefinition.of(MinReproduction.class, Map.of("min", min)))),
				Arbitraries.strings().alpha().numeric().ofLength(5).map(s -> DefaultParameterDefinition.of(String.class,
						AnnotationDefinition.of(ComplexReproduction.class, Map.of("stringValue", s, "intValue", 42,
								"boolValue", true, "enumValue", TimeUnit.DAYS, "classValue", String.class)))));
	}

	private static final String FIXED_SEED = "42";

	@Property(seed = FIXED_SEED, shrinking = OFF, tries = 5)
	void customValidatorsWithInitialize(@ForAll("constructorParameters") List<ParameterDefinition> params)
			throws Exception {
		var projectRoot = configure(keepJsr380Annotations());
		var approver = new Approver(new Transformer().projectRoot(projectRoot));
		ApprovalSettings settings = settings();
		settings.allowMultipleVerifyCallsForThisClass();
		settings.allowMultipleVerifyCallsForThisMethod();
		withProjectRoot(projectRoot, () -> approver.approveTransformed("Custom validators with initialize", params));
	}

	private static void withProjectRoot(File projectRoot, ThrowingRunnable runnable) throws Exception {
		try {
			runnable.run();
		} finally {
			try (var paths = walk(projectRoot.toPath())) {
				paths.sorted(reverseOrder()).map(Path::toFile).forEach(File::delete);
			}
		}
	}

	private static Entry<String, Object> keepJsr380Annotations() {
		return Map.entry("vaadoo.removeJsr380Annotations", false);
	}

	@SafeVarargs
	private static File configure(Entry<String, Object>... entries) throws IOException {
		File projectRoot = Files.createTempDirectory("project-root").toFile();
		new File(projectRoot, "target/classes").mkdirs();
		writeTo(new File(projectRoot, "pom.xml"), "");
		writeTo(new File(projectRoot, VAADOO_CONFIG), Map.ofEntries(entries));
		return projectRoot;
	}

	private static void writeTo(File file, Map<String, Object> data) throws IOException {
		writeTo(file, content(data));
	}

	private static String content(Map<String, Object> data) {
		return data.entrySet().stream() //
				.map(e -> format("%s=%s", e.getKey(), e.getValue())) //
				.collect(joining(lineSeparator()));
	}

	private static void writeTo(File file, String text) throws IOException {
		try (FileWriter writer = new FileWriter(file)) {
			writer.write(text);
		}
	}

}
