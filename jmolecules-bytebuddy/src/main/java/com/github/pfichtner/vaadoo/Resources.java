/*
 * Copyright 2022-2025 the original author or authors.
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

import static java.util.Collections.enumeration;
import static java.util.stream.Collectors.toMap;
import static lombok.AccessLevel.PRIVATE;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.ResourceBundle;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = PRIVATE)
public final class Resources {

	private static class MergingResourceBundleControl extends ResourceBundle.Control {

		@Override
		public ResourceBundle newBundle(String baseName, Locale locale, String format, ClassLoader loader,
				boolean reload) throws IOException {

			Enumeration<URL> resources = loader
					.getResources(toResourceName(toBundleName(baseName, locale), "properties"));

			Properties mergedProps = new Properties();
			while (resources.hasMoreElements()) {
				try (InputStream stream = resources.nextElement().openStream()) {
					Properties props = new Properties();
					props.load(stream);
					mergedProps.putAll(props);
				}
			}

			return new ResourceBundle() {

				@Override
				protected Object handleGetObject(String key) {
					return mergedProps.get(key);
				}

				@Override
				public Enumeration<String> getKeys() {
					return enumeration(mergedProps.stringPropertyNames());
				}
			};
		}

		@Override
		public Locale getFallbackLocale(String baseName, Locale locale) {
			return null;
		}
	}

	private static final Map<String, String> messages = loadMessages();

	public static String message(String key) {
		return messages.getOrDefault(key, key);
	}

	private static Map<String, String> loadMessages() {
		ResourceBundle bundle = ResourceBundle.getBundle("org/jmolecules/bytebuddy/vaadoo",
				new MergingResourceBundleControl());
		return bundle.keySet().stream().collect(toMap(key -> key, bundle::getString));
	}

}
