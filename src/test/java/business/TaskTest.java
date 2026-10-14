package business;

import org.junit.Test;

import java.time.LocalDateTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TaskTest {
    @Test
    public void constructorAndSettersExposeTaskValues() {
        LocalDateTime created = LocalDateTime.of(2026, 10, 14, 9, 30);
        LocalDateTime due = created.plusDays(2);
        Task task = new Task(7, "Review", "Review the test setup", created, due);

        assertEquals(Integer.valueOf(7), task.getTaskId());
        assertEquals("Review", task.getTaskTitle());
        assertEquals("Review the test setup", task.getTaskDescription());
        assertEquals(created, task.getTaskDateCreated());
        assertEquals(due, task.getTaskDateDue());

        task.setTaskId(8);
        task.setTaskTitle("Updated");
        task.setTaskDescription("Updated description");
        task.setTaskDateCreated(due);
        task.setTaskDateDue(created);

        assertEquals(Integer.valueOf(8), task.getTaskId());
        assertEquals("Updated", task.getTaskTitle());
        assertEquals("Updated description", task.getTaskDescription());
        assertEquals(due, task.getTaskDateCreated());
        assertEquals(created, task.getTaskDateDue());
    }

    @Test
    public void noArgConstructorLeavesOptionalValuesNull() {
        Task task = new Task();

        assertNull(task.getTaskId());
        assertNull(task.getTaskDateDue());
    }
}
