package ru.barakah.focus;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

final class PasswordCrypto {
    static String salt() { byte[] salt=new byte[16]; new SecureRandom().nextBytes(salt); return Base64.getEncoder().encodeToString(salt); }
    static String hash(char[] password,String salt) throws Exception {
        PBEKeySpec spec=new PBEKeySpec(password,Base64.getDecoder().decode(salt),210000,256);
        try { return Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded()); }
        finally { spec.clearPassword(); }
    }
    static boolean matches(char[] password,String salt,String expected) throws Exception {
        return MessageDigest.isEqual(Base64.getDecoder().decode(hash(password,salt)),Base64.getDecoder().decode(expected));
    }
    static char[] unknownSecret() {
        byte[] bytes=new byte[32]; new SecureRandom().nextBytes(bytes);
        char[] secret=new char[64]; char[] hex="0123456789abcdef".toCharArray();
        for(int i=0;i<bytes.length;i++) { secret[i*2]=hex[(bytes[i] & 255) >>> 4]; secret[i*2+1]=hex[bytes[i] & 15]; }
        java.util.Arrays.fill(bytes,(byte)0); return secret;
    }
}
