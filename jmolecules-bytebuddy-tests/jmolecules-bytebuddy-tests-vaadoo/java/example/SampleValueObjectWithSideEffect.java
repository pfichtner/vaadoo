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
package example;

import java.util.List;

import jakarta.validation.constraints.NotNull;

public class SampleValueObjectWithSideEffect extends SuperClassThatThrowsRTE {

	// nor the super constructor nor the List#add should get called if "toAdd" is null
	public SampleValueObjectWithSideEffect(List<String> list, @NotNull String toAdd) {
		list.add(toAdd);
	}

}
