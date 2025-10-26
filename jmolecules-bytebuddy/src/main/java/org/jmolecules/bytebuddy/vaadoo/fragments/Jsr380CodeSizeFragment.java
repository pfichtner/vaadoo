package org.jmolecules.bytebuddy.vaadoo.fragments;

import java.util.Collection;
import java.util.Map;

import jakarta.validation.constraints.NotEmpty;

public interface Jsr380CodeSizeFragment {

	void check(NotEmpty anno, Collection<?> collection);

	void check(NotEmpty anno, Map<?, ?> map);

	void check(NotEmpty anno, Object[] objects);

}
