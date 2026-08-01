package archtest.fixtures.badservice;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FixtureEntityRepository extends JpaRepository<FixtureEntity, UUID> {
}
