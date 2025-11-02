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

import static com.github.pfichtner.vaadoo.NamedPlaceholders.replace;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NamedPlaceholdersTest {

	private static final String aBooleanValueKey = "aBooleanValue";

	private static final String inWithExpression1 = "prefix ${" + aBooleanValueKey + " == true} suffix";
	private static final String inWithExpression2 = "prefix ${" + aBooleanValueKey
			+ " == true ? 'someString' : 'anotherString'} suffix";

	@Test
	void noReplacment() {
		String in = "in";
		assertThat(replace(in, emptyMap())).isEqualTo(in);
	}

	@Test
	void simpleReplacmentMatch() {
		String in = "prefix {theKey} suffix";
		String expected = "prefix aValue suffix";
		assertThat(replace(in, Map.of("theKey", "aValue"))).isEqualTo(expected);
	}

	@Test
	void simpleReplacmentNoMatch() {
		String in = "prefix {thekey} suffix";
		assertThat(replace(in, emptyMap())).isEqualTo(in);
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { //
			"true  | prefix true suffix", //
			"false | prefix false suffix", //
	})
	void eval(boolean aBooleanValue, String expected) {
		assertThat(replace(inWithExpression1, mapWithABoolean(aBooleanValue))).isEqualTo(expected);
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { //
			"true  | prefix someString suffix", //
			"false | prefix anotherString suffix", //
	})
	void evalTenary(boolean aBooleanValue, String expected) {
		assertThat(replace(inWithExpression2, mapWithABoolean(aBooleanValue))).isEqualTo(expected);
	}

	@Test
	void booleanValueNotSetIsHandledLikeBooleanFalse() {
		Map<String, Object> mapWithFalse = mapWithABoolean(false);
		Map<String, Object> mapWithTrue = mapWithABoolean(true);
		String emptyReplaced1 = replace(inWithExpression1, emptyMap());
		String emptyReplaced2 = replace(inWithExpression2, emptyMap());
		assertSoftly(s -> {
			s.assertThat(emptyReplaced1).isEqualTo(replace(inWithExpression1, mapWithFalse));
			s.assertThat(emptyReplaced2).isEqualTo(replace(inWithExpression2, mapWithFalse));

			s.assertThat(emptyReplaced1).isNotEqualTo(replace(inWithExpression2, mapWithTrue));
			s.assertThat(emptyReplaced2).isNotEqualTo(replace(inWithExpression2, mapWithTrue));
		});
	}

	/**
	 * "== true" / "== false" is missing here which is not supported <br>
	 * (only <code>a == true ? 'foo' : 'bar'</code> works but not
	 * <code>a ? 'foo' : 'bar'</code>)
	 */
	@Test
	void implicitTrueOperatorNotSupported() {
		assertThatThrownBy(
				() -> replace("prefix ${" + aBooleanValueKey + " ? 'someString' : 'anotherString'} suffix", emptyMap()))
				.hasMessageContaining("Unsupported operator");
	}

	private static Map<String, Object> mapWithABoolean(boolean aBooleanValue) {
		return Map.of(aBooleanValueKey, aBooleanValue);
	}

}
