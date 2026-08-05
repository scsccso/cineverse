package com.cineverse.backend.report.config;

import com.cineverse.backend.report.dto.ReportGranularity;
import java.util.Locale;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Spring's default enum request-param binding is case-sensitive
 * (Enum.valueOf), but the documented API shape is lowercase
 * (?granularity=day|week|month, matching Postgres's own date_trunc
 * vocabulary) — this makes ?granularity=day and ?granularity=DAY both bind,
 * rather than forcing the URL to spell out the Java enum constant name.
 * Auto-registered into the web ConversionService: any Converter bean in the
 * context is picked up by Spring Boot's WebMvcAutoConfiguration.
 */
@Component
public class ReportGranularityConverter implements Converter<String, ReportGranularity> {

    @Override
    public ReportGranularity convert(String source) {
        return ReportGranularity.valueOf(source.toUpperCase(Locale.ROOT));
    }
}
