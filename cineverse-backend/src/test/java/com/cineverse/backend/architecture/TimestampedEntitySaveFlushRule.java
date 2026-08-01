package com.cineverse.backend.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.AccessTarget.MethodCallTarget;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import java.util.Set;
import java.util.stream.Collectors;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Movie, Cinema, and Hall all shipped the same bug independently: an
 * {@code @Entity} with {@code @CreationTimestamp}/{@code @UpdateTimestamp}
 * saved via the plain {@code save()}, so the response built right after
 * carried a null/stale timestamp — Hibernate only populates those fields at
 * flush time. This rule makes that a build failure instead of something
 * someone has to notice by curling the API.
 *
 * <p>Repository lookup is by the project's own naming convention
 * ({@code Foo} entity -&gt; {@code FooRepository}), not generic-type
 * introspection — simpler, and matches how every repository in this
 * codebase is actually named.
 */
final class TimestampedEntitySaveFlushRule {

    private TimestampedEntitySaveFlushRule() {
    }

    static ArchRule forClasses(JavaClasses classes) {
        Set<String> repositoriesRequiringFlush = repositoriesRequiringFlush(classes);

        return classes()
                .should(new ArchCondition<JavaClass>(
                        "call saveAndFlush()/saveAllAndFlush(), not save()/saveAll(), on a repository "
                                + "whose entity has @CreationTimestamp or @UpdateTimestamp") {
                    @Override
                    public void check(JavaClass javaClass, ConditionEvents events) {
                        javaClass.getMethods().forEach(method -> method.getMethodCallsFromSelf().forEach(call -> {
                            if (isUnflushedSaveOnFlaggedRepository(call, repositoriesRequiringFlush)) {
                                events.add(SimpleConditionEvent.violated(call, String.format(
                                        "%s calls %s.%s(..) instead of %s(..) — %s has a timestamp field that "
                                                + "won't be populated until flush",
                                        call.getOrigin().getFullName(),
                                        call.getTarget().getOwner().getSimpleName(),
                                        call.getTarget().getName(),
                                        call.getTarget().getName().equals("save") ? "saveAndFlush" : "saveAllAndFlush",
                                        call.getTarget().getOwner().getSimpleName())));
                            }
                        }));
                    }
                });
    }

    private static Set<String> repositoriesRequiringFlush(JavaClasses classes) {
        return classes.stream()
                .filter(c -> c.isAnnotatedWith(Entity.class))
                .filter(TimestampedEntitySaveFlushRule::hasTimestampField)
                .map(c -> c.getSimpleName() + "Repository")
                .collect(Collectors.toSet());
    }

    private static boolean hasTimestampField(JavaClass entityClass) {
        return entityClass.getFields().stream()
                .anyMatch(f -> f.isAnnotatedWith(CreationTimestamp.class) || f.isAnnotatedWith(UpdateTimestamp.class));
    }

    private static boolean isUnflushedSaveOnFlaggedRepository(JavaMethodCall call, Set<String> flaggedRepositories) {
        MethodCallTarget target = call.getTarget();
        String methodName = target.getName();
        boolean isSingleEntitySave = methodName.equals("save") && target.getRawParameterTypes().size() == 1;
        boolean isBatchSave = methodName.equals("saveAll") && target.getRawParameterTypes().size() == 1;
        if (!isSingleEntitySave && !isBatchSave) {
            return false;
        }
        return flaggedRepositories.contains(target.getOwner().getSimpleName());
    }
}
