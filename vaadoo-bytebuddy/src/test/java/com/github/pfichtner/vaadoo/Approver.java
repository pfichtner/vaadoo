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
import static org.approvaltests.Approvals.verify;

import java.io.IOException;
import java.util.List;

import org.approvaltests.Approvals;
import org.approvaltests.StoryBoard;
import org.approvaltests.core.Options;
import org.approvaltests.core.Scrubber;
import org.approvaltests.reporters.AutoApproveWhenEmptyReporter;
import org.approvaltests.scrubbers.RegExScrubber;

import com.github.pfichtner.vaadoo.TestClassBuilder.ConstructorDefinition;
import com.github.pfichtner.vaadoo.TestClassBuilder.ParameterDefinition;

import lombok.Value;
import net.bytebuddy.dynamic.DynamicType.Unloaded;

@Value
class Approver {

	Transformer transformer;

	public void approveTransformed(String story, List<ParameterDefinition> params) throws Exception {
		var checksum = ParameterDefinition.stableChecksum(params);
		var testClass = a(testClass("com.example.Generated_" + checksum).thatImplementsValueObject()
				.withConstructor(ConstructorDefinition.of(params)));
		approveTransformed(story, params, testClass, Approvals.NAMES.withParameters(checksum));
	}

	public void approveTransformed(String story, List<ParameterDefinition> params, Unloaded<?> generatedClass)
			throws Exception {
		approveTransformed(story, params, generatedClass, new Options());
	}

	public void approveTransformed(String story, List<ParameterDefinition> params, Unloaded<?> generatedClass,
			Options options) throws Exception {
		Unloaded<?> transformedClass = transformer.transform(generatedClass);
		StoryBoard sb = new StoryBoard();
		sb = sb.addFrame("Story", story);
		sb = sb.addDescription("params annotations");
		sb = params.stream().map(Object::toString).reduce(sb, (s, p) -> s.addDescriptionWithData("-", p), (l, __) -> l);
		sb = sb.addFrame("Source", decompile(generatedClass));
		sb = sb.addFrame("Transformed", decompile(transformedClass));
		verify(sb, configure(options));
	}

	private static Options configure(Options options) {
		return options.withScrubber(scrubber()).withReporter(new AutoApproveWhenEmptyReporter());
	}

	public static Scrubber scrubber() {
		return new RegExScrubber("auxiliary\\.\\S+\\s+\\S+[),]", i -> format("auxiliary.[AUX1_%d AUX1_%d]", i, i));
	}

	public static String decompile(Unloaded<?> clazz) throws IOException {
		return Decompiler.decompile(clazz.getBytes());
	}

}
