package archtest.fixtures.badservice;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Deliberately mirrors the real bug pattern (Movie/Cinema/Hall all had this):
 * an @Entity with @CreationTimestamp whose Service saves it without
 * flushing. Lives in a standalone package so it's never picked up by the
 * real production rule scan of com.cineverse.backend — see
 * TimestampedEntitySaveFlushRuleTest for how it's used in isolation.
 */
@Entity
public class FixtureEntity {

    @Id
    private UUID id;

    @CreationTimestamp
    private Instant createdAt;
}
