package com.github.pfichtner.vaadoo.fragments;

import java.util.Collection;
import java.util.Map;

import jakarta.validation.constraints.NotEmpty;

public interface Jsr380CodeSizeFragment {

	void check(NotEmpty anno, Collection<?> collection, Object[] args);

	void check(NotEmpty anno, Map<?, ?> map, Object[] args);

	void check(NotEmpty anno, Object[] objects, Object[] args);

}
