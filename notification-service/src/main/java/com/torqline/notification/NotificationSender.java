package com.torqline.notification;

/** Delivery port. Swap the logging implementation for an SMS gateway or SES adapter in a real deployment. */
public interface NotificationSender {
    void send(Channel channel, String recipient, String message);
}
