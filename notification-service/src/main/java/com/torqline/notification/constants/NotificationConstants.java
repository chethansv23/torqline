package com.torqline.notification.constants;

import java.time.ZoneId;

public final class NotificationConstants {

    /** Service-manager inbox that receives stock alerts, e.g. manager+TQ-BLR-IND@torqline.dev */
    public static final String MANAGER_EMAIL_FORMAT = "manager+%s@torqline.dev";

    /** Times in customer messages are shown in IST because all seeded dealers are in Bengaluru. */
    public static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Kolkata");

    /** e.g. "Mon 7 Jan, 10:00 AM" */
    public static final String MESSAGE_DATE_TIME_PATTERN = "EEE d MMM, h:mm a";

    private NotificationConstants() {
    }
}
