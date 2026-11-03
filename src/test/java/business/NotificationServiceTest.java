package business;

import org.junit.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class NotificationServiceTest {
    @Test
    public void selectsLatestSelectedOccurrenceThatHasPassedSinceLastCheck() {
        ZoneId zone = ZoneId.of("America/Denver");
        Instant now = Instant.parse("2026-11-04T17:00:00Z");
        Instant checkedAt = Instant.parse("2026-11-02T16:00:00Z");

        ZonedDateTime occurrence = NotificationService.latestDueOccurrence(
                now, (1 << 0) | (1 << 2), LocalTime.of(9, 0), zone, checkedAt);

        assertEquals(Instant.parse("2026-11-04T16:00:00Z"), occurrence.toInstant());
    }

    @Test
    public void doesNotReturnAnOccurrenceThatWasAlreadyCheckedOrIsInTheFuture() {
        ZoneId zone = ZoneId.of("America/Denver");
        Instant now = Instant.parse("2026-11-03T15:00:00Z");
        Instant checkedAt = Instant.parse("2026-11-02T16:00:00Z");

        assertNull(NotificationService.latestDueOccurrence(
                now, 1 << 1, LocalTime.of(9, 0), zone, checkedAt));
    }

    @Test
    public void shiftsNonexistentDaylightSavingTimeForwardByTheGap() {
        ZoneId zone = ZoneId.of("America/New_York");
        Instant now = Instant.parse("2026-03-08T07:45:00Z");
        Instant checkedAt = Instant.parse("2026-03-08T05:00:00Z");

        ZonedDateTime occurrence = NotificationService.latestDueOccurrence(
                now, 1 << 6, LocalTime.of(2, 30), zone, checkedAt);

        assertEquals(Instant.parse("2026-03-08T07:30:00Z"), occurrence.toInstant());
    }

    @Test
    public void usesEarlierOffsetDuringDaylightSavingOverlap() {
        ZoneId zone = ZoneId.of("America/New_York");
        Instant now = Instant.parse("2026-11-01T06:00:00Z");
        Instant checkedAt = Instant.parse("2026-11-01T04:00:00Z");

        ZonedDateTime occurrence = NotificationService.latestDueOccurrence(
                now, 1 << 6, LocalTime.of(1, 30), zone, checkedAt);

        assertEquals(Instant.parse("2026-11-01T05:30:00Z"), occurrence.toInstant());
    }

    @Test
    public void limitsEachNotificationToTenTasks() {
        List<NotificationTask> tasks = IntStream.rangeClosed(1, 12)
                .mapToObj(id -> new NotificationTask(id, "Task " + id, null))
                .toList();

        List<NotificationTask> selected = NotificationService.tasksForNotification(tasks);

        assertEquals(10, selected.size());
        assertEquals(2, tasks.size() - selected.size());
    }
}
