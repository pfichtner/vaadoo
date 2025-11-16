/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy.config;

import com.github.pfichtner.vaadoo.fragments.Jsr380CodeFragment;
import com.github.pfichtner.vaadoo.fragments.impl.GuavaCodeFragment;
import com.github.pfichtner.vaadoo.fragments.impl.JdkOnlyCodeFragment;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.bytebuddy.description.type.TypeDescription;

public interface VaadooConfiguration {

	public static final KnownFragmentClass DEFAULT_KNOWN_FRAGMENT_CLASS = KnownFragmentClass.JDK_ONLY;

	@RequiredArgsConstructor
	@Getter
	public static enum KnownFragmentClass {
		JDK_ONLY(JdkOnlyCodeFragment.class), //
		GUAVA(GuavaCodeFragment.class) //
		;

		private final Class<? extends Jsr380CodeFragment> fragmentClass;
	}

	VaadooConfiguration DEFAULT = new VaadooConfiguration() {
	};

	public default boolean include(TypeDescription description) {
		return true;
	}

	public default boolean customAnnotationsEnabled() {
		return true;
	}

	public default boolean matches(TypeDescription target) {
		return true;
	}

	public default KnownFragmentClass jsrFragmentType() {
		return DEFAULT_KNOWN_FRAGMENT_CLASS;
	}

	public default Class<? extends Jsr380CodeFragment> jsr380CodeFragmentClass() {
		KnownFragmentClass jsrFragmentType = jsrFragmentType();
		return (jsrFragmentType == null ? DEFAULT_KNOWN_FRAGMENT_CLASS : jsrFragmentType).getFragmentClass();
	}

}