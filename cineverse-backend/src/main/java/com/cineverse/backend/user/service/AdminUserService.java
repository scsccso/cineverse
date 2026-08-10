package com.cineverse.backend.user.service;

import com.cineverse.backend.booking.repository.BookingRepository;
import com.cineverse.backend.user.dto.UpdateUserRoleRequest;
import com.cineverse.backend.user.entity.User;
import com.cineverse.backend.user.repository.UserRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;

    public AdminUserService(UserRepository userRepository, BookingRepository bookingRepository) {
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
    }

    @Transactional(readOnly = true)
    public Page<User> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    @Transactional
    public User updateUserRole(UUID id, UpdateUserRoleRequest request, UUID callerId) {
        if (id.equals(callerId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot change your own role.");
        }

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        user.setRole(request.role());
        return userRepository.saveAndFlush(user);
    }

    @Transactional
    public void deleteUser(UUID id, UUID callerId) {
        if (id.equals(callerId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot delete your own account.");
        }

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (bookingRepository.existsByUserId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot delete user: user has existing bookings.");
        }

        userRepository.delete(user);
    }
}
