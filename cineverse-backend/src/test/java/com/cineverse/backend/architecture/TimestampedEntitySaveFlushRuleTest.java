package com.cineverse.backend.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.Test;

class TimestampedEntitySaveFlushRuleTest {

    /**
     * The actual guardrail: this is what fails CI if a future module repeats
     * the Movie/Cinema/Hall bug (an @Entity with a timestamp annotation
     * saved via save()/saveAll() instead of saveAndFlush()/saveAllAndFlush()).
     */
    @Test
    void productionCodeFlushesEveryTimestampedEntitySave() {
        JavaClasses productionCode = new ClassFileImporter().importPackages("com.cineverse.backend");

        TimestampedEntitySaveFlushRule.forClasses(productionCode).check(productionCode);
    }

    /**
     * Proves the rule itself actually works: run it against an isolated
     * fixture package (never scanned by the test above) containing one
     * violating service and one correct one, and assert it flags exactly
     * the violating one. If someone "fixes" the rule into a no-op, this is
     * what catches it.
     */
    @Test
    void ruleCatchesUnflushedSaveAndIgnoresCorrectUsage() {
        JavaClasses fixtureCode = new ClassFileImporter().importPackages("archtest.fixtures.badservice");

        EvaluationResult result = TimestampedEntitySaveFlushRule.forClasses(fixtureCode).evaluate(fixtureCode);

        assertThat(result.hasViolation())
                .as("rule should have flagged BadFixtureService.create()'s plain save()")
                .isTrue();

        String details = String.join("\n", result.getFailureReport().getDetails());
        assertThat(details)
                .contains("BadFixtureService")
                .contains("FixtureEntityRepository")
                .doesNotContain("GoodFixtureService");
    }
}
