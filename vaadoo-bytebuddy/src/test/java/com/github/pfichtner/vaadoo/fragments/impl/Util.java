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

import lombok.Value;

class Util {

	@Value
	public static class Fixture {

		Jsr380CodeFragment sut;
		Annotation anno;

		public static Fixture of(Jsr380CodeFragment sut, Class<? extends Annotation> clazz) {
			return of(sut, clazz, emptyMap());
		}

		public static Fixture of(Jsr380CodeFragment sut, Class<? extends Annotation> clazz, Map<String, Object> data) {
			return new Fixture(sut, AnnotationFactory.make(clazz, data));
		}

		public void noException(Object value, Class<?>... types) {
			for (Class<?> type : types) {
				assertThatNoException().isThrownBy(() -> accept(value, type));
			}
		}

		public void illegalArgumentExceptionIf(boolean shouldThrow, Object value, Class<?>... types) {
			assertException(shouldThrow, value, IllegalArgumentException.class, types);
		}

		public void assertException(boolean shouldThrow, Object value, Class<? extends Exception> exceptionType,
				Class<?>... types) {
			for (Class<?> type : types) {
				assertException(shouldThrow, exceptionType, value, type);
			}
		}

		private void assertException(boolean shouldThrow, Class<? extends Exception> exceptionType, Object value,
				Class<?> type) {
			if (shouldThrow) {
				assertThatExceptionOfType(exceptionType).isThrownBy(() -> accept(value, type))
						.withMessage("theMessage");
			} else {
				assertThatNoException().isThrownBy(() -> accept(value, type));
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
