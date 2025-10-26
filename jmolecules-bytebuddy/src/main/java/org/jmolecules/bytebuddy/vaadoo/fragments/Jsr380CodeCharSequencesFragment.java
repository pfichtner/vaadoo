package org.jmolecules.bytebuddy.vaadoo.fragments;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

public interface Jsr380CodeCharSequencesFragment {

	void check(NotBlank anno, CharSequence charSequence);

	void check(NotEmpty anno, CharSequence charSequence);

	void check(Pattern anno, CharSequence charSequence);

	void check(Email anno, CharSequence charSequence);

}
