package com.github.pfichtner.vaadoo;

import java.util.Map;
import net.bytebuddy.description.type.TypeDescription;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(exclude = "fieldName")
public class CustomValidatorInfo {
	TypeDescription validatorClass;
	TypeDescription annotationType;
	Map<String, Object> annotationValues;
	String fieldName;
}
