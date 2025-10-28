package org.jmolecules.bytebuddy.testclasses;

import org.jmolecules.ddd.annotation.ValueObject;

import jakarta.validation.constraints.NotEmpty;

@SuppressWarnings("unused")
@ValueObject
public class AnnotationDoesNotSupportType {

	private Integer someInteger;

	public AnnotationDoesNotSupportType(@NotEmpty Integer someInteger) {
		this.someInteger = someInteger;
	}

}