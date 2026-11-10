package business;

import javax.naming.NamingException;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import static data.TodoDB.advanceNotificationCheck;
import static data.TodoDB.createScheduledNotification;
import static data.TodoDB.selectNotificationSchedule;

public final class NotificationService {
    private static final int MAX_TASKS_PER_NOTIFICATION = 10;

    private NotificationService() {
    }

    public static boolean processStartup(Integer userId, Instant now) throws NamingException, SQLException {
        NotificationSchedule schedule = selectNotificationSchedule(userId);

        if (schedule == null) {
            return false;
        }

        if (!schedule.enabled() || schedule.taskCount() == 0) {
            advanceNotificationCheck(userId);
            return false;
        }

        ZoneId zone = ZoneId.of(schedule.timezone());
        ZonedDateTime scheduledAt = latestDueOccurrence(
                now, schedule.daysMask(), schedule.time(), zone,
                schedule.checkedAtUtc().toInstant(ZoneOffset.UTC));

        if (scheduledAt == null) {
            return false;
        }

        List<NotificationTask> tasks = tasksForNotification(schedule.tasks());
        return createScheduledNotification(
                userId,
                schedule.scheduleId(),
                scheduledAt.toLocalDate(),
                schedule.time(),
                schedule.timezone(),
                tasks,
                schedule.taskCount() - tasks.size());
    }

    static List<NotificationTask> tasksForNotification(List<NotificationTask> tasks) {
        return tasks.stream().limit(MAX_TASKS_PER_NOTIFICATION).toList();
    }

    static ZonedDateTime latestDueOccurrence(Instant now, int daysMask,
            LocalTime time, ZoneId zone, Instant after) {
        ZonedDateTime localNow = now.atZone(zone);
        LocalDate today = localNow.toLocalDate();

        for (int daysBack = 0; daysBack < 7; daysBack++) {
            LocalDate candidateDate = today.minusDays(daysBack);
            int dayBit = 1 << (candidateDate.getDayOfWeek().getValue() - 1);

            if ((daysMask & dayBit) == 0) {
                continue;
            }

            ZonedDateTime candidate = candidateDate.atTime(time).atZone(zone);

            if (!candidate.toInstant().isAfter(now) && candidate.toInstant().isAfter(after)) {
                return candidate;
            }
        }

        return null;
    }
}
