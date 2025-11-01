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

/**
 * Represents an ordered list of method or constructor parameters.
 * <p>
 * This class provides access to individual {@link Parameter} instances,
 * allowing inspection of their type, name, annotations, and stack offset. It
 * implements {@link Iterable}, so parameters can be iterated in declaration
 * order.
 */
public class Parameters implements Iterable<Parameter> {

	public static Parameters of(ParameterList<InDefinedShape> parameterList) {
		return new Parameters(parameterList);
	}

	private final List<Parameter> values;
	private final ParameterList<InDefinedShape> parameterList;

	/**
	 * Represents a single parameter.
	 */
	public static interface Parameter {

		/**
		 * Returns the position of this parameter within the declaring method's
		 * parameter list.
		 *
		 * @return the zero-based index of this parameter
		 */
		int index();

		/**
		 * Returns the name of this parameter.
		 *
		 * @return the parameter's name, or {@code null} if the name is not available
		 */
		String name();

		/**
		 * Returns the type of this parameter.
		 *
		 * @return a {@link TypeDescription} representing the parameter's type
		 */
		TypeDescription type();

		/**
		 * Computes the stack offset of this parameter within its declaring method.
		 * <p>
		 * The offset is determined by summing the stack sizes of all parameters
		 * declared before this one. If this parameter is the first (index {@code 0}),
		 * its offset is {@code 0}.
		 *
		 * @return The total stack offset of this parameter relative to the start of the
		 *         parameter list.
		 */
		int offset();

		/**
		 * Returns all annotations present on this parameter.
		 *
		 * @return an array of {@link TypeDescription} representing the parameter's
		 *         annotations, or an empty array if no annotations are present
		 */
		TypeDescription[] annotations();

		/**
		 * Retrieves the value of a specified attribute from the given annotation type.
		 * <p>
		 * This method returns the value associated with the attribute identified by
		 * {@code name} from the provided {@code annotation}. If the attribute does not
		 * exist or cannot be accessed, the behavior is implementation-dependent.
		 *
		 * @param annotation the annotation type from which to retrieve the attribute
		 *                   value
		 * @param name       the name of the attribute whose value should be returned
		 * @return the value of the specified annotation attribute, or {@code null} if
		 *         unavailable
		 */
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

	public Parameter parameter(int index) {
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
