package com.example.tracker.repository;

import com.example.tracker.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findByRecipient(String recipient, Pageable pageable);
    Page<Notification> findByRecipientAndReadFalse(String recipient, Pageable pageable);
    long countByRecipientAndReadFalse(String recipient);
    Optional<Notification> findByIdAndRecipient(Long id, String recipient);

    @Modifying
    @Query("update Notification n set n.read = true where n.recipient = :recipient and n.read = false")
    int markAllRead(@Param("recipient") String recipient);
}
