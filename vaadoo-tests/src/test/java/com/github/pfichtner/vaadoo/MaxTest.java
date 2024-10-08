package com.github.pfichtner.vaadoo;

import static com.github.pfichtner.vaadoo.DynamicByteCode.assertException;
import static com.github.pfichtner.vaadoo.DynamicByteCode.assertNoException;
import static com.github.pfichtner.vaadoo.DynamicByteCode.dynamicClass;
import static com.github.pfichtner.vaadoo.DynamicByteCode.lowerBoundInLongRange;
import static com.github.pfichtner.vaadoo.DynamicByteCode.randomConfigWith;
import static com.github.pfichtner.vaadoo.DynamicByteCode.transform;
import static com.github.pfichtner.vaadoo.DynamicByteCode.upperBoundInLongRange;
import static com.github.pfichtner.vaadoo.DynamicByteCode.ConfigEntry.entry;
import static com.github.pfichtner.vaadoo.NumberWrapper.numberWrapper;
import static com.github.pfichtner.vaadoo.supplier.Classes.SubTypes.NUMBERS;
import static com.github.pfichtner.vaadoo.supplier.Example.nullValue;

import java.lang.annotation.Annotation;
import java.util.Map;

import com.github.pfichtner.vaadoo.supplier.Classes;
import com.github.pfichtner.vaadoo.supplier.Example;
import com.github.pfichtner.vaadoo.supplier.Primitives;

import jakarta.validation.constraints.Max;
import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

class MaxTest {

	private static final Class<? extends Annotation> ANNO_CLASS = Max.class;

	@Property
	void primitiveValueLowerMax( //
			@ForAll(supplier = Primitives.class) //
			@Primitives.Types({ int.class, long.class, short.class, byte.class }) //
			Example example //
	) throws Exception {
		var parameterName = "param";
		var value = numberWrapper(example.type(), example.value());
		Assume.that(!value.isMin());
		@SuppressWarnings("unchecked")
		var config = randomConfigWith(entry(value.type(), parameterName, value.sub(1)).withAnno(ANNO_CLASS,
				Map.of("value", value.roundedLong())));
		var transformed = transform(dynamicClass(config));
		assertNoException(config, transformed);
	}

	@Property
	void primitivesValueEqualMax( //
			@ForAll(supplier = Primitives.class) //
			@Primitives.Types({ int.class, long.class, short.class, byte.class }) //
			Example example //
	) throws Exception {
		var parameterName = "param";
		var value = numberWrapper(example.type(), example.value());
		@SuppressWarnings("unchecked")
		var config = randomConfigWith(entry(value.type(), parameterName, value.value()).withAnno(ANNO_CLASS,
				Map.of("value", value.roundedLong())));
		var transformed = transform(dynamicClass(config));
		assertNoException(config, transformed);
	}

	@Property
	void objectValueLowerMax( //
			@ForAll(supplier = Classes.class) //
			@Classes.Types(value = NUMBERS) //
			Example example //
	) throws Exception {
		var parameterName = "param";
		var value = numberWrapper(example.type(), example.value());
		Assume.that(!value.isMin());
		Number sub = value.sub(1);
		Assume.that(upperBoundInLongRange(sub));
		@SuppressWarnings("unchecked")
		var config = randomConfigWith(
				entry(value.type(), parameterName, sub).withAnno(ANNO_CLASS, Map.of("value", value.roundedLong())));
		var transformed = transform(dynamicClass(config));
		assertNoException(config, transformed);
	}

	@Property
	void objectValueEqualMax( //
			@ForAll(supplier = Classes.class) //
			@Classes.Types(value = NUMBERS) //
			Example example //
	) throws Exception {
		var parameterName = "param";
		var value = numberWrapper(example.type(), example.value());
		Assume.that(upperBoundInLongRange(value.value()));
		@SuppressWarnings("unchecked")
		var config = randomConfigWith(entry(value.type(), parameterName, value.value()).withAnno(ANNO_CLASS,
				Map.of("value", value.roundedLong())));
		var transformed = transform(dynamicClass(config));
		assertNoException(config, transformed);
	}

	@Property
	void nullObjectIsOk(@ForAll(supplier = Classes.class) @Classes.Types(value = NUMBERS) Example example)
			throws Exception {
		var parameterName = "param";
		var value = numberWrapper(example.type(), example.value());
		@SuppressWarnings("unchecked")
		var config = randomConfigWith(entry(value.type(), parameterName, nullValue()).withAnno(ANNO_CLASS,
				Map.of("value", value.roundedLong())));
		var transformed = transform(dynamicClass(config));
		assertNoException(config, transformed);
	}

	@Property
	void objectValueGreaterMax( //
			@ForAll(supplier = Classes.class) //
			@Classes.Types(value = NUMBERS) //
			Example example //
	) throws Exception {
		var parameterName = "param";
		var value = numberWrapper(example.type(), example.value());
		Assume.that(!value.isMax());
		Assume.that(lowerBoundInLongRange(value.value()));
		@SuppressWarnings("unchecked")
		var config = randomConfigWith(entry(value.type(), parameterName, value.add(1)).withAnno(ANNO_CLASS,
				Map.of("value", value.roundedLong())));
		var transformed = transform(dynamicClass(config));
		assertException(config, transformed, parameterName + " must be less than or equal to " + value.roundedLong(),
				IllegalArgumentException.class);
	}

	@Property
	void primitiveValueGreaterMax( //
			@ForAll(supplier = Primitives.class) //
			@Primitives.Types({ int.class, long.class, short.class, byte.class }) //
			Example example //
	) throws Exception {
		var parameterName = "param";
		var value = numberWrapper(example.type(), example.value());
		Assume.that(!value.isMax());
		@SuppressWarnings("unchecked")
		var config = randomConfigWith(entry(value.type(), parameterName, value.add(1)).withAnno(ANNO_CLASS,
				Map.of("value", value.roundedLong())));
		var transformed = transform(dynamicClass(config));
		assertException(config, transformed, parameterName + " must be less than or equal to " + value.roundedLong(),
				IllegalArgumentException.class);
	}

	@Property
	void primitiveCustomMessage( //
			@ForAll(supplier = Primitives.class) //
			@Primitives.Types({ int.class, long.class, short.class, byte.class }) //
			Example example, //
			@ForAll String message) throws Exception {
		var value = numberWrapper(example.type(), example.value());
		Assume.that(!value.isMax());
		@SuppressWarnings("unchecked")
		var config = randomConfigWith(entry(value.type(), "param", value.add(1)).withAnno(ANNO_CLASS,
				Map.of("value", value.roundedLong(), "message", message)));
		var transformed = transform(dynamicClass(config));
		assertException(config, transformed, message, IllegalArgumentException.class);

	}

	@Property
	void objectCustomMessage(@ForAll(supplier = Classes.class) @Classes.Types(value = NUMBERS) Example example,
			@ForAll String message) throws Exception {
		var value = numberWrapper(example.type(), example.value());
		Assume.that(!value.isMax());
		Number add = value.add(1);
		Assume.that(lowerBoundInLongRange(add));
		@SuppressWarnings("unchecked")
		var config = randomConfigWith(entry(value.type(), "param", add).withAnno(ANNO_CLASS,
				Map.of("value", value.roundedLong(), "message", message)));
		var transformed = transform(dynamicClass(config));
		assertException(config, transformed, message, IllegalArgumentException.class);
	}

}
