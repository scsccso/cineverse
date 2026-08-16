package com.cineverse.backend.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cineverse.backend.booking.entity.Booking;
import com.cineverse.backend.booking.entity.BookingStatus;
import com.cineverse.backend.cinema.entity.Cinema;
import com.cineverse.backend.cinema.entity.Hall;
import com.cineverse.backend.movie.entity.Movie;
import com.cineverse.backend.movie.entity.MovieStatus;
import com.cineverse.backend.payment.dto.AdminPaymentResponse;
import com.cineverse.backend.payment.entity.Payment;
import com.cineverse.backend.payment.entity.PaymentStatus;
import com.cineverse.backend.payment.mapper.AdminPaymentMapper;
import com.cineverse.backend.payment.repository.PaymentRepository;
import com.cineverse.backend.showtime.entity.Showtime;
import com.cineverse.backend.user.entity.Role;
import com.cineverse.backend.user.entity.User;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit coverage for the five-hop assembly AdminPaymentMapper does (payment
 * -> booking -> user/showtime -> movie/hall) — the thing worth pinning down
 * without a database, same split of responsibility as
 * AdminBookingServiceTest/BookingServiceListTest. The real query and the
 * 401/403 gate live in AdminPaymentFlowIntegrationTest instead.
 */
@ExtendWith(MockitoExtension.class)
class AdminPaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    private AdminPaymentService adminPaymentService;

    @BeforeEach
    void setUp() {
        adminPaymentService = new AdminPaymentService(paymentRepository, new AdminPaymentMapper());
    }

    @Test
    void passesStatusFilterThroughAndAssemblesTheNestedBookingSummary() {
        Pageable pageable = PageRequest.of(0, 20);
        Payment payment = payment(PaymentStatus.ORPHANED_SUCCESS, "fan@example.com", "Dune", "Hall 2", BookingStatus.EXPIRED);
        when(paymentRepository.search(eq(PaymentStatus.ORPHANED_SUCCESS), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(payment), pageable, 1));

        Page<AdminPaymentResponse> result = adminPaymentService.search(PaymentStatus.ORPHANED_SUCCESS, pageable);

        verify(paymentRepository).search(eq(PaymentStatus.ORPHANED_SUCCESS), eq(pageable));
        assertThat(result.getContent()).hasSize(1);
        AdminPaymentResponse response = result.getContent().get(0);
        assertThat(response.id()).isEqualTo(payment.getId());
        assertThat(response.status()).isEqualTo(PaymentStatus.ORPHANED_SUCCESS);
        assertThat(response.amount()).isEqualByComparingTo("50.00");
        assertThat(response.stripeSessionId()).isEqualTo(payment.getStripeSessionId());
        assertThat(response.booking().id()).isEqualTo(payment.getBooking().getId());
        assertThat(response.booking().status()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(response.booking().userEmail()).isEqualTo("fan@example.com");
        assertThat(response.booking().movieTitle()).isEqualTo("Dune");
        assertThat(response.booking().hallName()).isEqualTo("Hall 2");
    }

    @Test
    void nullStatusBrowsesEverything() {
        Pageable pageable = PageRequest.of(0, 20);
        when(paymentRepository.search(isNull(), eq(pageable))).thenReturn(new PageImpl<>(List.of(), pageable, 0));

        adminPaymentService.search(null, pageable);

        verify(paymentRepository).search(isNull(), eq(pageable));
    }

    private Payment payment(
            PaymentStatus status, String userEmail, String movieTitle, String hallName, BookingStatus bookingStatus) {
        User user = new User(userEmail, "hash", Role.CUSTOMER, "Test Customer");
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());

        Movie movie = new Movie();
        movie.setTitle(movieTitle);
        movie.setDurationMinutes(120);
        movie.setStatus(MovieStatus.NOW_PLAYING);
        Cinema cinema = new Cinema("CineVerse Downtown", "1 Main St");
        Hall hall = new Hall(cinema, hallName, 6, 10);
        Showtime showtime = new Showtime(
                movie, hall, Instant.now(), Instant.now().plus(Duration.ofHours(2)), new BigDecimal("25.00"));
        ReflectionTestUtils.setField(showtime, "id", UUID.randomUUID());

        Booking booking = new Booking(user, showtime, new BigDecimal("50.00"), Instant.now().plus(Duration.ofMinutes(5)));
        ReflectionTestUtils.setField(booking, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(booking, "status", bookingStatus);

        Payment payment = new Payment(booking, "cs_test_" + UUID.randomUUID(), new BigDecimal("50.00"), "myr");
        ReflectionTestUtils.setField(payment, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(payment, "status", status);
        ReflectionTestUtils.setField(payment, "createdAt", Instant.now());
        ReflectionTestUtils.setField(payment, "updatedAt", Instant.now());
        return payment;
    }
}
