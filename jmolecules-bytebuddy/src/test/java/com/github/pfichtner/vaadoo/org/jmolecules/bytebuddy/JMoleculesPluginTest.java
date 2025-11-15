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
package com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.net.URISyntaxException;

import org.junit.jupiter.api.Test;

import com.github.pfichtner.vaadoo.org.jmolecules.bytebuddy.JMoleculesPlugin.FileBasedVaadooConfiguration;

/**
 * Tests for {@link FileBasedVaadooConfiguration}.
 *
 * @author Oliver Drotbohm
 * @author Peter Fichtner
 */
class JMoleculesConfigurationTests {

	@Test
	void detectsConfigurationInRootFolder() {
		File file = getFolder("config/direct");
		assertThat(new FileBasedVaadooConfiguration(file).getProperty("in")).isEqualTo("direct");
	}

	@Test
	void stopsTraversingAtNonBuildFolder() {
		File file = getFolder("config/none");
		assertThat(new FileBasedVaadooConfiguration(file).getProperty("in")).isNull();
	}

	@Test
	void detectsConfigInParentBuildFolder() {
		File file = getFolder("config/intermediate/nested");
		assertThat(new FileBasedVaadooConfiguration(file).getProperty("intermediate")).isNull();
	}

	private static File getFolder(String name) {
		try {
			return new File(JMoleculesConfigurationTests.class.getResource(name).toURI());
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}
}
