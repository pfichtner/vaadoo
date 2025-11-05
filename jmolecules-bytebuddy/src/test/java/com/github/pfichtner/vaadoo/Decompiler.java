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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.benf.cfr.reader.api.CfrDriver;
import org.benf.cfr.reader.api.OutputSinkFactory;

import lombok.NoArgsConstructor;

@NoArgsConstructor(access = PRIVATE)
public final class Decompiler {

	public static String decompile(byte[] bytes) throws IOException {
		Path tempClass = Files.createTempFile("Transformed", ".class");
		Files.write(tempClass, bytes);

		try {
			StringBuilder decompiled = new StringBuilder();
			CfrDriver driver = new CfrDriver.Builder().withOutputSink(new OutputSinkFactory() {
				@Override
				public List<SinkClass> getSupportedSinks(SinkType sinkType, Collection<SinkClass> collection) {
					return List.of(SinkClass.STRING);
				}

				@Override
				public <T> Sink<T> getSink(SinkType sinkType, SinkClass sinkClass) {
					return s -> decompiled.append(s).append("\n");
				}
			}).build();
			driver.analyse(singletonList(tempClass.toString()));
			return normalizeAnnotations(decompiled.toString());
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
