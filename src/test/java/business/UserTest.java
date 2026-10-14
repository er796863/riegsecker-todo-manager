package business;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class UserTest {
    @Test
    public void noArgConstructorDefaultsRoleToUser() {
        User user = new User();

        assertEquals("USER", user.getRole());
    }

    @Test
    public void constructorAndSettersExposeUserValues() {
        User user = new User(3, "jdoe", "Jane", "Doe", "jane@example.com", "hash", "ADMIN");

        assertEquals(Integer.valueOf(3), user.getUserId());
        assertEquals("jdoe", user.getUserName());
        assertEquals("Jane", user.getFirstName());
        assertEquals("Doe", user.getLastName());
        assertEquals("jane@example.com", user.getEmail());
        assertEquals("hash", user.getPasswordHash());
        assertEquals("ADMIN", user.getRole());

        user.setRole("USER");
        assertEquals("USER", user.getRole());
    }
}
