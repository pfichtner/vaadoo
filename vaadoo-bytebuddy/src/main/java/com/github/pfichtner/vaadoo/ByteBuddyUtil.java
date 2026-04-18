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
package com.github.pfichtner.vaadoo;

import static lombok.AccessLevel.PRIVATE;

import java.util.Collection;
import java.util.stream.Stream;

import lombok.NoArgsConstructor;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.jar.asm.Type;

@NoArgsConstructor(access = PRIVATE)
public class ByteBuddyUtil {

	public static int sizeOf(Collection<TypeDescription> types) {
		return sizeOf(types.stream());
	}

	public static int sizeOf(Stream<TypeDescription> types) {
		return AsmUtil.sizeOf(toTypes(types));
	}

	public static Stream<Type> toTypes(Collection<TypeDescription> types) {
		return toTypes(types.stream());
	}

	public static Stream<Type> toTypes(Stream<TypeDescription> types) {
		return types //
				.map(TypeDescription::asErasure) //
				.map(TypeDescription::getDescriptor) //
				.map(Type::getType);
	}

}
