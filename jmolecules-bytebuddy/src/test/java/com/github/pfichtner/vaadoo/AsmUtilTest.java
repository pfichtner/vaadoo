package com.github.pfichtner.vaadoo;

import static com.github.pfichtner.vaadoo.AsmUtil.sizeOf;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import net.bytebuddy.jar.asm.Type;

class AsmUtilTest {

	@Test
	void testSizeOf() {
		assertThat(sizeOf(new Type[] { //
				Type.LONG_TYPE, Type.LONG_TYPE, Type.getType(CharSequence.class) //
		})).isEqualTo(5);
	}

}
