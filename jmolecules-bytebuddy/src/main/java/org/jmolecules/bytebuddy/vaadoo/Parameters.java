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
package org.jmolecules.bytebuddy;

import static java.util.stream.Collectors.toList;
import static java.util.stream.IntStream.range;

import java.util.Iterator;
import java.util.List;

import net.bytebuddy.description.method.ParameterDescription.InDefinedShape;
import net.bytebuddy.description.method.ParameterList;
import net.bytebuddy.description.type.TypeDescription;

public class Parameters implements Iterable<Parameters.Parameter> {

	public static Parameters of(ParameterList<InDefinedShape> parameterList) {
		return new Parameters(parameterList);
	}

	private List<Parameter> values;

	public static interface Parameter {

		int index();

		String name();

		TypeDescription type();

	}

	private static class ParameterWrapper implements Parameter {

		private final int index;
		private final InDefinedShape definedShape;

		public ParameterWrapper(int index, InDefinedShape definedShape) {
			this.index = index;
			this.definedShape = definedShape;
		}

		@Override
		public int index() {
			return index;
		}

		@Override
		public String name() {
			return definedShape.getName();
		}

		@Override
		public TypeDescription type() {
			return definedShape.getType().asErasure();
		}

	}

	private Parameters(ParameterList<InDefinedShape> parameterList) {
		values = range(0, parameterList.size()).mapToObj(i -> new ParameterWrapper(i, parameterList.get(i)))
				.collect(toList());
	}

	public Parameter param(int index) {
		return values.get(index);
	}

	public List<TypeDescription> types() {
		return values.stream().map(Parameter::type).toList();
	}

	@Override
	public Iterator<Parameter> iterator() {
		return values.iterator();
	}

}
