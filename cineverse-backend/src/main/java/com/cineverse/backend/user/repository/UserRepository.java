package com.cineverse.backend.user.repository;

import com.cineverse.backend.user.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * Backs GET /api/v1/admin/users' optional email search. A derived query
     * method, not a hand-written {@code @Query} with a
     * {@code lower(concat(...))}-style pattern — Spring Data generates this
     * one through the same Criteria API path MovieSpecifications.
     * hasTitleContaining uses, so it isn't at risk of the Postgres
     * parameter-type-inference bug documented on BookingRepository.search;
     * that bug was specific to a placeholder appearing only inside a JPQL
     * string-function call, which neither Criteria-based mechanism produces.
     */
    Page<User> findByEmailContainingIgnoreCase(String emailFragment, Pageable pageable);
}
