package com.github.pfichtner.vaadoo.fragments.impl;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Collection;
import java.util.Map;

import com.github.pfichtner.vaadoo.fragments.Jsr380CodeFragment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Size;

/**
 * Can be used to override methods that calls Preconditions#checkNull, so null
 * checks will throw {@link IllegalArgumentException}s instead of
 * {@link NullPointerException}s.
 */
public abstract class GuavaCodeFragmentIAEMixin implements Jsr380CodeFragment {

	@Override
	public void check(Null anno, Object ref) {
		checkArgument(ref == null, anno.message());
	}

	@Override
	public void check(NotNull anno, Object ref) {
		checkArgument(ref != null, anno.message());
	}

	@Override
	public void check(NotBlank anno, CharSequence charSequence) {
		checkArgument(charSequence != null, anno.message());
		checkArgument(charSequence.toString().trim().length() > 0, anno.message());
	}

	// -----------------------------------------------------------------

	@Override
	public void check(NotEmpty anno, CharSequence charSequence) {
		checkArgument(charSequence != null, anno.message());
		checkArgument(charSequence.length() > 0, anno.message());
	}

	public void check(NotEmpty anno, Collection<?> collection) {
		checkArgument(collection != null, anno.message());
		checkArgument(collection.size() > 0, anno.message());
	}

	@Override
	public void check(NotEmpty anno, Map<?, ?> map) {
		checkArgument(map != null, anno.message());
		checkArgument(map.size() > 0, anno.message());
	}

	@Override
	public void check(NotEmpty anno, Object[] objects) {
		checkArgument(objects != null, anno.message());
		checkArgument(objects.length > 0, anno.message());
	}

	// -----------------------------------------------------------------

	@Override
	public void check(Size anno, CharSequence charSequence) {
		checkArgument(charSequence != null, anno.message());
		int length = charSequence.length();
		checkArgument(length >= anno.min() && length <= anno.max(), anno.message());
	}

	public void check(Size anno, Collection<?> collection) {
		checkArgument(collection != null, anno.message());
		int length = collection.size();
		checkArgument(length >= anno.min() && length <= anno.max(), anno.message());
	}

	@Override
	public void check(Size anno, Map<?, ?> map) {
		checkArgument(map != null, anno.message());
		int length = map.size();
		checkArgument(length >= anno.min() && length <= anno.max(), anno.message());
	}

	@Override
	public void check(Size anno, Object[] objects) {
		checkArgument(objects != null, anno.message());
		int length = objects.length;
		checkArgument(length >= anno.min() && length <= anno.max(), anno.message());
	}

}
