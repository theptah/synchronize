package net.ptah.synchronize.util;

import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class CryptoUtil {
   private static final SecureRandom RANDOM = new SecureRandom();
   private static final int GCM_IV_LENGTH = 12;
   private static final int GCM_TAG_LENGTH = 128;

   public static byte[] generateAesKey() throws Exception {
      KeyGenerator keyGen = KeyGenerator.getInstance("AES");
      keyGen.init(256, RANDOM);
      return keyGen.generateKey().getEncoded();
   }

   public static byte[] generateIv() {
      byte[] iv = new byte[12];
      RANDOM.nextBytes(iv);
      return iv;
   }

   public static Cipher getEncryptionCipher(byte[] keyBytes, byte[] iv) throws Exception {
      SecretKey secretKey = new SecretKeySpec(keyBytes, "AES");
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(1, secretKey, new GCMParameterSpec(128, iv));
      return cipher;
   }

   public static Cipher getDecryptionCipher(byte[] keyBytes, byte[] iv) throws Exception {
      SecretKey secretKey = new SecretKeySpec(keyBytes, "AES");
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(2, secretKey, new GCMParameterSpec(128, iv));
      return cipher;
   }
}
