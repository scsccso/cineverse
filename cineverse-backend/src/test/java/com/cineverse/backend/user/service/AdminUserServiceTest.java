package com.cineverse.backend.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cineverse.backend.booking.repository.BookingRepository;
import com.cineverse.backend.user.dto.UpdateUserRoleRequest;
import com.cineverse.backend.user.entity.Role;
import com.cineverse.backend.user.entity.User;
import com.cineverse.backend.user.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Unit coverage for the admin user management guard rails
 * (GET/PATCH/DELETE /api/v1/admin/users). The self-lockout checks are the
 * part most worth pinning down here: reproduced live during review that
 * PATCHing an ADMIN's own id to CUSTOMER succeeded with zero server-side
 * pushback (only a disabled frontend button stood in the way), leaving the
 * system with no ADMIN account at all and requiring a direct DB fix to
 * recover — see CLAUDE.md "Admin 用户管理". These tests lock the 409 in at
 * the service layer so a future refactor can't silently drop it.
 */
@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private BookingRepository bookingRepository;

    private AdminUserService adminUserService;

    private final UUID callerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        adminUserService = new AdminUserService(userRepository, bookingRepository);
    }

    @Test
    void updateUserRoleRejectsChangingYourOwnRoleWithoutTouchingTheRepository() {
        UpdateUserRoleRequest request = new UpdateUserRoleRequest(Role.CUSTOMER);

        assertThatThrownBy(() -> adminUserService.updateUserRole(callerId, request, callerId))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getReason()).isEqualTo("Cannot change your own role.");
                });

        // The guard must fire before any lookup — self-lockout is caught by
        // id equality alone, not by loading the (already-known-to-exist) caller.
        verifyNoInteractions(userRepository);
    }

    @Test
    void updateUserRoleFlushesImmediatelyInsteadOfPlainSave() {
        UUID targetId = UUID.randomUUID();
        User target = user(targetId, "customer@example.com", Role.CUSTOMER);
        UpdateUserRoleRequest request = new UpdateUserRoleRequest(Role.ADMIN);
        when(userRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(userRepository.saveAndFlush(target)).thenReturn(target);

        User result = adminUserService.updateUserRole(targetId, request, callerId);

        assertThat(result.getRole()).isEqualTo(Role.ADMIN);
        // TimestampedEntitySaveFlushRuleTest (architecture package) is what
        // actually guards "never plain save() on a timestamped entity"
        // project-wide — not duplicated here as a never()-on-save() check,
        // because ArchUnit's ClassFileImporter sweeps test bytecode too, and
        // a verify(..., never()).save(...) call is itself a call site the
        // rule would flag.
        verify(userRepository).saveAndFlush(target);
    }

    @Test
    void updateUserRoleRejectsAnUnknownUserId() {
        UUID targetId = UUID.randomUUID();
        UpdateUserRoleRequest request = new UpdateUserRoleRequest(Role.ADMIN);
        when(userRepository.findById(targetId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserService.updateUserRole(targetId, request, callerId))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void deleteUserRejectsDeletingYourOwnAccountWithoutTouchingEitherRepository() {
        assertThatThrownBy(() -> adminUserService.deleteUser(callerId, callerId))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getReason()).isEqualTo("Cannot delete your own account.");
                });

        // Same reasoning as the role-change guard: id equality alone is
        // enough to reject, so this must short-circuit before either the user
        // lookup or the booking-ownership check ever runs.
        verifyNoInteractions(userRepository);
        verifyNoInteractions(bookingRepository);
    }

    @Test
    void deleteUserRemovesAUserWithNoBookings() {
        UUID targetId = UUID.randomUUID();
        User target = user(targetId, "customer@example.com", Role.CUSTOMER);
        when(userRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(bookingRepository.existsByUserId(targetId)).thenReturn(false);

        adminUserService.deleteUser(targetId, callerId);

        verify(userRepository).delete(target);
    }

    @Test
    void deleteUserRejectsAUserWithExistingBookings() {
        UUID targetId = UUID.randomUUID();
        User target = user(targetId, "customer@example.com", Role.CUSTOMER);
        when(userRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(bookingRepository.existsByUserId(targetId)).thenReturn(true);

        assertThatThrownBy(() -> adminUserService.deleteUser(targetId, callerId))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        verify(userRepository, never()).delete(any());
    }

    @Test
    void deleteUserRejectsAnUnknownUserId() {
        UUID targetId = UUID.randomUUID();
        when(userRepository.findById(targetId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserService.deleteUser(targetId, callerId))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        verifyNoInteractions(bookingRepository);
    }

    private User user(UUID id, String email, Role role) {
        User user = new User(email, "hash", role, "Test User");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
