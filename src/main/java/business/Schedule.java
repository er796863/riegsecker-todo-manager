package business;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;

public class Schedule implements Serializable {
    private String notificationSettings;
    private boolean notificationsEnabled;
    private List<String> notificationDays = new ArrayList<>();
    private String notificationTime;
    private String notificationTimezone;
    private Map<Integer, Task> tasks;
    private User user;

    public Schedule() {
    }

    public Schedule(String notificationSettings, Map<Integer, Task> tasks, User user) {
        this(notificationSettings,
                notificationSettings != null && !"NONE".equalsIgnoreCase(notificationSettings),
                defaultNotificationDays(notificationSettings), "09:00", "UTC", tasks, user);
    }

    public Schedule(String notificationSettings, boolean notificationsEnabled, List<String> notificationDays,
            String notificationTime, String notificationTimezone, Map<Integer, Task> tasks, User user) {
        this.notificationSettings = notificationSettings;
        this.notificationsEnabled = notificationsEnabled;
        this.notificationDays = notificationDays;
        this.notificationTime = notificationTime;
        this.notificationTimezone = notificationTimezone;
        this.tasks = tasks;
        this.user = user;
    }

    private static List<String> defaultNotificationDays(String settings) {
        if ("WEEKLY".equalsIgnoreCase(settings)) {
            return List.of("MONDAY");
        }
        return List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY");
    }

    public String getNotificationSettings() {
        return notificationSettings;
    }

    public void setNotificationSettings(String notificationSettings) {
        this.notificationSettings = notificationSettings;
    }

    public boolean isNotificationsEnabled() {
        return notificationsEnabled;
    }

    public void setNotificationsEnabled(boolean notificationsEnabled) {
        this.notificationsEnabled = notificationsEnabled;
    }

    public List<String> getNotificationDays() {
        return notificationDays;
    }

    public void setNotificationDays(List<String> notificationDays) {
        this.notificationDays = notificationDays;
    }

    public String getNotificationTime() {
        return notificationTime;
    }

    public void setNotificationTime(String notificationTime) {
        this.notificationTime = notificationTime;
    }

    public String getNotificationTimezone() {
        return notificationTimezone;
    }

    public void setNotificationTimezone(String notificationTimezone) {
        this.notificationTimezone = notificationTimezone;
    }

    public Map<Integer, Task> getTasks() {
        return tasks;
    }

    public void setTasks(Map<Integer, Task> tasks) {
        this.tasks = tasks;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}
