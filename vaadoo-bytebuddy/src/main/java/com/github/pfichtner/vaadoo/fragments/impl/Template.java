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
package com.github.pfichtner.vaadoo.fragments.impl;

import static lombok.AccessLevel.PRIVATE;

import java.util.stream.Stream;

import jakarta.validation.constraints.Pattern.Flag;
import lombok.NoArgsConstructor;

/**
 * This is a helper method for the template classes. Methods called by the
 * template methods are not used after transformation but the bytecode contains
 * precalculated values.
 */
@NoArgsConstructor(access = PRIVATE)
public final class Template {

	public static int bitwiseOr(Flag[] flags) {
		return Stream.of(flags).mapToInt(Flag::getValue).reduce(0, (l, r) -> l | r);
	}

}
