package business;

import java.util.List;

public record InAppNotification(
        Integer notificationId,
        String scheduledDate,
        String scheduledTime,
        String notificationTimezone,
        boolean read,
        int additionalTaskCount,
        List<NotificationTask> tasks) {
}
