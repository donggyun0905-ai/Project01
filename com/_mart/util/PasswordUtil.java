package com._mart.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * 비밀번호를 SHA-256으로 해시한다. java.security 표준 API만 사용 (외부 라이브러리 불필요).
 * 참고: 실무에서는 salt가 포함되고 느리게 설계된 BCrypt/Argon2를 더 권장하지만,
 * 표준 라이브러리만으로 "평문 저장은 피한다"는 최소한의 안전장치로 SHA-256을 사용한다.
 */
public class PasswordUtil {

    public static String hash(String plainText) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(plainText.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256은 모든 JVM에 기본 내장되어 있어 실제로는 발생하지 않는다.
            throw new RuntimeException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }

    public static boolean matches(String plainText, String hashed) {
        return hash(plainText).equals(hashed);
    }
}
