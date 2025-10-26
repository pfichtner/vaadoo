/*
 * Copyright 2022-2025 the original author or authors.
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
package org.jmolecules.bytebuddy.vaadoo;

import static java.util.stream.IntStream.range;

import java.util.Iterator;
import java.util.List;

import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.annotation.AnnotationList;
import net.bytebuddy.description.method.ParameterDescription.InDefinedShape;
import net.bytebuddy.description.method.ParameterList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.StackSize;
import net.bytebuddy.jar.asm.Type;

public class Parameters implements Iterable<Parameters.Parameter> {

	@Deprecated // copied from vaadoo-poc
	static class EnumEntry {
		Type type;
		String value;

		EnumEntry(Type type, String value) {
			this.type = type;
			this.value = value;
		}

		Type type() {
			return type;
		}

		String value() {
			return value;
		}
	}

	public static Parameters of(ParameterList<InDefinedShape> parameterList) {
		return new Parameters(parameterList);
	}

	private List<Parameter> values;
	private ParameterList<InDefinedShape> parameterList;

	public static interface Parameter {

		int index();

		String name();

		TypeDescription type();

		int offset();

		List<TypeDescription> annotations();

	}

	private class ParameterWrapper implements Parameter {

		private final int index;

		public ParameterWrapper(int index) {
			this.index = index;
		}

		private InDefinedShape definedShape() {
			return parameterList.get(index);
		}

		@Override
		public int index() {
			return index;
		}

		@Override
		public String name() {
			return definedShape().getName();
		}

		@Override
		public TypeDescription type() {
			return definedShape().getType().asErasure();
		}

		@Override
		public int offset() {
			return index == 0 ? 0 : StackSize.of(values.subList(0, index).stream().map(Parameter::type).toList());
		}

		@Override
		public List<TypeDescription> annotations() {
			return annotationList().stream().map(AnnotationDescription::getAnnotationType)
					.map(TypeDescription::asErasure).toList();
		}

		private AnnotationList annotationList() {
			return definedShape().getDeclaredAnnotations();
		}

	}

	private Parameters(ParameterList<InDefinedShape> parameterList) {
		this.parameterList = parameterList;
		this.values = range(0, parameterList.size()).<Parameter>mapToObj(ParameterWrapper::new).toList();
	}

	public Parameter param(int index) {
		return values.get(index);
	}

	public List<TypeDescription> types() {
		return values.stream().map(Parameter::type).toList();
	}

	public int count() {
		return values.size();
	}

	@Override
	public Iterator<Parameter> iterator() {
		return values.iterator();
	}

}
