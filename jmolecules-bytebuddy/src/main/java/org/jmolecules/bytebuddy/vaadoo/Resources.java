package org.jmolecules.bytebuddy.vaadoo;

import static java.util.Collections.enumeration;
import static java.util.stream.Collectors.toMap;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.ResourceBundle;

public final class Resources {

	private static class MergingResourceBundleControl extends ResourceBundle.Control {

		@Override
		public ResourceBundle newBundle(String baseName, Locale locale, String format, ClassLoader loader,
				boolean reload) throws IllegalAccessException, InstantiationException, IOException {

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

	private Resources() {
		super();
	}

	public static String message(String key) {
		return messages.getOrDefault(key, key);
	}

	private static Map<String, String> loadMessages() {
		ResourceBundle bundle = ResourceBundle.getBundle("org/jmolecules/bytebuddy/vaadoo",
				new MergingResourceBundleControl());
		return bundle.keySet().stream().collect(toMap(key -> key, bundle::getString));
	}

}
