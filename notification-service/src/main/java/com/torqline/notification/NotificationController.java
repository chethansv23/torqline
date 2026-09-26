package com.torqline.notification;

import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationRepository notifications;

    public NotificationController(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public List<Notification> list(@RequestParam(required = false) String recipient,
                                   @RequestParam(defaultValue = "50") int limit) {
        PageRequest page = PageRequest.of(0, Math.min(Math.max(limit, 1), 200));
        return recipient == null
                ? notifications.findAllByOrderBySentAtDesc(page)
                : notifications.findByRecipientOrderBySentAtDesc(recipient, page);
    }
}
