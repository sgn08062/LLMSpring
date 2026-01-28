package com.example.LlmSpring.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class EncryptionUtil {

    // application.properties에서 비밀키를 가져옵니다.
    @Value("${encryption.secret-key}")
    private String secretKey;

    private static final String ALG = "AES/CBC/PKCS5Padding";

    // 암호화
    public String encrypt(String plainText) {
        try {
            // 키 길이를 32바이트(256비트)로 맞춤
            SecretKeySpec keySpec = new SecretKeySpec(getFixedKey(), "AES");
            // 초기화 벡터(IV)는 보안상 랜덤이 좋으나, 구현 단순화를 위해 키의 일부를 사용 (실무에선 랜덤+prefix 추천)
            IvParameterSpec ivParamSpec = new IvParameterSpec(secretKey.substring(0, 16).getBytes());

            Cipher cipher = Cipher.getInstance(ALG);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivParamSpec);

            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("암호화 실패", e);
        }
    }

    // 복호화
    public String decrypt(String encryptedText) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(getFixedKey(), "AES");
            IvParameterSpec ivParamSpec = new IvParameterSpec(secretKey.substring(0, 16).getBytes());

            Cipher cipher = Cipher.getInstance(ALG);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivParamSpec);

            byte[] decodedBytes = Base64.getDecoder().decode(encryptedText);
            byte[] decrypted = cipher.doFinal(decodedBytes);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("복호화 실패", e);
        }
    }

    // 키 길이를 32바이트로 맞추는 헬퍼 메소드
    private byte[] getFixedKey() {
        byte[] keyBytes = new byte[32];
        byte[] sourceBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(sourceBytes, 0, keyBytes, 0, Math.min(sourceBytes.length, keyBytes.length));
        return keyBytes;
    }
}