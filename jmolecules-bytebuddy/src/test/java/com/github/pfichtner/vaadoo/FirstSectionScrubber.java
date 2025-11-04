package com.github.pfichtner.vaadoo;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import org.approvaltests.core.Scrubber;

public class FirstSectionScrubber implements Scrubber {

    private static final Pattern COL0 = Pattern.compile("^\\S.*"); // starts in column 0 (non-whitespace)

	public String scrub(String input) {
		// split into lines preserving order
        List<String> lines = input.lines().collect(toList());

        // find index (0-based) of the second line that matches COL0
        int secondIndex = IntStream.range(0, lines.size()) //
            .filter(i -> COL0.matcher(lines.get(i)).matches()) //
            .limit(2) // we only need up to the first two matches
            .boxed() //
            .collect(toList()) //
            .stream() //
            .skip(1) // skip the first, keep the second
            .findFirst() //
            .orElse(-1) //
            ;

        if (secondIndex < 0) {
            // fewer than 2 top-level lines -> use original
            return input;
        }

        // stream from secondIndex to end and print (preserve original newline semantics)
        return IntStream.range(secondIndex, lines.size())
                .mapToObj(lines::get)
                .collect(joining(System.lineSeparator())) 
                // if original text ended with a newline, reproduce that:
                + (input.endsWith("\n") || input.endsWith("\r\n") ? System.lineSeparator() : "");
	}
	
}
