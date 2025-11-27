package com.github.pfichtner.vaadoo.fragments;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;

public interface Jsr380CodeObjectsFragment {

	void check(Null anno, Object ref);

	void check(NotNull anno, Object ref);

}
