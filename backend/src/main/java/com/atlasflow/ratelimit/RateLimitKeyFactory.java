package com.atlasflow.ratelimit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

public final class RateLimitKeyFactory {

    private  RateLimitKeyFactory() {
    }

    public static String login (String ip, String email){

        String normalizedEmail = email == null
                ? ""
                : email.trim().toLowerCase(Locale.ROOT);

        String subject = ip + "|" + normalizedEmail;

        return "atlasflow:rl:login:"
                + sha256(subject)
                +":v1";

    }

    public static String submitJob(UUID projectId){
        return "atlasflow:rl:job:"
                + projectId
                +":v1";
    }

    private static String sha256(String value){
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 algorithm is not available",
                    exception
            );
        }
    }
}
