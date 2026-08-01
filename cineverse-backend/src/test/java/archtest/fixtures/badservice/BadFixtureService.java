package archtest.fixtures.badservice;

/** The violation the rule must catch: create() uses save(), not saveAndFlush(). */
public class BadFixtureService {

    private final FixtureEntityRepository repository;

    public BadFixtureService(FixtureEntityRepository repository) {
        this.repository = repository;
    }

    public FixtureEntity create(FixtureEntity entity) {
        return repository.save(entity);
    }
}
