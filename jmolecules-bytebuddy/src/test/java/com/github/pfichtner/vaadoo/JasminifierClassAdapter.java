package com.github.pfichtner.vaadoo;

import static java.util.Comparator.comparing;
import static org.objectweb.asm.Opcodes.ASM9;

/***
 * ASM: a very small and fast Java bytecode manipulation framework
 * Copyright (c) 2000-2011 INRIA, France Telecom
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holders nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */

import java.io.FileInputStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.util.Printer;

/**
 * A {@link ClassVisitor} that prints a disassembled view of the classes it
 * visits in Jasmin assembler format. This class visitor can be used alone (see
 * the {@link #main main} method) to disassemble a class. It can also be used in
 * the middle of class visitor chain to trace the class that is visited at a
 * given point in this chain. This may be useful for debugging purposes.
 * <p>
 * The trace printed when visiting the <tt>Hello</tt> class is the following:
 * <p>
 * <blockquote>
 * 
 * <pre>
 * .bytecode 45.3
 * .class public Hello
 * .super java/lang/Object
 * 
 * .method public <init>()V
 * aload 0
 * invokespecial java/lang/Object/<init>()V
 * return
 * .limit locals 1
 * .limit stack 1
 * .end method
 * 
 * .method public static main([Ljava/lang/String;)V
 * getstatic java/lang/System/out Ljava/io/PrintStream;
 * ldc "hello"
 * invokevirtual java/io/PrintStream/println(Ljava/lang/String;)V
 * return
 * .limit locals 2
 * .limit stack 2
 * .end method
 * </pre>
 * 
 * </blockquote> where <tt>Hello</tt> is defined by:
 * <p>
 * <blockquote>
 * 
 * <pre>
 * public class Hello {
 * 
 * 	public static void main(String[] args) {
 * 		System.out.println(&quot;hello&quot;);
 * 	}
 * }
 * </pre>
 * 
 * </blockquote>
 * 
 * @author Eric Bruneton
 */
public class JasminifierClassAdapter extends ClassVisitor {

	protected final Set<String> dependencies = new HashSet<>();
	protected final Set<String> provides = new HashSet<>();
	protected final Set<String> descriptors = new HashSet<>();

	/**
	 * The print writer to be used to print the class.
	 */
	protected PrintWriter pw;

	/**
	 * The label names. This map associate String values to Label keys.
	 */
	protected final Map<Label, String> labelNames = new HashMap<>();
	private String className;
	private boolean sortAnnotationValues;
	private boolean sortDescriptors;

	private static final Map<Integer, String> accessMapping = createAccessMap();

	/**
	 * Prints a disassembled view of the given class in Jasmin assembler format
	 * to the standard output.
	 * <p>
	 * Usage: JasminifierClassAdapter [-debug] &lt;fully qualified class name or
	 * class file name &gt;
	 * 
	 * @param args the command line arguments.
	 * 
	 * @throws Exception if the class cannot be found, or if an IO exception
	 *             occurs.
	 */
	public static void main(final String[] args) throws Exception {
		int i = 0;
		int flags = ClassReader.SKIP_DEBUG;

		boolean ok = true;
		if (ok && "-debug".equals(args[0])) {
			i = 1;
			flags = 0;
		}
		if (!ok) {
			System.err
					.println("Prints a disassembled view of the given class.");
			System.err.println("Usage: JasminifierClassAdapter [-debug] "
					+ "<fully qualified class name or class file name>");
			return;
		}
		ClassReader cr;
		for (; i < args.length; ++i) {
			if (args[i].endsWith(".class") || args[i].indexOf('\\') > -1
					|| args[i].indexOf('/') > -1) {
				cr = new ClassReader(new FileInputStream(args[i]));
			} else {
				cr = new ClassReader(args[i]);
			}
			cr.accept(new JasminifierClassAdapter(new PrintWriter(System.out,
					true), null), flags | ClassReader.EXPAND_FRAMES);

		}
	}

	public JasminifierClassAdapter deterministic() {
		return setSortAnnotationValues(true).setSortDescriptors(true);
	}

	public JasminifierClassAdapter setSortAnnotationValues(boolean sortAnnotationValues) {
		this.sortAnnotationValues = sortAnnotationValues;
		return this;
	}

	public JasminifierClassAdapter setSortDescriptors(boolean sortDescriptors) {
		this.sortDescriptors = sortDescriptors;
		return this;
	}

	private static Map<Integer, String> createAccessMap() {
		Map<Integer, String> map = new LinkedHashMap<>();
		map.put(Opcodes.ACC_PUBLIC, "public");
		map.put(Opcodes.ACC_PRIVATE, "private");
		map.put(Opcodes.ACC_PROTECTED, "protected");
		map.put(Opcodes.ACC_STATIC, "static");
		map.put(Opcodes.ACC_FINAL, "final");
		map.put(Opcodes.ACC_SYNCHRONIZED, "synchronized");
		map.put(Opcodes.ACC_VOLATILE, "volatile");
		map.put(Opcodes.ACC_TRANSIENT, "transient");
		map.put(Opcodes.ACC_NATIVE, "native");
		map.put(Opcodes.ACC_ABSTRACT, "abstract");
		map.put(Opcodes.ACC_STRICT, "fpstrict");
		map.put(Opcodes.ACC_SYNTHETIC, "synthetic");
		map.put(Opcodes.ACC_INTERFACE, "interface");
		map.put(Opcodes.ACC_ANNOTATION, "annotation");
		map.put(Opcodes.ACC_ENUM, "enum");
		return map;
	}

	/**
	 * Constructs a new {@link JasminifierClassAdapter}.
	 * 
	 * @param pw the print writer to be used to print the class.
	 * @param cv the {@link ClassVisitor} to which this visitor delegates calls.
	 *            May be <tt>null</tt>.
	 */
	public JasminifierClassAdapter(final PrintWriter pw, final ClassVisitor cv) {
		super(ASM9, new ClassNode(ASM9) {
			@Override
			public void visitEnd() {
				if (this.cv != null) {
					accept(this.cv);
				}
			}
		});
		this.pw = pw;
	}

	@Override
	public void visitEnd() {
		ClassNode cn = (ClassNode) this.cv;
		this.pw.print(".bytecode ");
		this.pw.print(cn.version & 0xFFFF);
		this.pw.print('.');
		this.pw.println(cn.version >>> 16);
		println(".source ", cn.sourceFile);
		this.pw.print(".class");
		this.pw.print(access(cn.access));
		this.pw.print(' ');
		this.className = cn.name;
		this.pw.println(cn.name);
		println(".super ", cn.superName == null ? "java/lang/Object" : cn.superName);
		for (int i = 0; i < cn.interfaces.size(); ++i) {
			println(".implements ", (String) cn.interfaces.get(i));
		}
		if (cn.signature != null)
			println(".signature ", '"' + cn.signature + '"');
		if (cn.outerClass != null) {
			this.pw.print(".enclosing method ");
			this.pw.print(cn.outerClass);
			if (cn.outerMethod != null) {
				this.pw.print('/');
				this.pw.print(cn.outerMethod);
				this.pw.println(cn.outerMethodDesc);
			} else {
				this.pw.println();
			}
		}
		if (isOpcode(cn.access, Opcodes.ACC_DEPRECATED)) {
			this.pw.println(".deprecated");
		}
		printAnnotations(cn.visibleAnnotations, 1);
		printAnnotations(cn.invisibleAnnotations, 2);
		println(".debug ", cn.sourceDebug == null ? null
				: '"' + cn.sourceDebug + '"');

		for (int i = 0; i < cn.innerClasses.size(); ++i) {
			InnerClassNode in = (InnerClassNode) cn.innerClasses.get(i);
			this.pw.print(".inner class");
			this.pw.print(access(in.access));
			if (in.innerName != null) {
				this.pw.print(' ');
				this.pw.print(in.innerName);
			}
			if (in.name != null) {
				this.pw.print(" inner ");
				this.pw.print(in.name);
			}
			if (in.outerName != null) {
				this.pw.print(" outer ");
				this.pw.print(in.outerName);
			}
			this.pw.println();
		}

		for (int i = 0; i < cn.fields.size(); ++i) {
			FieldNode fn = (FieldNode) cn.fields.get(i);
			boolean annotations = false;
			if (fn.visibleAnnotations != null
					&& fn.visibleAnnotations.size() > 0) {
				annotations = true;
			}
			if (fn.invisibleAnnotations != null
					&& fn.invisibleAnnotations.size() > 0) {
				annotations = true;
			}
			boolean deprecated = isOpcode(fn.access, Opcodes.ACC_DEPRECATED);
			this.pw.print("\n.field");
			this.pw.print(access(fn.access));
			this.pw.print(" '");
			this.pw.print(fn.name);
			this.pw.print("' ");
			this.pw.print(fn.desc);
			this.descriptors.add(fn.desc);
			if (fn.signature != null && (!deprecated && !annotations)) {
				this.pw.print(" signature \"");
				this.pw.print(fn.signature);
				this.pw.print("\"");
			}
			if (fn.value instanceof String) {
				StringBuilder buf = new StringBuilder();
				Printer.appendString(buf, (String) fn.value);
				this.pw.print(" = ");
				this.pw.print(buf.toString());
			} else if (fn.value != null) {
				this.pw.print(" = ");
				print(fn.value);
				this.pw.println();
			}
			this.pw.println();
			if (fn.signature != null && (deprecated || annotations)) {
				this.pw.print(".signature \"");
				this.pw.print(fn.signature);
				this.pw.println("\"");
			}
			if (deprecated) {
				this.pw.println(".deprecated");
			}
			printAnnotations(fn.visibleAnnotations, 1);
			printAnnotations(fn.invisibleAnnotations, 2);
			if (deprecated || annotations) {
				this.pw.println(".end field");
			}
		}

		for (int i = 0; i < cn.methods.size(); ++i) {
			MethodNode mn = (MethodNode) cn.methods.get(i);
			this.pw.print("\n.method");
			this.pw.print(access(mn.access));
			this.pw.print(' ');
			this.pw.print(mn.name);
			this.provides.add(mn.name);

			this.pw.println(mn.desc);
			if (mn.signature != null) {
				this.pw.print(".signature \"");
				this.pw.print(mn.signature);
				this.pw.println("\"");
			}
			if (mn.annotationDefault != null) {
				this.pw.println(".annotation default");
				printAnnotationValue(mn.annotationDefault);
				this.pw.println(".end annotation");
			}
			printAnnotations(mn.visibleAnnotations, 1);
			printAnnotations(mn.invisibleAnnotations, 2);
			if (mn.visibleParameterAnnotations != null) {
				for (int j = 0; j < mn.visibleParameterAnnotations.length; ++j) {
					printAnnotations(mn.visibleParameterAnnotations[j], 1);
				}
			}
			if (mn.invisibleParameterAnnotations != null) {
				for (int j = 0; j < mn.invisibleParameterAnnotations.length; ++j) {
					printAnnotations(mn.invisibleParameterAnnotations[j], 2);
				}
			}
			for (int j = 0; j < mn.exceptions.size(); ++j) {
				println(".throws ", (String) mn.exceptions.get(j));
			}
			if (isOpcode(mn.access, Opcodes.ACC_DEPRECATED)) {
				this.pw.println(".deprecated");
			}
			if (mn.instructions.size() > 0) {
				this.labelNames.clear();
				for (int j = 0; j < mn.tryCatchBlocks.size(); ++j) {
					TryCatchBlockNode tcb = (TryCatchBlockNode) mn.tryCatchBlocks
							.get(j);
					this.pw.print(".catch ");
					this.pw.print(tcb.type);
					this.pw.print(" from ");
					print(tcb.start);
					this.pw.print(" to ");
					print(tcb.end);
					this.pw.print(" using ");
					print(tcb.handler);
					this.pw.println();
				}
				for (int j = 0; j < mn.instructions.size(); ++j) {
					AbstractInsnNode in = mn.instructions.get(j);
					in.accept(new MethodVisitor(ASM9) {

						@Override
						public void visitFrame(int type, int local,
								Object[] locals, int stack, Object[] stacks) {
							if (type != Opcodes.F_FULL && type != Opcodes.F_NEW) {
								throw new RuntimeException(
										"Compressed frames unsupported, use EXPAND_FRAMES option");
							}
							JasminifierClassAdapter.this.pw.println(".stack");
							for (int i = 0; i < local; ++i) {
								JasminifierClassAdapter.this.pw
										.print("locals ");
								printFrameType(locals[i]);
								JasminifierClassAdapter.this.pw.println();
							}
							for (int i = 0; i < stack; ++i) {
								JasminifierClassAdapter.this.pw.print("stack ");
								printFrameType(stacks[i]);
								JasminifierClassAdapter.this.pw.println();
							}
							JasminifierClassAdapter.this.pw
									.println(".end stack");
						}

						@Override
						public void visitInsn(int opcode) {
							print(opcode);
							JasminifierClassAdapter.this.pw.println();
						}

						@Override
						public void visitIntInsn(int opcode, int operand) {
							print(opcode);
							if (opcode == Opcodes.NEWARRAY) {
								switch (operand) {
								case Opcodes.T_BOOLEAN:
									JasminifierClassAdapter.this.pw
											.println(" boolean");
									break;
								case Opcodes.T_CHAR:
									JasminifierClassAdapter.this.pw
											.println(" char");
									break;
								case Opcodes.T_FLOAT:
									JasminifierClassAdapter.this.pw
											.println(" float");
									break;
								case Opcodes.T_DOUBLE:
									JasminifierClassAdapter.this.pw
											.println(" double");
									break;
								case Opcodes.T_BYTE:
									JasminifierClassAdapter.this.pw
											.println(" byte");
									break;
								case Opcodes.T_SHORT:
									JasminifierClassAdapter.this.pw
											.println(" short");
									break;
								case Opcodes.T_INT:
									JasminifierClassAdapter.this.pw
											.println(" int");
									break;
								case Opcodes.T_LONG:
								default:
									JasminifierClassAdapter.this.pw
											.println(" long");
									break;
								}
							} else {
								JasminifierClassAdapter.this.pw.print(' ');
								JasminifierClassAdapter.this.pw
										.println(operand);
							}
						}

						@Override
						public void visitVarInsn(int opcode, int var) {
							print(opcode);
							JasminifierClassAdapter.this.pw.print(' ');
							JasminifierClassAdapter.this.pw.println(var);
						}

						@Override
						public void visitTypeInsn(int opcode, String type) {
							print(opcode);
							JasminifierClassAdapter.this.pw.print(' ');
							JasminifierClassAdapter.this.pw.println(type);
						}

						@Override
						public void visitFieldInsn(int opcode, String owner,
								String name, String desc) {
							print(opcode);
							JasminifierClassAdapter.this.pw.print(' ');
							JasminifierClassAdapter.this.pw.print(owner);
							JasminifierClassAdapter.this.pw.print('/');
							JasminifierClassAdapter.this.pw.print(name);
							JasminifierClassAdapter.this.pw.print(' ');
							JasminifierClassAdapter.this.pw.println(desc);
							addDep(owner, name);
							JasminifierClassAdapter.this.descriptors.add(desc);
						}

						@Override
						public void visitMethodInsn(int opcode, String owner,
								String name, String desc, boolean isInterface) {
							print(opcode);
							JasminifierClassAdapter.this.pw.print(' ');
							JasminifierClassAdapter.this.pw.print(owner);
							JasminifierClassAdapter.this.pw.print('/');
							JasminifierClassAdapter.this.pw.print(name);
							JasminifierClassAdapter.this.pw.print(desc);
							if (opcode == Opcodes.INVOKEINTERFACE) {
								JasminifierClassAdapter.this.pw.print(' ');
								JasminifierClassAdapter.this.pw.print((Type
										.getArgumentsAndReturnSizes(desc) >> 2) - 1);
							}
							JasminifierClassAdapter.this.pw.println();
							JasminifierClassAdapter.this.descriptors.add(desc);
							addDep(owner, name);
						}

						@Override
						public void visitJumpInsn(int opcode, Label label) {
							print(opcode);
							JasminifierClassAdapter.this.pw.print(' ');
							print(label);
							JasminifierClassAdapter.this.pw.println();
						}

						@Override
						public void visitLabel(Label label) {
							print(label);
							JasminifierClassAdapter.this.pw.println(':');
						}

						@Override
						public void visitLdcInsn(Object cst) {
							JasminifierClassAdapter.this.pw.print("ldc ");
							if (cst instanceof Type) {
								JasminifierClassAdapter.this.pw
										.print(((Type) cst).getInternalName());
							} else {
								print(cst);
							}
							JasminifierClassAdapter.this.pw.println();
						}

						@Override
						public void visitIincInsn(int var, int increment) {
							JasminifierClassAdapter.this.pw.print("iinc ");
							JasminifierClassAdapter.this.pw.print(var);
							JasminifierClassAdapter.this.pw.print(' ');
							JasminifierClassAdapter.this.pw.println(increment);
						}

						@Override
						public void visitTableSwitchInsn(int min, int max,
								Label dflt, Label... labels) {
							JasminifierClassAdapter.this.pw
									.print("tableswitch ");
							JasminifierClassAdapter.this.pw.println(min);
							for (int i = 0; i < labels.length; ++i) {
								print(labels[i]);
								JasminifierClassAdapter.this.pw.println();
							}
							JasminifierClassAdapter.this.pw.print("default : ");
							print(dflt);
							JasminifierClassAdapter.this.pw.println();
						}

						@Override
						public void visitLookupSwitchInsn(Label dflt,
								int[] keys, Label[] labels) {
							if (keys.length == 0) {
								// TODO Jasmin bug workaround
								JasminifierClassAdapter.this.pw.print("goto ");
								print(dflt);
								JasminifierClassAdapter.this.pw.println();
								return;
							}
							JasminifierClassAdapter.this.pw
									.println("lookupswitch");
							for (int i = 0; i < keys.length; ++i) {
								JasminifierClassAdapter.this.pw.print(keys[i]);
								JasminifierClassAdapter.this.pw.print(" : ");
								print(labels[i]);
								JasminifierClassAdapter.this.pw.println();
							}
							JasminifierClassAdapter.this.pw.print("default : ");
							print(dflt);
							JasminifierClassAdapter.this.pw.println();
						}

						@Override
						public void visitMultiANewArrayInsn(String desc,
								int dims) {
							JasminifierClassAdapter.this.pw
									.print("multianewarray ");
							JasminifierClassAdapter.this.pw.print(desc);
							JasminifierClassAdapter.this.pw.print(' ');
							JasminifierClassAdapter.this.pw.println(dims);
							JasminifierClassAdapter.this.descriptors.add(desc);
						}

						@Override
						public void visitLineNumber(int line, Label start) {
							JasminifierClassAdapter.this.pw.print(".line ");
							JasminifierClassAdapter.this.pw.println(line);
						}
					});
				}
				for (int j = 0; j < mn.localVariables.size(); ++j) {
					LocalVariableNode lv = (LocalVariableNode) mn.localVariables
							.get(j);
					this.pw.print(".var ");
					this.pw.print(lv.index);
					this.pw.print(" is '");
					this.pw.print(lv.name);
					this.pw.print("' ");
					this.pw.print(lv.desc);
					this.descriptors.add(lv.desc);
					if (lv.signature != null) {
						this.pw.print(" signature \"");
						this.pw.print(lv.signature);
						this.pw.print("\"");
					}
					this.pw.print(" from ");
					print(lv.start);
					this.pw.print(" to ");
					print(lv.end);
					this.pw.println();
				}
				println(".limit locals ", Integer.toString(mn.maxLocals));
				println(".limit stack ", Integer.toString(mn.maxStack));
			}
			this.pw.println(".end method");
			dumpState();
		}
		super.visitEnd();
	}

	private void dumpState() {
		for (String el : this.dependencies) {
			this.pw.println(".dep " + el);

		}
		this.dependencies.clear();

		for (String el : this.provides) {
			this.pw.println(".provide " + this.className + ";" + el);

		}

		(sortDescriptors 
				? this.descriptors.stream().sorted() 
						: this.descriptors.stream()) 
		.forEach(d -> {
			this.pw.println(".desc " + d);
		});
		this.dependencies.clear();
		this.provides.clear();
		this.descriptors.clear();

	}

	private void addDep(String owner, String name) {
		this.dependencies.add(owner + ";" + name);
	}

	protected void println(final String directive, final String arg) {
		if (arg != null) {
			this.pw.print(directive);
			this.pw.println(arg);
		}
	}

	protected String access(final int access) {
		StringBuilder sb = new StringBuilder();
		for (Entry<Integer, String> entry : accessMapping.entrySet()) {
			if (isOpcode(access, entry.getKey())) {
				sb.append(" ").append(entry.getValue());
			}
		}
		return sb.toString();
	}

	private boolean isOpcode(final int all, int queried) {
		return (all & queried) != 0;
	}

	protected void print(final int opcode) {
		this.pw.print(Printer.OPCODES[opcode].toLowerCase());
	}

	protected void print(final Object cst) {
		if (cst instanceof String) {
			StringBuilder buf = new StringBuilder();
			Printer.appendString(buf, (String) cst);
			this.pw.print(buf.toString());
		} else if (cst instanceof Float) {
			Float f = (Float) cst;
			// TODO Jasmin bug workaround
			this.pw.print(f.isNaN() || f.isInfinite() ? "0.0" : f);
		} else if (cst instanceof Double) {
			// TODO Jasmin bug workaround
			Double d = (Double) cst;
			this.pw.print(d.isNaN() || d.isInfinite() ? "0.0" : d);
		} else {
			this.pw.print(cst);
		}
	}

	protected void print(final Label l) {
		String name = this.labelNames.get(l);
		if (name == null) {
			name = "L" + this.labelNames.size();
			this.labelNames.put(l, name);
		}
		this.pw.print(name);
	}

	protected void print(final LabelNode l) {
		print(l.getLabel());
	}

	protected void printAnnotations(final List<AnnotationNode> annotations,
			int visible) {
		if (annotations != null) {
			for (int j = 0; j < annotations.size(); ++j) {
				printAnnotation(annotations.get(j), visible, -1);
			}
		}
	}

	protected void printAnnotation(final AnnotationNode n, final int visible,
			final int param) {
		this.pw.print(".annotation ");
		if (visible > 0) {
			if (param == -1) {
				this.pw.print(visible == 1 ? "visible " : "invisible ");
			} else {
				this.pw.print(visible == 1 ? "visibleparam "
						: "invisibleparam ");
				this.pw.print(param);
				this.pw.print(' ');
			}
			this.pw.print(n.desc);
		}
		this.pw.println();
		Stream<Entry<Object, Object>> entries = IntStream.range(0, n.values.size() / 2)
				.mapToObj(i -> Map.entry(n.values.get(2 * i), n.values.get(2 * i + 1)));
		if (sortAnnotationValues) {
			entries = entries.sorted(comparing(Map.Entry::getKey, comparing(Object::toString)));
		}
		entries.forEach(m -> {
			this.pw.print(m.getKey());
			this.pw.print(' ');
			printAnnotationValue(m.getValue());
		});
		this.pw.println(".end annotation");
	}

	protected void printAnnotationValue(final Object value) {
		if (value instanceof String[]) {
			this.pw.print("e ");
			this.pw.print(((String[]) value)[0]);
			this.pw.print(" = ");
			print(((String[]) value)[1]);
			this.pw.println();
		} else if (value instanceof AnnotationNode) {
			this.pw.print("@ ");
			this.pw.print(((AnnotationNode) value).desc);
			this.pw.print(" = ");
			printAnnotation((AnnotationNode) value, 0, -1);
		} else if (value instanceof byte[]) {
			this.pw.print("[B = ");
			byte[] v = (byte[]) value;
			for (int i = 0; i < v.length; i++) {
				this.pw.print(v[i]);
				this.pw.print(' ');
			}
			this.pw.println();
		} else if (value instanceof boolean[]) {
			this.pw.print("[Z = ");
			boolean[] v = (boolean[]) value;
			for (int i = 0; i < v.length; i++) {
				this.pw.print(v[i] ? '1' : '0');
				this.pw.print(' ');
			}
			this.pw.println();
		} else if (value instanceof short[]) {
			this.pw.print("[S = ");
			short[] v = (short[]) value;
			for (int i = 0; i < v.length; i++) {
				this.pw.print(v[i]);
				this.pw.print(' ');
			}
			this.pw.println();
		} else if (value instanceof char[]) {
			this.pw.print("[C = ");
			char[] v = (char[]) value;
			for (int i = 0; i < v.length; i++) {
				this.pw.print(Integer.valueOf(v[i]));
				this.pw.print(' ');
			}
			this.pw.println();
		} else if (value instanceof int[]) {
			this.pw.print("[I = ");
			int[] v = (int[]) value;
			for (int i = 0; i < v.length; i++) {
				this.pw.print(v[i]);
				this.pw.print(' ');
			}
			this.pw.println();
		} else if (value instanceof long[]) {
			this.pw.print("[J = ");
			long[] v = (long[]) value;
			for (int i = 0; i < v.length; i++) {
				this.pw.print(v[i]);
				this.pw.print(' ');
			}
			this.pw.println();
		} else if (value instanceof float[]) {
			this.pw.print("[F = ");
			float[] v = (float[]) value;
			for (int i = 0; i < v.length; i++) {
				print(Float.valueOf(v[i]));
				this.pw.print(' ');
			}
			this.pw.println();
		} else if (value instanceof double[]) {
			this.pw.print("[D = ");
			double[] v = (double[]) value;
			for (int i = 0; i < v.length; i++) {
				print(Double.valueOf(v[i]));
				this.pw.print(' ');
			}
			this.pw.println();
		} else if (value instanceof List) {
			List<?> l = (List<?>) value;
			if (l.size() > 0) {
				Object o = l.get(0);
				if (o instanceof String[]) {
					this.pw.print("[e ");
					this.pw.print(((String[]) o)[0]);
					this.pw.print(" = ");
				} else if (o instanceof AnnotationNode) {
					this.pw.print("[& ");
					this.pw.print(((AnnotationNode) o).desc);
					this.pw.print(" = ");
					this.pw.print("[@ = ");
				} else if (o instanceof String) {
					this.pw.print("[s = ");
				} else if (o instanceof Byte) {
					this.pw.print("[B = ");
				} else if (o instanceof Boolean) {
					this.pw.print("[Z = ");
				} else if (o instanceof Character) {
					this.pw.print("[C = ");
				} else if (o instanceof Short) {
					this.pw.print("[S = ");
				} else if (o instanceof Type) {
					this.pw.print("[c = ");
				} else if (o instanceof Integer) {
					this.pw.print("[I = ");
				} else if (o instanceof Float) {
					this.pw.print("[F = ");
				} else if (o instanceof Long) {
					this.pw.print("[J = ");
				} else if (o instanceof Double) {
					this.pw.print("[D = ");
				}
				for (int j = 0; j < l.size(); ++j) {
					printAnnotationArrayValue(l.get(j));
					this.pw.print(' ');
				}
			} else {
				this.pw.print("; empty array annotation value");
			}
			this.pw.println();
		} else if (value instanceof String) {
			this.pw.print("s = ");
			print(value);
			this.pw.println();
		} else if (value instanceof Byte) {
			this.pw.print("B = ");
			this.pw.println(((Byte) value).intValue());
		} else if (value instanceof Boolean) {
			this.pw.print("Z = ");
			this.pw.println(((Boolean) value).booleanValue() ? 1 : 0);
		} else if (value instanceof Character) {
			this.pw.print("C = ");
			this.pw.println(Integer.valueOf(((Character) value).charValue()));
		} else if (value instanceof Short) {
			this.pw.print("S = ");
			this.pw.println(((Short) value).intValue());
		} else if (value instanceof Type) {
			this.pw.print("c = ");
			this.pw.println(((Type) value).getDescriptor());
		} else if (value instanceof Integer) {
			this.pw.print("I = ");
			print(value);
			this.pw.println();
		} else if (value instanceof Float) {
			this.pw.print("F = ");
			print(value);
			this.pw.println();
		} else if (value instanceof Long) {
			this.pw.print("J = ");
			print(value);
			this.pw.println();
		} else if (value instanceof Double) {
			this.pw.print("D = ");
			print(value);
			this.pw.println();
			throw new RuntimeException();
		}
	}

	protected void printAnnotationArrayValue(final Object value) {
		if (value instanceof String[]) {
			print(((String[]) value)[1]);
		} else if (value instanceof AnnotationNode) {
			printAnnotation((AnnotationNode) value, 0, -1);
		} else if (value instanceof String) {
			print(value);
		} else if (value instanceof Byte) {
			this.pw.print(((Byte) value).intValue());
		} else if (value instanceof Boolean) {
			this.pw.print(((Boolean) value).booleanValue() ? 1 : 0);
		} else if (value instanceof Character) {
			this.pw.print(Integer.valueOf(((Character) value).charValue()));
		} else if (value instanceof Short) {
			this.pw.print(((Short) value).intValue());
		} else if (value instanceof Type) {
			this.pw.print(((Type) value).getDescriptor());
		} else {
			print(value);
		}
	}

	protected void printFrameType(final Object type) {
		if (type == Opcodes.TOP) {
			this.pw.print("Top");
		} else if (type == Opcodes.INTEGER) {
			this.pw.print("Integer");
		} else if (type == Opcodes.FLOAT) {
			this.pw.print("Float");
		} else if (type == Opcodes.LONG) {
			this.pw.print("Long");
		} else if (type == Opcodes.DOUBLE) {
			this.pw.print("Double");
		} else if (type == Opcodes.NULL) {
			this.pw.print("Null");
		} else if (type == Opcodes.UNINITIALIZED_THIS) {
			this.pw.print("UninitializedThis");
		} else if (type instanceof Label) {
			this.pw.print("Uninitialized ");
			print((Label) type);
		} else {
			this.pw.print("Object ");
			this.pw.print(type);
		}
	}
}
