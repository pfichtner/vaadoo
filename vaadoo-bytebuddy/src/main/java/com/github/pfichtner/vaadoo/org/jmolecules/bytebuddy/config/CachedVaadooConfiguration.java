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

import java.util.List;

import com.github.pfichtner.vaadoo.fragments.Jsr380CodeFragment;

import lombok.Value;
import lombok.experimental.Accessors;

@Value
@Accessors(fluent = true)
public class CachedVaadooConfiguration implements VaadooConfiguration {

	boolean customAnnotationsEnabled;
	boolean regexOptimizationEnabled;
	boolean removeJsr380Annotations;
	KnownFragmentClass jsrFragmentType;
	Class<? extends Jsr380CodeFragment> jsr380CodeFragmentClass;
	Class<? extends RuntimeException> nullValueExceptionType;
	String nullValueExceptionTypeInternalName;
	List<Class<? extends Jsr380CodeFragment>> codeFragmentMixins;

	public CachedVaadooConfiguration(VaadooConfiguration delegate) {
		this.customAnnotationsEnabled = delegate.customAnnotationsEnabled();
		this.regexOptimizationEnabled = delegate.regexOptimizationEnabled();
		this.removeJsr380Annotations = delegate.removeJsr380Annotations();
		this.jsrFragmentType = delegate.jsrFragmentType();
		this.jsr380CodeFragmentClass = delegate.jsr380CodeFragmentClass();
		this.nullValueExceptionType = delegate.nullValueExceptionType();
		this.nullValueExceptionTypeInternalName = delegate.nullValueExceptionTypeInternalName();
		this.codeFragmentMixins = delegate.codeFragmentMixins();
	}

}
