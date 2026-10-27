package business;

import org.mindrot.jbcrypt.BCrypt;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PasswordHasherTest {
    @Test
    public void generatedHashUsesTomcatCompatibleSaltIterationsCredentialFormat() {
        String password = "correct horse battery staple";
        String hash = PasswordHasher.hash(password);

        assertTrue(hash.matches("[0-9a-f]{32}\\$310000\\$[0-9a-f]{64}"));
        assertTrue(PasswordHasher.verify(password, hash));
        assertFalse(PasswordHasher.verify("a different password", hash));
    }

    @Test
    public void legacyBcryptHashCanBeVerifiedAndIsMarkedForUpgrade() {
        String password = "a legacy account password";
        String legacyHash = BCrypt.hashpw(password, BCrypt.gensalt(4));

        assertTrue(PasswordHasher.verify(password, legacyHash));
        assertTrue(PasswordHasher.needsUpgrade(legacyHash));
    }

    @Test
    public void malformedHashesDoNotVerify() {
        assertFalse(PasswordHasher.verify("password", "not-a-password-hash"));
        assertFalse(PasswordHasher.verify("password", "00$1$00"));
    }

    @Test
    public void documentedDevelopmentAdminCredentialMatchesSeededHash() {
        String hash = "3cbbc499f1b36ef13884b8de9ce78026$310000$"
                + "b40011e1c373026bb45869849f5ab7500a905014ba6f36cd61cede323351d65a";

        assertTrue(PasswordHasher.verify("LocalAdmin123!", hash));
    }
}
