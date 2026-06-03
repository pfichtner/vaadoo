package example.vaadoo;

import java.util.List;
import java.util.Objects;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record SampleRecordWithGenericNotNull(@NotEmpty List<@NotNull String> values) {

	public SampleRecordWithGenericNotNull {
		if (values.stream().anyMatch(Objects::isNull)) {
			throw new IllegalStateException("NULLS_REACHED_COMPACT_CONSTRUCTOR");
		}
	}

}
