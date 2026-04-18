package com.github.pfichtner.vaadoo.fragments.impl;

import static java.math.RoundingMode.UNNECESSARY;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;

import com.github.pfichtner.vaadoo.AnnotationFactory;
import com.github.pfichtner.vaadoo.fragments.Jsr380CodeFragment;

import lombok.RequiredArgsConstructor;
import lombok.Value;

class Util {

	@RequiredArgsConstructor
	public static class FixtureFactory {
		private final Jsr380CodeFragment sut;
		private final Class<? extends Exception> nullExceptionType;

		public Fixture create(Class<? extends Annotation> clazz) {
			return Fixture.of(sut, nullExceptionType, clazz);
		}

		public Fixture create(Class<? extends Annotation> clazz, Map<String, Object> data) {
			return Fixture.of(sut, nullExceptionType, clazz, data);
		}

	}

	@Value
	public static class Fixture {

		Jsr380CodeFragment sut;
		Class<? extends Exception> nullExceptionType;
		Annotation anno;

		public static Fixture of(Jsr380CodeFragment sut, Class<? extends Exception> nullExceptionType,
				Class<? extends Annotation> clazz) {
			return of(sut, nullExceptionType, clazz, emptyMap());
		}

		public static Fixture of(Jsr380CodeFragment sut, Class<? extends Exception> nullExceptionType,
				Class<? extends Annotation> clazz, Map<String, Object> data) {
			return new Fixture(sut, nullExceptionType, AnnotationFactory.make(clazz, data));
		}

		public void noException(Object v, Class<?>... types) {
			for (Class<?> type : types) {
				assertThatNoException().isThrownBy(() -> accept(v, type));
			}
		}

		public void nullPointerExceptionIf(boolean b, Object v, Class<?>... types) {
			for (Class<?> type : types) {
				assertException(b, nullExceptionType, v, type);
			}
		}

		public void illegalArgumentExceptionIf(boolean b, Object v, Class<?>... types) {
			for (Class<?> type : types) {
				assertException(b, IllegalArgumentException.class, v, type);
			}
		}

		private void assertException(boolean b, Class<? extends Exception> exceptionType, Object v, Class<?> type) {
			if (b) {
				assertThatExceptionOfType(exceptionType).isThrownBy(() -> accept(v, type)).withMessage("theMessage");
			} else {
				assertThatNoException().isThrownBy(() -> accept(v, type));
			}
		}

		private void accept(Object value, Class<?> param1Type) {
			Method method = only(Arrays.stream(sut.getClass().getMethods()).filter(m -> m.getName().equals("check"))
					.filter(m -> m.getParameterTypes().length == 2 && m.getParameterTypes()[0].isInstance(anno)
							&& m.getParameterTypes()[1].isAssignableFrom(param1Type)));
			try {
				method.invoke(sut, anno, convert(param1Type, value));
			} catch (IllegalAccessException | IllegalArgumentException e) {
				throw new RuntimeException(e);
			} catch (InvocationTargetException e) {
				Throwable cause = e.getCause();
				throw cause instanceof RuntimeException ? (RuntimeException) cause : new RuntimeException(e);
			}
		}

	}

	static Class<?>[] booleanTypes = new Class<?>[] { boolean.class, Boolean.class };
	static Class<?>[] numberTypes = new Class<?>[] { byte.class, short.class, int.class, long.class, Byte.class,
			Short.class, Integer.class, Long.class, BigInteger.class, BigDecimal.class };

	static Class<?>[] byteTypes = new Class<?>[] { byte.class, Byte.class };
	static Class<?>[] shortTypes = new Class<?>[] { short.class, Short.class };
	static Class<?>[] intTypes = new Class<?>[] { int.class, Integer.class };
	static Class<?>[] longTypes = new Class<?>[] { long.class, Long.class };

	static String[] emptyStringArray = new String[0];
	static Object[] emptyObjectArray = new Object[0];

	static <T> T only(Stream<T> stream) {
		return stream.reduce((_ign1, _ign2) -> {
			throw new IllegalStateException("multiple elements");
		}).orElseThrow(() -> new IllegalStateException("no match"));
	}

	static Object convert(Class<?> target, Object value) {
		if (value == null) {
			return null;
		}

		if (target == String.class)
			return String.valueOf(value);

		if (target.isPrimitive()) {
			if (target == byte.class) {
				return ((Number) value).byteValue();
			} else if (target == short.class) {
				return ((Number) value).shortValue();
			} else if (target == int.class) {
				return ((Number) value).intValue();
			} else if (target == long.class) {
				return ((Number) value).longValue();
			} else if (target == float.class) {
				return ((Number) value).floatValue();
			} else if (target == double.class) {
				return ((Number) value).doubleValue();
			} else if (target == char.class) {
				return (char) ((Number) value).intValue();
			} else if (target == boolean.class) {
				return value;
			}
		}

		// wrapper types
		if (Number.class.isAssignableFrom(target) && value instanceof Number) {
			Number n = (Number) value;
			if (target == Byte.class) {
				return n.byteValue();
			} else if (target == Short.class) {
				return n.shortValue();
			} else if (target == Integer.class) {
				return n.intValue();
			} else if (target == Long.class) {
				return n.longValue();
			} else if (target == Float.class) {
				return n.floatValue();
			} else if (target == Double.class) {
				return n.doubleValue();
			} else if (target == BigInteger.class) {
				return BigInteger.valueOf(n.longValue());
			} else if (target == BigDecimal.class) {
				BigDecimal bd = BigDecimal.valueOf(n.doubleValue());
				return value.getClass() == Integer.class || value.getClass() == Long.class ? bd.setScale(0, UNNECESSARY)
						: bd;
			}
		}

		return target.cast(value);
	}

}
