package example.vaadoo;

import java.util.List;

import jakarta.validation.constraints.NotNull;

// records are automatically handled as value objects, no need to implement ValueObject nor to annotate
public record SampleRecordWithSideEffect(List<String> list, @NotNull String toAdd) {

	// test that validation is called before the compact constructor body
	public SampleRecordWithSideEffect {
		list.add(toAdd);
	}

}
