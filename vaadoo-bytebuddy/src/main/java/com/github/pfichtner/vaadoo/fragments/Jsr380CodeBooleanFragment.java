package com.github.pfichtner.vaadoo.fragments;

import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.AssertTrue;

public interface Jsr380CodeBooleanFragment {

	void check(AssertTrue anno, boolean value, Object... args);

	void check(AssertTrue anno, Boolean value, Object... args);

	void check(AssertFalse anno, boolean value, Object... args);

	void check(AssertFalse anno, Boolean value, Object... args);

}
