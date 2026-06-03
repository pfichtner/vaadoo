package example.vaadoo;

import java.util.List;
import java.util.Objects;

import org.jmolecules.ddd.types.ValueObject;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public class SampleClassWithGenericNotNull implements ValueObject {

	private final List<String> values;

	public SampleClassWithGenericNotNull(@NotEmpty List<@NotNull String> values) {
		if (values.stream().anyMatch(Objects::isNull)) {
			throw new IllegalStateException("NULLS_REACHED_CONSTRUCTOR");
		}
		this.values = values;
	}

	public List<String> getValues() {
		return values;
	}

}
