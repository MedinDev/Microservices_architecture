package com.microservices.common.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class FieldEncryptionService {

    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_NONCE_LENGTH = 12;

    private final byte[] keyBytes;
    private final SecureRandom secureRandom = new SecureRandom();

    public FieldEncryptionService(@Value("${app.security.data.encryption-key:0123456789abcdef0123456789abcdef}") String key) {
        this.keyBytes = normalizeKey(key);
    }

    public String encrypt(String plainText) {
        if (!StringUtils.hasText(plainText)) {
            return plainText;
        }
        if (plainText.startsWith("enc:")) {
            return plainText;
        }
        try {
            byte[] nonce = new byte[GCM_NONCE_LENGTH];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new GCMParameterSpec(GCM_TAG_LENGTH, nonce));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(nonce.length + cipherText.length);
            buffer.put(nonce);
            buffer.put(cipherText);
            return "enc:" + Base64.getEncoder().encodeToString(buffer.array());
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to encrypt field", ex);
        }
    }

    public String decrypt(String encryptedValue) {
        if (!StringUtils.hasText(encryptedValue) || !encryptedValue.startsWith("enc:")) {
            return encryptedValue;
        }
        try {
            byte[] payload = Base64.getDecoder().decode(encryptedValue.substring(4));
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] nonce = new byte[GCM_NONCE_LENGTH];
            buffer.get(nonce);
            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new GCMParameterSpec(GCM_TAG_LENGTH, nonce));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return encryptedValue;
        }
    }

    private byte[] normalizeKey(String key) {
        byte[] source = key.getBytes(StandardCharsets.UTF_8);
        byte[] normalized = new byte[32];
        for (int i = 0; i < normalized.length; i++) {
            normalized[i] = source[i % source.length];
        }
        return normalized;
    }
}
