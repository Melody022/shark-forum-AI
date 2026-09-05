package com.itswy.paicodingai.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 轻量 API Key 加密服务。生产环境必须设置 MODEL_PROVIDER_SECRET_KEY。
 */
@Service
public class ModelProviderSecretService {

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public ModelProviderSecretService(@Value("${model-provider.security.secret-key:${MODEL_PROVIDER_SECRET_KEY:dev-only-change-me}}") String secret) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(bytes, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("初始化模型配置加密服务失败", e);
        }
    }

    public String encrypt(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(raw.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(iv) + ":"
                    + Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new IllegalStateException("模型 API Key 加密失败", e);
        }
    }

    public String decrypt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            String[] parts = value.split(":", 2);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, Base64.getDecoder().decode(parts[0])));
            return new String(cipher.doFinal(Base64.getDecoder().decode(parts[1])), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("模型 API Key 解密失败", e);
        }
    }

    public String mask(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (value.length() <= 8) {
            return "****";
        }
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }
}
