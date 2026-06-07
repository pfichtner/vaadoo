package com.github.pfichtner.vaadoo.testclasses;

import jakarta.validation.constraints.NotNull;
import org.jmolecules.ddd.types.ValueObject;

public class RealClassWithLineNumbers implements ValueObject {
    public RealClassWithLineNumbers(@NotNull String param) {
        System.out.println(param);
    }
}
