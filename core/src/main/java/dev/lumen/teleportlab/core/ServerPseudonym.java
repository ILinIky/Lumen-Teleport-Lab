package dev.lumen.teleportlab.core;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;

/** Secret-key HMAC avoids an unsalted address dictionary attack. Never export the key. */
public final class ServerPseudonym {
    private final byte[] key;
    public ServerPseudonym(byte[] key) {
        if (key.length < 32) throw new IllegalArgumentException("At least 256 secret bits required");
        this.key = key.clone();
    }
    public static byte[] newKey() { byte[] key = new byte[32]; new SecureRandom().nextBytes(key); return key; }
    public String identify(String address) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return "srv_" + HexFormat.of().formatHex(mac.doFinal(address.trim().toLowerCase(Locale.ROOT)
                    .getBytes(StandardCharsets.UTF_8))).substring(0, 24);
        } catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
    }
}
