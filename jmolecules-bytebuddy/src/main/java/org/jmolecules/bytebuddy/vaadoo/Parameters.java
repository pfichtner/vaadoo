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

import static java.util.stream.Collectors.toList;
import static java.util.stream.IntStream.range;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.jmolecules.bytebuddy.vaadoo.Parameters.Parameter;

import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.annotation.AnnotationList;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.method.ParameterDescription.InDefinedShape;
import net.bytebuddy.description.method.ParameterList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.StackSize;
import net.bytebuddy.jar.asm.Type;

public class Parameters implements Iterable<Parameter> {

	public static Parameters of(ParameterList<InDefinedShape> parameterList) {
		return new Parameters(parameterList);
	}

	private final List<Parameter> values;
	private final ParameterList<InDefinedShape> parameterList;

	public static interface Parameter {

		int index();

		String name();

		TypeDescription type();

		int offset();

		TypeDescription[] annotations();

		Object annotationValue(Type annotation, String name);

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
			return index == 0 ? 0
					: StackSize.of(values.subList(0, index).stream().map(Parameter::type).collect(toList()));
		}

		@Override
		public TypeDescription[] annotations() {
			return annotationList().stream() //
					.map(AnnotationDescription::getAnnotationType) //
					.map(TypeDescription::asErasure) //
					.toArray(TypeDescription[]::new);
		}

		@Override
		public Object annotationValue(Type annotation, String name) {
			for (AnnotationDescription annotationDescription : annotationList()) {
				if (annotationDescription.getAnnotationType().getName().equals(annotation.getClassName())) {
					MethodList<MethodDescription.InDefinedShape> candidates = annotationDescription.getAnnotationType() //
							.getDeclaredMethods().filter(named(name) //
									.and(takesArguments(0)) //
									.and(isPublic()) //
									.and(not(isStatic())));
					return candidates.size() == 1 ? annotationDescription.getValue(candidates.getOnly()).resolve()
							: null;
				}
			}
			return null;
		}

		private AnnotationList annotationList() {
			return definedShape().getDeclaredAnnotations();
		}

		@Override
		public String toString() {
			return "ParameterWrapper [index()=" + index() + ", name()=" + name() + ", type()=" + type() + ", offset()="
					+ offset() + ", annotations()=" + Arrays.toString(annotations()) + "]";
		}

	}

	private Parameters(ParameterList<InDefinedShape> parameterList) {
		this.parameterList = parameterList;
		this.values = range(0, parameterList.size()).<Parameter>mapToObj(ParameterWrapper::new).collect(toList());
	}

	public Parameter param(int index) {
		return values.get(index);
	}

	public List<TypeDescription> types() {
		return values.stream().map(Parameter::type).collect(toList());
	}

	public int count() {
		return values.size();
	}

	@Override
	public Iterator<Parameter> iterator() {
		return values.iterator();
	}

}
