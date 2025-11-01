package org.jmolecules.bytebuddy.vaadoo;

import static net.bytebuddy.jar.asm.Opcodes.AALOAD;
import static net.bytebuddy.jar.asm.Opcodes.ALOAD;
import static net.bytebuddy.jar.asm.Opcodes.ARETURN;
import static net.bytebuddy.jar.asm.Opcodes.ASTORE;
import static net.bytebuddy.jar.asm.Opcodes.DLOAD;
import static net.bytebuddy.jar.asm.Opcodes.DRETURN;
import static net.bytebuddy.jar.asm.Opcodes.DSTORE;
import static net.bytebuddy.jar.asm.Opcodes.FLOAD;
import static net.bytebuddy.jar.asm.Opcodes.FRETURN;
import static net.bytebuddy.jar.asm.Opcodes.FSTORE;
import static net.bytebuddy.jar.asm.Opcodes.ILOAD;
import static net.bytebuddy.jar.asm.Opcodes.IRETURN;
import static net.bytebuddy.jar.asm.Opcodes.ISTORE;
import static net.bytebuddy.jar.asm.Opcodes.LLOAD;
import static net.bytebuddy.jar.asm.Opcodes.LRETURN;
import static net.bytebuddy.jar.asm.Opcodes.LSTORE;
import static net.bytebuddy.jar.asm.Opcodes.RETURN;
import static net.bytebuddy.jar.asm.Type.ARRAY;

import java.io.IOException;
import java.io.InputStream;
import java.util.stream.Stream;

import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.Type;

public final class AsmUtil {

	public static final Type STRING_TYPE = Type.getType(String.class);

	private AsmUtil() {
		super();
	}

	public static boolean isLoadOpcode(int opcode) {
		return opcode == ALOAD || opcode == ILOAD || opcode == LLOAD //
				|| opcode == FLOAD || opcode == DLOAD;
	}

	public static boolean isStoreOpcode(int opcode) {
		return opcode == ASTORE || opcode == ISTORE || opcode == LSTORE //
				|| opcode == FSTORE || opcode == DSTORE;
	}

	public static boolean isReturnOpcode(int opcode) {
		return opcode == RETURN || opcode == ARETURN || opcode == IRETURN //
				|| opcode == LRETURN || opcode == FRETURN || opcode == DRETURN;
	}

	public static boolean isArrayHandlingOpcode(int opcode) {
		return opcode == AALOAD || opcode == ASTORE;
	}

	public static boolean isArray(Type type) {
		return type.getSort() == ARRAY;
	}

	public static int sizeOf(Type[] types) {
		return sizeOf(Stream.of(types));
	}

	public static int sizeOf(Stream<Type> types) {
		return types.mapToInt(Type::getSize).sum();
	}

	public static ClassReader classReader(Class<?> clazz) {
		String resource = clazz.getName().replace('.', '/') + ".class";
		InputStream inputStream = clazz.getResourceAsStream("/" + resource);
		if (inputStream == null) {
			inputStream = ClassLoader.getSystemResourceAsStream(resource);
		}
		if (inputStream == null) {
			throw new IllegalStateException("Could not find class resource for " + clazz.getName());
		}
		try {
			return new ClassReader(inputStream);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to read class bytes for " + clazz.getName(), e);
		}
	}

}
