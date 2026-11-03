package business;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public record NotificationSchedule(
        Integer scheduleId,
        boolean enabled,
        int daysMask,
        LocalTime time,
        String timezone,
        LocalDateTime checkedAtUtc,
        int taskCount,
        List<NotificationTask> tasks) {
}
