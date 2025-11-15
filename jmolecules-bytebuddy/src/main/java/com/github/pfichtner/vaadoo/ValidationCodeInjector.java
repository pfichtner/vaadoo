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

import static com.github.pfichtner.vaadoo.AsmUtil.STRING_TYPE;
import static com.github.pfichtner.vaadoo.AsmUtil.classReader;
import static com.github.pfichtner.vaadoo.AsmUtil.isArray;
import static com.github.pfichtner.vaadoo.AsmUtil.isLoadOpcode;
import static com.github.pfichtner.vaadoo.AsmUtil.isReturnOpcode;
import static com.github.pfichtner.vaadoo.AsmUtil.isStoreOpcode;
import static com.github.pfichtner.vaadoo.AsmUtil.sizeOf;
import static java.lang.String.format;
import static java.lang.reflect.Modifier.isStatic;
import static java.util.Arrays.stream;
import static java.util.Collections.emptyList;
import static net.bytebuddy.jar.asm.Opcodes.AASTORE;
import static net.bytebuddy.jar.asm.Opcodes.ANEWARRAY;
import static net.bytebuddy.jar.asm.Opcodes.ASM9;
import static net.bytebuddy.jar.asm.Opcodes.BIPUSH;
import static net.bytebuddy.jar.asm.Opcodes.DUP;
import static net.bytebuddy.jar.asm.Opcodes.GETSTATIC;
import static net.bytebuddy.jar.asm.Opcodes.SIPUSH;
import static net.bytebuddy.jar.asm.Type.BOOLEAN_TYPE;
import static net.bytebuddy.jar.asm.Type.INT_TYPE;
import static net.bytebuddy.jar.asm.Type.LONG_TYPE;
import static net.bytebuddy.jar.asm.Type.getArgumentTypes;
import static net.bytebuddy.jar.asm.Type.getMethodDescriptor;
import static net.bytebuddy.jar.asm.Type.getObjectType;
import static net.bytebuddy.jar.asm.Type.getReturnType;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import com.github.pfichtner.vaadoo.Parameters.Parameter;
import com.github.pfichtner.vaadoo.fragments.Jsr380CodeFragment;

import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;
import net.bytebuddy.description.enumeration.EnumerationDescription;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.Handle;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Type;

public class ValidationCodeInjector {

	// we remove the first arg (the code inserted has the annotation as it's first
	// argument)
	private static final int REMOVED_PARAMETERS = 1;
	private static final boolean TARGET_METHOD_IS_STATIC = true;

	@ToString
	static final class ValidationCallCodeInjectorClassVisitor extends ClassVisitor {

		private final String sourceMethodOwner;
		private final String sourceMethodName;
		private final String searchDescriptor;
		private final MethodVisitor targetMethodVisitor;
		private final Parameter targetParam;

		private final SlotInfo srcSlot;
		private final SlotInfo tgtSlot;
		private final SlotInfo offset;

		@Value
		@RequiredArgsConstructor
		@Accessors(fluent = true)
		static class SlotInfo {
			int firstArg;
			int firstLocal;

			public SlotInfo(boolean isStatic, Type[] args) {
				this.firstArg = isStatic ? 0 : 1;
				this.firstLocal = firstArg + sizeOf(args);
			}

			public SlotInfo offsetTo(SlotInfo other) {
				return new SlotInfo(firstArg - other.firstArg, firstLocal - other.firstLocal);
			}

			public boolean isVariable(int index) {
				return index >= firstLocal;
			}

		}

		private ValidationCallCodeInjectorClassVisitor(Method sourceMethod, MethodVisitor targetMethodVisitor,
				String signatureOfTargetMethod, Parameter parameter) {
			super(ASM9);
			this.sourceMethodOwner = Type.getType(sourceMethod.getDeclaringClass()).getInternalName();
			this.sourceMethodName = sourceMethod.getName();
			this.searchDescriptor = getMethodDescriptor(sourceMethod);
			this.targetMethodVisitor = targetMethodVisitor;
			this.targetParam = parameter;
			this.srcSlot = new SlotInfo(isStatic(sourceMethod.getModifiers()), argTypes(sourceMethod));
			this.tgtSlot = new SlotInfo(TARGET_METHOD_IS_STATIC, getArgumentTypes(signatureOfTargetMethod));
			this.offset = srcSlot.offsetTo(tgtSlot);
		}

		private static Type[] argTypes(Method method) {
			return stream(method.getParameterTypes()).map(Type::getType).toArray(Type[]::new);
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
				String[] exceptions) {

			if (name.equals(sourceMethodName) && descriptor.equals(searchDescriptor)) {
				// TODO migrate to LocalVariablesSorter
				return new MethodVisitor(ASM9, targetMethodVisitor) {

					private final boolean isStatic = isStatic(access);

					private boolean isFirstParamLoad;
					private Type currentAnnotationType;

					private final Function<String, String> rbResolver = Resources::message;
					private final Function<String, String> paramNameResolver = k -> k.equals(NAME) ? targetParam.name()
							: k;
					private final Function<String, Object> annotationValueResolver = k -> {
						Object annotationValue = targetParam.annotationValue(currentAnnotationType, k);
						return annotationValue == null ? k : annotationValue;
					};
					final Function<String, Object> resolver = rbResolver.andThen(paramNameResolver)
							.andThen(annotationValueResolver);

					@Override
					public void visitLineNumber(int line, Label start) {
						// ignore
					}

					public void visitLocalVariable(String name, String descriptor, String signature, Label start,
							Label end, int index) {
						// ignore, we would have to rewrite owner
					}

					@Override
					public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
						if (!owner.startsWith("java/lang")) {
							throw new IllegalStateException(format(
									"code that gets inserted must not access fields, found access to %s#%s in %s",
									owner, name, sourceMethodOwner));
						}
					}

					@Override
					public void visitMaxs(int maxStack, int maxLocals) {
						// ignore
					}

					@Override
					public void visitVarInsn(int opcode, int var) {
						assert var > 0 : "this not expected to get accesed";
						boolean opcodeIsLoad = isLoadOpcode(opcode);
						boolean opcodeIsStore = isStoreOpcode(opcode);

						if (opcodeIsLoad || opcodeIsStore) {
							if (srcSlot.isVariable(var)) {
								var = remapLocal(var);
							} else {
								// argument access
								if (opcodeIsLoad && var == srcSlot.firstArg()) {
									isFirstParamLoad = true;
									return;
								}
								var = remapArg(var);
							}
						}
						super.visitVarInsn(opcode, var);
					}

					private int remapArg(int varIndex) {
						return varIndex - offset.firstArg() - REMOVED_PARAMETERS + targetParam.offset();
					}

					private int remapLocal(int varIndex) {
						return varIndex - offset.firstLocal();
					}

					@Override
					public void visitIincInsn(int varIndex, int increment) {
						super.visitIincInsn(remapLocal(varIndex), increment);
					}

					public void visitMethodInsn(int opcode, String owner, String name, String descriptor,
							boolean isInterface) {
						if (owner.equals(sourceMethodOwner)) {
							throw new IllegalStateException(format(
									"code that gets inserted must not access methods in the class inserted, found access to %s#%s in %s",
									owner, name, sourceMethodOwner));
						}

						currentAnnotationType = getObjectType(owner);
						if (isFirstParamLoad) {
							Type returnType = getReturnType(descriptor);
							if (isArray(returnType)) {
								EnumerationDescription[] annotationValues = (EnumerationDescription[]) targetParam
										.annotationValue(currentAnnotationType, name);
								writeArray(returnType.getElementType(),
										annotationValues == null ? emptyList() : Arrays.asList(annotationValues));
							} else {
								visitLdcInsn(annotationsLdcInsnValue(targetParam, owner, name, returnType));
							}

							isFirstParamLoad = false;
						} else {
							super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
						}
					}

					private void writeArray(Type arrayElementType, List<EnumerationDescription> annotationValues) {
						int intInsn = annotationValues.size() <= 127 ? BIPUSH : SIPUSH;
						mv.visitIntInsn(intInsn, annotationValues.size());
						// TODO this only works for objects but not primitive arrays
						mv.visitTypeInsn(ANEWARRAY, arrayElementType.getInternalName());

						int idx = 0;
						for (EnumerationDescription entry : annotationValues) {
							mv.visitInsn(DUP);
							mv.visitIntInsn(intInsn, idx++);
							mv.visitFieldInsn(GETSTATIC, entry.getEnumerationType().getInternalName(), entry.getValue(),
									entry.getEnumerationType().getDescriptor());
							mv.visitInsn(AASTORE);
						}
					}

					private Object annotationsLdcInsnValue(Parameter parameter, String owner, String name,
							Type returnType) {
						String stringValue = String.valueOf(valueFromClass(parameter, owner, name));
						if (STRING_TYPE.equals(returnType)) {
							return stringValue;
						} else if (LONG_TYPE.equals(returnType)) {
							return Long.valueOf(stringValue);
						} else if (INT_TYPE.equals(returnType)) {
							return Integer.valueOf(stringValue);
						} else if (BOOLEAN_TYPE.equals(returnType)) {
							return Boolean.valueOf(stringValue);
						}
						throw new IllegalStateException(format("Unsupported type %s", returnType));
					}

					private Object valueFromClass(Parameter parameter, String owner, String name) {
						Object valueFromClass = parameter.annotationValue(currentAnnotationType, name);
						if (valueFromClass != null) {
							return valueFromClass;
						}
						Object defaultValue = defaultValue(currentAnnotationType.getClassName(), name);
						if (defaultValue != null) {
							return defaultValue;
						}
						throw new IllegalStateException(format("'%s' does not define attribute '%s'", owner, name));
					}

                    @Override
					public void visitInsn(int opcode) {
						if (!isReturnOpcode(opcode)) {
							super.visitInsn(opcode);
						}
					}

					@Override
					public void visitLdcInsn(Object value) {
						super.visitLdcInsn(value instanceof String //
								? NamedPlaceholders.replace((String) value, resolver) //
								: value);
					}

					public void visitInvokeDynamicInsn(String name, String descriptor, Handle handle, Object... args) {
						if ("makeConcatWithConstants".equals(name) && "(J)Ljava/lang/String;".equals(descriptor)
								&& "makeConcatWithConstants".equals(handle.getName())
								&& "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;"
										.equals(handle.getDesc())
								&& "java/lang/invoke/StringConcatFactory".equals(handle.getOwner()) && args.length >= 0
								&& args[0] instanceof String) {
							args[0] = format((String) args[0], targetParam.name());
						}
						super.visitInvokeDynamicInsn(name, descriptor, handle, args);
					}

                };
			}
			return super.visitMethod(access, name, descriptor, signature, exceptions);
		}
	}

	private final ClassReader classReader;

	public static final String NAME = "@@@NAME@@@";
	private final String signatureOfTargetMethod;

	public ValidationCodeInjector(Class<? extends Jsr380CodeFragment> clazz, String signatureOfTargetMethod) {
		this.classReader = classReader(clazz);
		this.signatureOfTargetMethod = signatureOfTargetMethod;
	}

	public void inject(MethodVisitor mv, Parameter parameter, Method sourceMethod) {
		ClassVisitor classVisitor = new ValidationCallCodeInjectorClassVisitor(sourceMethod, mv,
				signatureOfTargetMethod, parameter);
		classReader.accept(classVisitor, 0);
	}

	private static String defaultValue(String className, String name) {
		return defaultValue(loadClass(className), name);
	}

	private static Class<?> loadClass(String className) {
		try {
			return Class.forName(className);
		} catch (ClassNotFoundException e) {
			throw new IllegalStateException(e);
		}
	}

	private static String defaultValue(Class<?> clazz, String name) {
		return stream(clazz.getMethods()) //
				.filter(m -> name.equals(m.getName())) //
				.findFirst() //
				.map(Method::getDefaultValue) //
				.map(String::valueOf) //
				.orElse(null);
	}

}
