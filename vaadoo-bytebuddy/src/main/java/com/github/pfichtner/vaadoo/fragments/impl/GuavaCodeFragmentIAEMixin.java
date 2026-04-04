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
@SuppressWarnings("null")
public abstract class GuavaCodeFragmentIAEMixin implements Jsr380CodeFragment {

	@Override
	public void check(Null anno, Object ref, Object[] args) {
		checkArgument(ref == null, anno.message(), args);
	}

	@Override
	public void check(NotNull anno, Object ref, Object[] args) {
		checkArgument(ref != null, anno.message(), args);
	}

	@Override
	public void check(NotBlank anno, CharSequence charSequence, Object[] args) {
		checkArgument(charSequence != null, anno.message(), args);
		checkArgument(charSequence.toString().trim().length() > 0, anno.message(), args);
	}

	// -----------------------------------------------------------------

	@Override
	public void check(NotEmpty anno, CharSequence charSequence, Object[] args) {
		checkArgument(charSequence != null, anno.message(), args);
		checkArgument(charSequence.length() > 0, anno.message(), args);
	}

	public void check(NotEmpty anno, Collection<?> collection, Object[] args) {
		checkArgument(collection != null, anno.message(), args);
		checkArgument(collection.size() > 0, anno.message(), args);
	}

	@Override
	public void check(NotEmpty anno, Map<?, ?> map, Object[] args) {
		checkArgument(map != null, anno.message(), args);
		checkArgument(map.size() > 0, anno.message(), args);
	}

	@Override
	public void check(NotEmpty anno, Object[] objects, Object[] args) {
		checkArgument(objects != null, anno.message(), args);
		checkArgument(objects.length > 0, anno.message(), args);
	}

	// -----------------------------------------------------------------

	@Override
	public void check(Size anno, CharSequence charSequence, Object[] args) {
		checkArgument(charSequence != null, anno.message(), args);
		int length = charSequence.length();
		checkArgument(length >= anno.min() && length <= anno.max(), anno.message(), args);
	}

	public void check(Size anno, Collection<?> collection, Object[] args) {
		checkArgument(collection != null, anno.message(), args);
		int length = collection.size();
		checkArgument(length >= anno.min() && length <= anno.max(), anno.message(), args);
	}

	@Override
	public void check(Size anno, Map<?, ?> map, Object[] args) {
		checkArgument(map != null, anno.message(), args);
		int length = map.size();
		checkArgument(length >= anno.min() && length <= anno.max(), anno.message(), args);
	}

	@Override
	public void check(Size anno, Object[] objects, Object[] args) {
		checkArgument(objects != null, anno.message(), args);
		int length = objects.length;
		checkArgument(length >= anno.min() && length <= anno.max(), anno.message(), args);
	}

}
