package com.github.pfichtner.vaadoo;

import static java.util.Collections.singletonList;
import static lombok.AccessLevel.PRIVATE;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import org.benf.cfr.reader.api.CfrDriver;
import org.benf.cfr.reader.api.CfrDriver.Builder;
import org.benf.cfr.reader.api.OutputSinkFactory;
import org.benf.cfr.reader.api.OutputSinkFactory.Sink;
import org.benf.cfr.reader.api.OutputSinkFactory.SinkClass;
import org.benf.cfr.reader.api.OutputSinkFactory.SinkType;

import lombok.NoArgsConstructor;

@NoArgsConstructor(access = PRIVATE)
public final class Decompiler {static String decompile(byte[] bytes) throws IOException {
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
			return decompiled.toString();
		} finally {
			Files.delete(tempClass);
		}
	}

}
