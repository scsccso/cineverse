package archtest.fixtures.badservice;

/** The correct counterpart, in the same fixture package, so the isolated
 * test also proves the rule does NOT flag correct usage (no false positive). */
public class GoodFixtureService {

    private final FixtureEntityRepository repository;

    public GoodFixtureService(FixtureEntityRepository repository) {
        this.repository = repository;
    }

    public FixtureEntity create(FixtureEntity entity) {
        return repository.saveAndFlush(entity);
    }
}
