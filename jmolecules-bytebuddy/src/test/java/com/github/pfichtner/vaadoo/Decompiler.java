/*
 * Copyright 2021-2025 the original author or authors.
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

import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static java.util.Comparator.comparing;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toList;
import static lombok.AccessLevel.PRIVATE;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.benf.cfr.reader.api.CfrDriver;
import org.benf.cfr.reader.api.OutputSinkFactory;

import lombok.NoArgsConstructor;

@NoArgsConstructor(access = PRIVATE)
public final class Decompiler {

	public static String decompile(byte[] bytes) throws IOException {
		Path tempClass = Files.createTempFile("Transformed", ".class");
		Files.write(tempClass, bytes);

		try {
			StringBuilder source = new StringBuilder();
			CfrDriver driver = new CfrDriver.Builder() //
					.withOptions(Map.of("comments", "false", "showversion", "false")) //
					.withOutputSink(new OutputSinkFactory() {
						@Override
						public List<SinkClass> getSupportedSinks(SinkType sinkType, Collection<SinkClass> collection) {
							return List.of(SinkClass.STRING);
						}

						@Override
						public <T> Sink<T> getSink(SinkType sinkType, SinkClass sinkClass) {
							return s -> source.append(s).append("\n");
						}
					}).build();
			driver.analyse(singletonList(tempClass.toString()));
			return normalizeAnnotations(source.toString());
		} finally {
			Files.delete(tempClass);
		}
	}

	private static String normalizeAnnotations(String source) {
		StringBuilder sb = new StringBuilder();

		Matcher matcher = Pattern.compile("@(\\w+)\\(([^)]*)\\)").matcher(source);
		while (matcher.find()) {
			String annotationName = matcher.group(1);
			String params = matcher.group(2);

			// Split by comma and sort keys
			List<String> paramList = Arrays.stream(params.split(",")) //
					.map(String::trim) //
					.filter(not(String::isEmpty)) //
					.sorted(comparing(p -> p.split("=")[0].trim())) //
					.collect(toList());

			matcher.appendReplacement(sb, format("@%s(%s)", annotationName, String.join(", ", paramList)));
		}
		matcher.appendTail(sb);
		return sb.toString();
	}

}
