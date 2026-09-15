package dev.fincore.configuration.api;

import dev.fincore.configuration.domain.RoundingMode;
import dev.fincore.configuration.domain.Source;
import java.util.List;
import java.util.UUID;

public record SourceResponse(
        UUID id,
        String code,
        String name,
        String timezone,
        String decimalSeparator,
        String thousandsSeparator,
        List<String> dateFormats,
        RoundingMode roundingMode,
        boolean active) {

    public static SourceResponse from(Source source) {
        return new SourceResponse(
                source.id(), source.code(), source.name(), source.timezone(), source.decimalSeparator(),
                source.thousandsSeparator(), source.dateFormats(), source.roundingMode(), source.active());
    }
}
