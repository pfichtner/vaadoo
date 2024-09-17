package com.github.pfichtner.vaadoo.supplier;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.util.EnumSet.allOf;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Collection;
import java.util.Set;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ArbitrarySupplier;
import net.jqwik.api.providers.TypeUsage;

public class CharSequences implements ArbitrarySupplier<CharSequence> {

	public static enum Type {
		NON_BLANKS("x", "xXx", "x ", " x", "X", "1", "!", "abc", "XyZ-987"),
		BLANKS("", " ", "     ", "\t", "\n", "\r", "\t \n", "\r\n ");

		private final CharSequence[] sequences;

		Type(String... values) {
			this.sequences = values;
		}

		public CharSequence[] sequences() {
			return sequences;
		}

	}

	@Retention(RUNTIME)
	@Target(PARAMETER)
	public static @interface Types {
		Type[] value();

		Class<?>[] ofType() default {};
	}

	@Override
	public Arbitrary<CharSequence> get() {
		return arbitraries(allOf(Type.class));
	}

	@Override
	public Arbitrary<CharSequence> supplyFor(TypeUsage targetType) {
		return arbitraries(targetType.findAnnotation(Types.class) //
				.map(Types::value).map(Set::of) //
				.orElseGet(() -> allOf(Type.class)));
	}

	private static Arbitrary<CharSequence> arbitraries(Set<Type> type) {
		return Arbitraries.of(type.stream().map(Type::sequences).map(Set::of).flatMap(Collection::stream).toList());
	}

}
