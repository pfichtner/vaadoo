package org.jmolecules.bytebuddy.vaadoo.fragments;

import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.AssertTrue;

public interface Jsr380CodeBooleanFragment {

	void check(AssertTrue anno, boolean value);

	void check(AssertTrue anno, Boolean value);

	void check(AssertFalse anno, boolean value);

	void check(AssertFalse anno, Boolean value);

}
