package iloveshopping.demo.shared;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Encrypts confidential persisted text. Configure DATA_ENCRYPTION_KEY outside source control in production. */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {
    private static final byte[] KEY = key();
    private static final byte[] LEGACY_IV = "shop-order-1".getBytes(StandardCharsets.UTF_8);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String V2_PREFIX = "v2.";
    @Override public String convertToDatabaseColumn(String value) {
        if (value == null) return null;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(KEY, "AES"), new GCMParameterSpec(128, iv));
            return V2_PREFIX + Base64.getEncoder().encodeToString(iv) + "." + Base64.getEncoder().encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new IllegalStateException("Could not encrypt sensitive data", ex); }
    }
    @Override public String convertToEntityAttribute(String value) {
        if (value == null) return null;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            if (value.startsWith(V2_PREFIX)) {
                String[] parts = value.split("\\.", 3);
                cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(KEY, "AES"), new GCMParameterSpec(128, Base64.getDecoder().decode(parts[1])));
                return new String(cipher.doFinal(Base64.getDecoder().decode(parts[2])), StandardCharsets.UTF_8);
            }
            // Existing development data used a fixed IV. Keep it readable while all new values use a unique IV.
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(KEY, "AES"), new GCMParameterSpec(128, LEGACY_IV));
            return new String(cipher.doFinal(Base64.getDecoder().decode(value)), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            // Compatibility for pre-encryption development rows. New writes are always encrypted.
            return value;
        }
    }
    private static byte[] key() {
        try { return MessageDigest.getInstance("SHA-256").digest(System.getenv().getOrDefault("DATA_ENCRYPTION_KEY", "development-only-change-me").getBytes(StandardCharsets.UTF_8)); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
}
