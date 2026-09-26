package com.torqline.notification;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findAllByOrderBySentAtDesc(Pageable page);

    List<Notification> findByRecipientOrderBySentAtDesc(String recipient, Pageable page);
}
