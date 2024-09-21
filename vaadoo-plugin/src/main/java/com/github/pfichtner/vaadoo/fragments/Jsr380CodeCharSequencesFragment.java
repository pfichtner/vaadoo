package com.github.pfichtner.vaadoo.fragments;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

public interface Jsr380CodeCharSequencesFragment {

	void check(NotBlank notBlank, CharSequence charSequence);

	void check(NotEmpty notEmpty, CharSequence charSequence);

	void check(Pattern pattern, CharSequence charSequence);

}