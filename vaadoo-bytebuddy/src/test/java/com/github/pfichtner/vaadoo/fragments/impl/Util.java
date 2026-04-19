package com.github.pfichtner.vaadoo.fragments.impl;

import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.github.pfichtner.vaadoo.AnnotationFactory;
import com.github.pfichtner.vaadoo.fragments.Jsr380CodeFragment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
				assertThatNoException() //
						.describedAs("%s should not throw exception for type %s on value %s", anno.annotationType(),
								type.getName(), value)
						.isThrownBy(() -> accept(value, type));
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
				assertThatExceptionOfType(exceptionType) //
						.describedAs("%s should throw exception for type %s on value %s", anno.annotationType(),
								type.getName(), value) //
						.isThrownBy(() -> accept(value, type)).withMessage("theMessage");
			} else {
				assertThatNoException() //
						.describedAs("%s should not throw exception for type %s on value %s", anno.annotationType(),
								type.getName(), value)
						.isThrownBy(() -> accept(value, type));
			}
		}

		private void accept(Object value, Class<?> param1Type) {
			Method method = only(checkMethodsThatAccepts(param1Type));
			try {
				method.invoke(sut, anno, convert(param1Type, value));
			} catch (IllegalAccessException | IllegalArgumentException e) {
				throw new RuntimeException(e);
			} catch (InvocationTargetException e) {
				Throwable cause = e.getCause();
				throw cause instanceof RuntimeException ? (RuntimeException) cause : new RuntimeException(e);
			}
		}

		public boolean supports(Class<?> param1Type) {
			return checkMethodsThatAccepts(param1Type).findAny().isPresent();
		}

		public boolean nullIsValidValue() {
			Class<? extends Annotation> annotationType = getAnno().annotationType();
			return annotationType != NotNull.class //
					&& annotationType != NotBlank.class //
					&& annotationType != NotEmpty.class //
			;
		}

		private Stream<Method> checkMethodsThatAccepts(Class<?> param1Type) {
			return Arrays.stream(sut.getClass().getMethods()).filter(m -> m.getName().equals("check"))
					.filter(m -> m.getParameterTypes().length == 2 && m.getParameterTypes()[0].isInstance(anno)
							&& m.getParameterTypes()[1].isAssignableFrom(param1Type));
		}

	}

	static <T> T only(Stream<T> stream) {
		return stream.reduce((v1, v2) -> {
			throw new IllegalStateException(String.format("more than one match (%s, %s)", v1, v2));
		}).orElseThrow(() -> new IllegalStateException("stream is empty"));
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
				return n instanceof BigDecimal ? n : new BigDecimal(n.toString());
			}
		}

		return target.cast(value);
	}

	@SuppressWarnings("null")
	public static List<Fixture> fixtures(Object obj) {
		return Arrays.stream(obj.getClass().getDeclaredFields()) //
				.filter(f -> Fixture.class.isAssignableFrom(f.getType())) //
				.peek(f -> f.setAccessible(true)) //
				.map(f -> getValue(obj, f)) //
				.map(Fixture.class::cast) //
				.collect(toList());
	}

	private static Object getValue(Object obj, Field field) {
		try {
			return field.get(obj);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

}
