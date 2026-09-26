package com.torqline.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class LoggingNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationSender.class);

    @Override
    public void send(Channel channel, String recipient, String message) {
        log.info("[{}] -> {}: {}", channel, recipient, message);
    }
}
