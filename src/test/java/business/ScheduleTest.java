package business;

import org.junit.Test;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ScheduleTest {
    @Test
    public void storesNotificationSettingsUserAndTasks() {
        User user = new User(3, "jdoe", "Jane", "Doe", "jane@example.com", "hash", "USER");
        Task task = new Task();
        Map<Integer, Task> tasks = Map.of(12, task);
        Schedule schedule = new Schedule("daily", tasks, user);

        assertEquals("daily", schedule.getNotificationSettings());
        assertSame(tasks, schedule.getTasks());
        assertSame(user, schedule.getUser());

        schedule.setNotificationSettings("weekly");
        schedule.setTasks(Map.of());
        schedule.setUser(new User());

        assertEquals("weekly", schedule.getNotificationSettings());
        assertEquals(Map.of(), schedule.getTasks());
    }

    @Test
    public void storesWeekdaysLocalTimeAndTimeZone() {
        Schedule schedule = new Schedule("WEEKLY", true,
                java.util.List.of("MONDAY", "WEDNESDAY"), "08:30", "America/Denver",
                Map.of(), new User());

        assertEquals(java.util.List.of("MONDAY", "WEDNESDAY"), schedule.getNotificationDays());
        assertEquals("08:30", schedule.getNotificationTime());
        assertEquals("America/Denver", schedule.getNotificationTimezone());
        assertTrue(schedule.isNotificationsEnabled());
    }
}
