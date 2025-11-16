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

import static com.github.pfichtner.vaadoo.Buildable.a;
import static com.github.pfichtner.vaadoo.TestClassBuilder.testClass;
import static java.lang.String.format;
import static java.util.stream.Collectors.joining;
import static org.approvaltests.Approvals.settings;
import static org.approvaltests.Approvals.verify;
import static org.approvaltests.namer.NamerFactory.withParameters;

import java.io.IOException;
import java.util.List;

import org.approvaltests.core.Options;
import org.approvaltests.core.Scrubber;
import org.approvaltests.namer.NamedEnvironment;
import org.approvaltests.reporters.AutoApproveWhenEmptyReporter;
import org.approvaltests.scrubbers.RegExScrubber;

import com.github.pfichtner.vaadoo.TestClassBuilder.ConstructorDefinition;
import com.github.pfichtner.vaadoo.TestClassBuilder.ParameterDefinition;

import lombok.Value;
import net.bytebuddy.dynamic.DynamicType.Unloaded;

@Value
class Approver {

	Transformer transformer;

	@Value
	private static class Storyboard {
		List<ParameterDefinition> params;
		String source;
		String transformed;

		@Override
		public String toString() {
			String br = "-".repeat(64);
			return String.join("\n", //
					List.of(br, //
							"params annotations\n"
									+ params.stream().map(Object::toString).map("- "::concat).collect(joining("\n")), //
							br, //
							source, //
							br, //
							transformed, //
							br //
					) //
			);
		}
	}

	public void approveTransformed(List<ParameterDefinition> params) throws Exception {
		settings().allowMultipleVerifyCallsForThisClass();
		settings().allowMultipleVerifyCallsForThisMethod();
		var checksum = ParameterDefinition.stableChecksum(params);
		try (NamedEnvironment env = withParameters(checksum)) {
			var testClass = a(testClass("com.example.Generated_" + checksum).thatImplementsValueObject()
					.withConstructor(new ConstructorDefinition(params)));
			approveTransformed(params, testClass);
		}
	}

	public void approveTransformed(List<ParameterDefinition> params, Unloaded<?> generatedClass) throws Exception {
		Unloaded<?> transformedClass = transformer.transform(generatedClass);
		verify(new Storyboard(params, decompile(generatedClass), decompile(transformedClass)), options());
	}

	public static Options options() {
		return new Options().withScrubber(scrubber()).withReporter(new AutoApproveWhenEmptyReporter());
	}

	public static Scrubber scrubber() {
		return new RegExScrubber("auxiliary\\.\\S+\\s+\\S+[),]", i -> format("auxiliary.[AUX1_%d AUX1_%d]", i, i));
	}

	public static String decompile(Unloaded<?> clazz) throws IOException {
		return Decompiler.decompile(clazz.getBytes());
	}

}
