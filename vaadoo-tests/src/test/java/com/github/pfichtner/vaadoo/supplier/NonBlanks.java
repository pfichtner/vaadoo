package com.github.pfichtner.vaadoo.supplier;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ArbitrarySupplier;

public class NonBlanks implements ArbitrarySupplier<String> {

	@Override
	public Arbitrary<String> get() {
		return Arbitraries.of("x", "xXx", "x ", " x");
	}

}
