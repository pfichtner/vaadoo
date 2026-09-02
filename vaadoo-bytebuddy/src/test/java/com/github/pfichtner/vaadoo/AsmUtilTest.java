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

import static com.github.pfichtner.vaadoo.AsmUtil.isArrayHandlingOpcode;
import static com.github.pfichtner.vaadoo.AsmUtil.sizeOf;
import static net.bytebuddy.jar.asm.Opcodes.AALOAD;
import static net.bytebuddy.jar.asm.Opcodes.AASTORE;
import static net.bytebuddy.jar.asm.Opcodes.ALOAD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.jar.asm.Type;

class AsmUtilTest {

	private static final class StubClassLoader extends ClassLoader {

		private final InputStream stream;

		StubClassLoader(InputStream stream) {
			this.stream = stream;
		}

		@Override
		public InputStream getResourceAsStream(String name) {
			return stream;
		}

		Class<?> define(byte[] bytes, String name) {
			return defineClass(name, bytes, 0, bytes.length);
		}

	}

	@Test
	void testSizeOf() {
		assertThat(sizeOf(new Type[] { //
				Type.LONG_TYPE, Type.LONG_TYPE, Type.getType(CharSequence.class) //
		})).isEqualTo(5);
	}

	@Test
	void isArrayHandlingOpcodeRecognizesArrayLoadAndStore() {
		assertThat(isArrayHandlingOpcode(AALOAD)).isTrue();
		assertThat(isArrayHandlingOpcode(AASTORE)).isTrue();
		assertThat(isArrayHandlingOpcode(ALOAD)).isFalse();
	}

	@Test
	void classReaderReadsClassBytesFromClassResource() {
		assertThat(AsmUtil.classReader(String.class)).isNotNull();
	}

	@Nested
	class ClassReaderResourceHandling {

		private byte[] classBytes(String internalName) {
			ClassWriter classWriter = new ClassWriter(0);
			classWriter.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);
			classWriter.visitEnd();
			return classWriter.toByteArray();
		}

		@Test
		void fallsBackToSystemResourceWhenOwnLookupReturnsNull() throws Exception {
			// A real class from the classpath, defined in a loader whose resource
			// lookup returns null so classReader has to fall back to the system classloader.
			byte[] bytes = readClassByName("com/github/pfichtner/vaadoo/AsmUtilTest$Dummy.class");
			Class<?> clazz = new StubClassLoader(null).define(bytes,
					"com.github.pfichtner.vaadoo.AsmUtilTest$Dummy");
			assertThat(AsmUtil.classReader(clazz)).isNotNull();
		}

		@Test
		void throwsWhenNoResourceCanBeFound() {
			// A class whose name is not present anywhere, with a loader that returns
			// null, so both the own and the system lookup fail.
			Class<?> clazz = new StubClassLoader(null).define(classBytes("com/example/nonexistent/Unfindable"),
					"com.example.nonexistent.Unfindable");
			assertThatThrownBy(() -> AsmUtil.classReader(clazz)) //
					.isInstanceOf(IllegalStateException.class) //
					.hasMessageContaining("Could not find class resource");
		}

		@Test
		void throwsWhenClassBytesCannotBeRead() {
			// The resource is found, but reading its bytes fails, so the ClassReader
			// constructor raises an IOException that classReader wraps.
			InputStream unreadable = new InputStream() {
				@Override
				public int read() throws IOException {
					throw new IOException("boom");
				}
			};
			Class<?> clazz = new StubClassLoader(unreadable).define(classBytes("com/example/invalid/Invalid"),
					"com.example.invalid.Invalid");
			assertThatThrownBy(() -> AsmUtil.classReader(clazz)) //
					.isInstanceOf(IllegalStateException.class) //
					.hasMessageContaining("Failed to read class bytes");
		}

		private byte[] readClassByName(String resource) throws IOException {
			try (InputStream in = AsmUtilTest.class.getClassLoader().getResourceAsStream(resource)) {
				if (in == null) {
					throw new IOException("Missing test resource " + resource);
				}
				return in.readAllBytes();
			}
		}

	}

	/** Nested type only used as a resource for the classReader fallback test. */
	@SuppressWarnings("unused")
	private static final class Dummy {
		// empty
	}

}
