package business;

import org.junit.Test;
import org.mindrot.jbcrypt.BCrypt;

import static business.PasswordHasher.hash;
import static business.PasswordHasher.needsUpgrade;
import static business.PasswordHasher.verify;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PasswordHasherTest {
    @Test
    public void generatedHashUsesTomcatCompatibleSaltIterationsCredentialFormat() {
        String password = "correct horse battery staple";
        String hash = hash(password);

        assertTrue(hash.matches("[0-9a-f]{32}\\$310000\\$[0-9a-f]{64}"));
        assertTrue(verify(password, hash));
        assertFalse(verify("a different password", hash));
    }

    @Test
    public void legacyBcryptHashCanBeVerifiedAndIsMarkedForUpgrade() {
        String password = "a legacy account password";
        String legacyHash = BCrypt.hashpw(password, BCrypt.gensalt(4));

        assertTrue(verify(password, legacyHash));
        assertTrue(needsUpgrade(legacyHash));
    }

    @Test
    public void malformedHashesDoNotVerify() {
        assertFalse(verify("password", "not-a-password-hash"));
        assertFalse(verify("password", "00$1$00"));
    }

    @Test
    public void documentedDevelopmentAdminCredentialMatchesSeededHash() {
        String hash = "3cbbc499f1b36ef13884b8de9ce78026$310000$"
                + "b40011e1c373026bb45869849f5ab7500a905014ba6f36cd61cede323351d65a";

        assertTrue(verify("LocalAdmin123!", hash));
    }
}
