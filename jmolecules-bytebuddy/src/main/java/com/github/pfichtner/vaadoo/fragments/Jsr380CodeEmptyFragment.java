package com.github.pfichtner.vaadoo.fragments;

import java.util.Collection;
import java.util.Map;

import jakarta.validation.constraints.Size;

public interface Jsr380CodeEmptyFragment {

	void check(Size anno, CharSequence collection);

	void check(Size anno, Collection<?> collection);

	void check(Size anno, Map<?, ?> map);

	void check(Size anno, Object[] objects);

}
