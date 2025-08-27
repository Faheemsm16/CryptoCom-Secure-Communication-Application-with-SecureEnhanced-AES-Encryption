package com.example.cryptochat;

import android.content.Context;
import android.util.Base64;
import android.widget.Toast;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AES {

    private SecretKey secretKey;
    private Cipher cipher, deCipher;
    private SecretKey whiteningKey;

    public AES(Context context) {
        try {
            // load the key securely
            secretKey = loadKey();

            // load the whitening key securely
            whiteningKey = loadWhiteningKey();

            // Use CBC mode with PKCS7Padding
            cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
            deCipher = Cipher.getInstance("AES/CBC/PKCS7Padding");

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, "Encryption Error!", Toast.LENGTH_SHORT).show();
        }
    }

    private SecretKey loadKey() throws NoSuchAlgorithmException {
        // Use a consistent key for simplicity (not recommended for production)
        byte[] encryptionKey = {
                9, 115, 51, 86, 105, 4, -31, -23, 89 , -83, -35, 98, -123, 20, 43, -128,
                -55, -43, 45, -63, 87, 93, 69, -48, -68, 88, 17, 20, 3, -105, 119, -53};

        return new SecretKeySpec(encryptionKey, "AES");
    }

    private SecretKey loadWhiteningKey() throws NoSuchAlgorithmException {
        // Use a different consistent key for whitening
        byte[] whiteningKeyBytes = {
                -15, 62, 74, -43, 27, 95, -118, -23, 39, -9, -74, 57, 11, 74, 2, -46};

        return new SecretKeySpec(whiteningKeyBytes, "AES");
    }

    public String Encrypt(String string, Context context) {
        byte[] stringByte = string.getBytes();
        byte[] encryptedByte;

        try {
            // Apply SPN transformation
            byte[] spnTransformedByte = applySPNTransformation(stringByte);

            // Apply XOR transformation with whitening key
            byte[] whitenedInput = applyXORTransformation(spnTransformedByte, whiteningKey.getEncoded());

            // Generate a random IV for each encryption
            byte[] iv = generateRandomIV();
            IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);

            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParameterSpec);
//            encryptedByte = cipher.doFinal(transformedByte);
            encryptedByte = cipher.doFinal(whitenedInput);

            // Combine IV and ciphertext and encode in Base64
            byte[] combined = new byte[iv.length + encryptedByte.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encryptedByte, 0, combined, iv.length, encryptedByte.length);

            return Base64.encodeToString(combined, Base64.DEFAULT);

        } catch (InvalidKeyException | BadPaddingException | IllegalBlockSizeException e) {
            e.printStackTrace();
            Toast.makeText(context, "Encryption Error!", Toast.LENGTH_SHORT).show();
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, "Encryption Error!", Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    public String Decrypt(String string, Context context) {
        try {
            // Decode Base64
            byte[] combined = Base64.decode(string, Base64.DEFAULT);

            // Extract IV and ciphertext
            byte[] iv = new byte[cipher.getBlockSize()];
            byte[] encryptedByte = new byte[combined.length - iv.length];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            System.arraycopy(combined, iv.length, encryptedByte, 0, encryptedByte.length);

            IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);

            deCipher.init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpec);
            byte[] decryption = deCipher.doFinal(encryptedByte);

            // Apply XOR transformation with whitening key to reverse whitening
            byte[] whitenedOutput = applyXORTransformation(decryption, whiteningKey.getEncoded());

            // Reverse SPN transformation
            byte[] spnReversedByte = reverseSPNTransformation(whitenedOutput);

            return new String(spnReversedByte);

        } catch (InvalidKeyException | BadPaddingException | IllegalBlockSizeException e) {
            e.printStackTrace();
            Toast.makeText(context, "Decryption Error!", Toast.LENGTH_SHORT).show();
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, "Decryption Error!", Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    private byte[] generateRandomIV() {
        // Generate a random IV
        SecureRandom secureRandom = new SecureRandom();
        byte[] iv = new byte[cipher.getBlockSize()];
        secureRandom.nextBytes(iv);
        return iv;
    }

    private byte[] applySPNTransformation(byte[] input) {
        // Apply substitution box transformation
        byte[] substituted = new byte[input.length];
        for (int i = 0; i < input.length; i++) {
            substituted[i] = substitutionBox(input[i]);
        }

        // Apply permutation layer
        byte[] permuted = permutationLayer(substituted);

        return permuted;
    }

    private byte[] reverseSPNTransformation(byte[] input) {
        // Reverse the permutation step
        byte[] result = reversePermutationLayer(input);

        // Reverse the more complex substitution-permutation network
        for (int i = 0; i < result.length; i++) {
            result[i] = reverseSubstitutionBox(result[i]);
        }

        return result;
    }

    private byte[] applyXORTransformation(byte[] input, byte[] key) {
        // Apply XOR transformation with a key
        byte[] result = new byte[input.length];
        for (int i = 0; i < input.length; i++) {
            result[i] = (byte) (input[i] ^ key[i % key.length]);
        }
        return result;
    }

    // Add more values to complete the substitution box
    private int[] sBox = {
            0x63, 0x7C, 0x77, 0x7B, 0xF2, 0x6B, 0x6F, 0xC5,
            0x30, 0x01, 0x67, 0x2B, 0xFE, 0xD7, 0xAB, 0x76,
            0xCA, 0x82, 0xC9, 0x7D, 0xFA, 0x59, 0x47, 0xF0,
            0xAD, 0xD4, 0xA2, 0xAF, 0x9C, 0xA4, 0x72, 0xC0,
            0xB7, 0xFD, 0x93, 0x26, 0x36, 0x3F, 0xF7, 0xCC,
            0x34, 0xA5, 0xE5, 0xF1, 0x71, 0xD8, 0x31, 0x15,
            0x04, 0xC7, 0x23, 0xC3, 0x18, 0x96, 0x05, 0x9A,
            0x07, 0x12, 0x80, 0xE2, 0xEB, 0x27, 0xB2, 0x75,
            0x09, 0x83, 0x2C, 0x1A, 0x1B, 0x6E, 0x5A, 0xA0,
            0x52, 0x3B, 0xD6, 0xB3, 0x29, 0xE3, 0x2F, 0x84,
            0x53, 0xD1, 0x00, 0xED, 0x20, 0xFC, 0xB1, 0x5B,
            0x6A, 0xCB, 0xBE, 0x39, 0x4A, 0x4C, 0x58, 0xCF,
            0xD0, 0xEF, 0xAA, 0xFB, 0x43, 0x4D, 0x33, 0x85,
            0x45, 0xF9, 0x02, 0x7F, 0x50, 0x3C, 0x9F, 0xA8,
            0x51, 0xA3, 0x40, 0x8F, 0x92, 0x9D, 0x38, 0xF5,
            0xBC, 0xB6, 0xDA, 0x21, 0x10, 0xFF, 0xF3, 0xD2,
            0xCD, 0x0C, 0x13, 0xEC, 0x5F, 0x97, 0x44, 0x17,
            0xC4, 0xA7, 0x7E, 0x3D, 0x64, 0x5D, 0x19, 0x73,
            0x60, 0x81, 0x4F, 0xDC, 0x22, 0x2A, 0x90, 0x88,
            0x46, 0xEE, 0xB8, 0x14, 0xDE, 0x5E, 0x0B, 0xDB,
            0xE0, 0x32, 0x3A, 0x0A, 0x49, 0x06, 0x24, 0x5C,
            0xC2, 0xD3, 0xAC, 0x62, 0x91, 0x95, 0xE4, 0x79,
            0xE7, 0xC8, 0x37, 0x6D, 0x8D, 0xD5, 0x4E, 0xA9,
            0x6C, 0x56, 0xF4, 0xEA, 0x65, 0x7A, 0xAE, 0x08,
            0xBA, 0x78, 0x25, 0x2E, 0x1C, 0xA6, 0xB4, 0xC6,
            0xE8, 0xDD, 0x74, 0x1F, 0x4B, 0xBD, 0x8B, 0x8A,
            0x70, 0x3E, 0xB5, 0x66, 0x48, 0x03, 0xF6, 0x0E,
            0x61, 0x35, 0x57, 0xB9, 0x86, 0xC1, 0x1D, 0x9E,
            0xE1, 0xF8, 0x98, 0x11, 0x69, 0xD9, 0x8E, 0x94,
            0x9B, 0x1E, 0x87, 0xE9, 0xCE, 0x55, 0x28, 0xDF,
            0x8C, 0xA1, 0x89, 0x0D, 0xBF, 0xE6, 0x42, 0x68,
            0x41, 0x99, 0x2D, 0x0F, 0xB0, 0x54, 0xBB, 0x16
    };


    private byte substitutionBox(byte value) {
        // Retrieve the substitution value from the sBox array
        return (byte) sBox[value & 0xFF];
    }

    private byte reverseSubstitutionBox(byte value) {
        // Reverse substitution box
        for (int i = 0; i < sBox.length; i++) {
            if ((sBox[i] & 0xFF) == (value & 0xFF)) {
                return (byte) i;
            }
        }
        throw new IllegalArgumentException("Invalid input for reverse substitution box");
    }

    private byte[] permutationLayer(byte[] input) {
        // Apply a simple permutation by swapping adjacent elements
        byte[] permuted = new byte[input.length];
        for (int i = 0; i < input.length; i++) {
            if (i % 2 == 0) {
                permuted[i] = input[i + 1 < input.length ? i + 1 : i];
            } else {
                permuted[i] = input[i - 1];
            }
        }
        return permuted;
    }

    private byte[] reversePermutationLayer(byte[] input) {
        // Reverse the permutation layer
        return permutationLayer(input);
    }


}






//AES with generateRandomIV() & SPN transformation

//import android.content.Context;
//import android.util.Base64;
//import android.widget.Toast;
//
//import java.security.InvalidKeyException;
//import java.security.NoSuchAlgorithmException;
//import java.security.SecureRandom;
//
//import javax.crypto.BadPaddingException;
//import javax.crypto.Cipher;
//import javax.crypto.IllegalBlockSizeException;
//import javax.crypto.SecretKey;
//import javax.crypto.spec.IvParameterSpec;
//import javax.crypto.spec.SecretKeySpec;
//
//public class AES {
//
//    private SecretKey secretKey;
//    private Cipher cipher, deCipher;
//
//    public AES(Context context) {
//        try {
//            // Generate or load the key securely
//            secretKey = loadOrGenerateKey();
//
//            // Use CBC mode with PKCS7Padding
//            cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
//            deCipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
//
//        } catch (Exception e) {
//            e.printStackTrace();
//            Toast.makeText(context, "Encryption Error!", Toast.LENGTH_SHORT).show();
//        }
//    }
//
//    private SecretKey loadOrGenerateKey() throws NoSuchAlgorithmException {
//        // Use a consistent key for simplicity (not recommended for production)
//        byte[] encryptionKey = {
//                9, 115, 51, 86, 105, 4, -31, -23, 89 , -83, -35, 98, -123, 20, 43, -128,
//                -55, -43, 45, -63, 87, 93, 69, -48, -68, 88, 17, 20, 3, -105, 119, -53};
//
//        return new SecretKeySpec(encryptionKey, "AES");
//    }
//
//    public String Encrypt(String string, Context context) {
//        byte[] stringByte = string.getBytes();
//        byte[] encryptedByte;
//
//        try {
//            // Apply XOR transformation
//            byte[] transformedByte = applySPNTransformation(stringByte);
//
//            // Generate a random IV for each encryption
//            byte[] iv = generateRandomIV();
//            IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);
//
//            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParameterSpec);
//            encryptedByte = cipher.doFinal(transformedByte);
//
//            // Combine IV and ciphertext and encode in Base64
//            byte[] combined = new byte[iv.length + encryptedByte.length];
//            System.arraycopy(iv, 0, combined, 0, iv.length);
//            System.arraycopy(encryptedByte, 0, combined, iv.length, encryptedByte.length);
//
//            return Base64.encodeToString(combined, Base64.DEFAULT);
//
//        } catch (InvalidKeyException | BadPaddingException | IllegalBlockSizeException e) {
//            e.printStackTrace();
//            Toast.makeText(context, "Encryption Error!", Toast.LENGTH_SHORT).show();
//            return null;
//        } catch (Exception e) {
//            e.printStackTrace();
//            Toast.makeText(context, "Encryption Error!", Toast.LENGTH_SHORT).show();
//            return null;
//        }
//    }
//
//    public String Decrypt(String string, Context context) {
//        try {
//            // Decode Base64
//            byte[] combined = Base64.decode(string, Base64.DEFAULT);
//
//            // Extract IV and ciphertext
//            byte[] iv = new byte[cipher.getBlockSize()];
//            byte[] encryptedByte = new byte[combined.length - iv.length];
//            System.arraycopy(combined, 0, iv, 0, iv.length);
//            System.arraycopy(combined, iv.length, encryptedByte, 0, encryptedByte.length);
//
//            IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);
//
//            deCipher.init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpec);
//            byte[] decryption = deCipher.doFinal(encryptedByte);
//
//            // Reverse XOR transformation
//            byte[] transformedByte = reverseSPNTransformation(decryption);
//
//            return new String(transformedByte);
//
//        } catch (InvalidKeyException | BadPaddingException | IllegalBlockSizeException e) {
//            e.printStackTrace();
//            Toast.makeText(context, "Decryption Error!", Toast.LENGTH_SHORT).show();
//            return null;
//        } catch (Exception e) {
//            e.printStackTrace();
//            Toast.makeText(context, "Decryption Error!", Toast.LENGTH_SHORT).show();
//            return null;
//        }
//    }
//
//    private byte[] generateRandomIV() {
//        // Generate a random IV
//        SecureRandom secureRandom = new SecureRandom();
//        byte[] iv = new byte[cipher.getBlockSize()];
//        secureRandom.nextBytes(iv);
//        return iv;
//    }
//
//    private byte[] applySPNTransformation(byte[] input) {
//        // Apply a basic linear transformation
//        byte[] result = new byte[input.length];
//        for (int i = 0; i < input.length; i++) {
//            result[i] = (byte) ((input[i] + 3) % 256); // Simple linear transformation, not secure
//        }
//        return result;
//    }
//
//    private byte[] reverseSPNTransformation(byte[] input) {
//        // Reverse the basic linear transformation
//        byte[] result = new byte[input.length];
//        for (int i = 0; i < input.length; i++) {
//            result[i] = (byte) ((input[i] - 3 + 256) % 256); // Reverse linear transformation
//        }
//        return result;
//    }
//
//}




//AES with generateRandomIV() & simple XOR transformation

//package com.example.cryptochat;
//
//import android.content.Context;
//import android.util.Base64;
//import android.widget.Toast;
//
//import java.security.InvalidKeyException;
//import java.security.NoSuchAlgorithmException;
//import java.security.SecureRandom;
//
//import javax.crypto.BadPaddingException;
//import javax.crypto.Cipher;
//import javax.crypto.IllegalBlockSizeException;
//import javax.crypto.SecretKey;
//import javax.crypto.spec.IvParameterSpec;
//import javax.crypto.spec.SecretKeySpec;
//
//public class AES {
//
//    private SecretKey secretKey;
//    private Cipher cipher, deCipher;
//
//    public AES(Context context) {
//        try {
//            // Generate or load the key securely
//            secretKey = loadOrGenerateKey();
//
//            // Use CBC mode with PKCS7Padding
//            cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
//            deCipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
//
//        } catch (Exception e) {
//            e.printStackTrace();
//            Toast.makeText(context, "Encryption Error!", Toast.LENGTH_SHORT).show();
//        }
//    }
//
//    private SecretKey loadOrGenerateKey() throws NoSuchAlgorithmException {
//        // Use a consistent key for simplicity (not recommended for production)
//        byte[] encryptionKey = {
//                9, 115, 51, 86, 105, 4, -31, -23, 89 , -83, -35, 98, -123, 20, 43, -128,
//                -55, -43, 45, -63, 87, 93, 69, -48, -68, 88, 17, 20, 3, -105, 119, -53};
//
//        return new SecretKeySpec(encryptionKey, "AES");
//    }
//
//    public String Encrypt(String string, Context context) {
//        byte[] stringByte = string.getBytes();
//        byte[] encryptedByte;
//
//        try {
//            // Apply XOR transformation
//            byte[] transformedByte = applyXORTransformation(stringByte);
//
//            // Generate a random IV for each encryption
//            byte[] iv = generateRandomIV();
//            IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);
//
//            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParameterSpec);
//            encryptedByte = cipher.doFinal(transformedByte);
//
//            // Combine IV and ciphertext and encode in Base64
//            byte[] combined = new byte[iv.length + encryptedByte.length];
//            System.arraycopy(iv, 0, combined, 0, iv.length);
//            System.arraycopy(encryptedByte, 0, combined, iv.length, encryptedByte.length);
//
//            return Base64.encodeToString(combined, Base64.DEFAULT);
//
//        } catch (InvalidKeyException | BadPaddingException | IllegalBlockSizeException e) {
//            e.printStackTrace();
//            Toast.makeText(context, "Encryption Error!", Toast.LENGTH_SHORT).show();
//            return null;
//        } catch (Exception e) {
//            e.printStackTrace();
//            Toast.makeText(context, "Encryption Error!", Toast.LENGTH_SHORT).show();
//            return null;
//        }
//    }
//
//    public String Decrypt(String string, Context context) {
//        try {
//            // Decode Base64
//            byte[] combined = Base64.decode(string, Base64.DEFAULT);
//
//            // Extract IV and ciphertext
//            byte[] iv = new byte[cipher.getBlockSize()];
//            byte[] encryptedByte = new byte[combined.length - iv.length];
//            System.arraycopy(combined, 0, iv, 0, iv.length);
//            System.arraycopy(combined, iv.length, encryptedByte, 0, encryptedByte.length);
//
//            IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);
//
//            deCipher.init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpec);
//            byte[] decryption = deCipher.doFinal(encryptedByte);
//
//            // Reverse XOR transformation
//            byte[] transformedByte = applyXORTransformation(decryption);
//
//            return new String(transformedByte);
//
//        } catch (InvalidKeyException | BadPaddingException | IllegalBlockSizeException e) {
//            e.printStackTrace();
//            Toast.makeText(context, "Decryption Error!", Toast.LENGTH_SHORT).show();
//            return null;
//        } catch (Exception e) {
//            e.printStackTrace();
//            Toast.makeText(context, "Decryption Error!", Toast.LENGTH_SHORT).show();
//            return null;
//        }
//    }
//
//    private byte[] generateRandomIV() {
//        // Generate a random IV
//        SecureRandom secureRandom = new SecureRandom();
//        byte[] iv = new byte[cipher.getBlockSize()];
//        secureRandom.nextBytes(iv);
//        return iv;
//    }
//
//    private byte[] applyXORTransformation(byte[] input) {
//        // Implement XOR transformation logic here
//        // For simplicity, this example XORs each byte with a constant value
//        byte[] transformed = new byte[input.length];
//        for (int i = 0; i < input.length; i++) {
//            transformed[i] = (byte) (input[i] ^ 0xFF);
//        }
//        return transformed;
//    }
//}


//AES with generateRandomIV()

//package com.example.cryptochat;
//
//import android.content.Context;
//import android.util.Base64;
//import android.widget.Toast;
//
//import java.security.InvalidKeyException;
//import java.security.NoSuchAlgorithmException;
//import java.security.SecureRandom;
//
//import javax.crypto.BadPaddingException;
//import javax.crypto.Cipher;
//import javax.crypto.IllegalBlockSizeException;
//import javax.crypto.SecretKey;
//import javax.crypto.spec.IvParameterSpec;
//import javax.crypto.spec.SecretKeySpec;
//
//public class AES {
//
//    private SecretKey secretKey;
//    private Cipher cipher, deCipher;
//
//    public AES(Context context) {
//        try {
//            // Generate or load the key securely
//            secretKey = loadOrGenerateKey();
//
//            // Use CBC mode with PKCS7Padding
//            cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
//            deCipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
//
//        } catch (Exception e) {
//            e.printStackTrace();
//            Toast.makeText(context, "Encryption Error!", Toast.LENGTH_SHORT).show();
//        }
//    }
//
//    private SecretKey loadOrGenerateKey() throws NoSuchAlgorithmException {
//        // Use a consistent key for simplicity (not recommended for production)
//        byte[] encryptionKey = {
//                9, 115, 51, 86, 105, 4, -31, -23, 89 , -83, -35, 98, -123, 20, 43, -128,
//                -55, -43, 45, -63, 87, 93, 69, -48, -68, 88, 17, 20, 3, -105, 119, -53};
//
//        return new SecretKeySpec(encryptionKey, "AES");
//    }
//
//    public String Encrypt(String string, Context context) {
//        byte[] stringByte = string.getBytes();
//        byte[] encryptedByte;
//
//        try {
//            // Generate a random IV for each encryption
//            byte[] iv = generateRandomIV();
//            IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);
//
//            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParameterSpec);
//            encryptedByte = cipher.doFinal(stringByte);
//
//            // Combine IV and ciphertext and encode in Base64
//            byte[] combined = new byte[iv.length + encryptedByte.length];
//            System.arraycopy(iv, 0, combined, 0, iv.length);
//            System.arraycopy(encryptedByte, 0, combined, iv.length, encryptedByte.length);
//
//            return Base64.encodeToString(combined, Base64.DEFAULT);
//
//        } catch (InvalidKeyException | BadPaddingException | IllegalBlockSizeException e) {
//            e.printStackTrace();
//            Toast.makeText(context, "Encryption Error!", Toast.LENGTH_SHORT).show();
//            return null;
//        } catch (Exception e) {
//            e.printStackTrace();
//            Toast.makeText(context, "Encryption Error!", Toast.LENGTH_SHORT).show();
//            return null;
//        }
//    }
//
//    public String Decrypt(String string, Context context) {
//        try {
//            // Decode Base64
//            byte[] combined = Base64.decode(string, Base64.DEFAULT);
//
//            // Extract IV and ciphertext
//            byte[] iv = new byte[cipher.getBlockSize()];
//            byte[] encryptedByte = new byte[combined.length - iv.length];
//            System.arraycopy(combined, 0, iv, 0, iv.length);
//            System.arraycopy(combined, iv.length, encryptedByte, 0, encryptedByte.length);
//
//            IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);
//
//            deCipher.init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpec);
//            byte[] decryption = deCipher.doFinal(encryptedByte);
//
//            return new String(decryption);
//
//        } catch (InvalidKeyException | BadPaddingException | IllegalBlockSizeException e) {
//            e.printStackTrace();
//            Toast.makeText(context, "Decryption Error!", Toast.LENGTH_SHORT).show();
//            return null;
//        } catch (Exception e) {
//            e.printStackTrace();
//            Toast.makeText(context, "Decryption Error!", Toast.LENGTH_SHORT).show();
//            return null;
//        }
//    }
//
//    private byte[] generateRandomIV() {
//        // Generate a random IV
//        SecureRandom secureRandom = new SecureRandom();
//        byte[] iv = new byte[cipher.getBlockSize()];
//        secureRandom.nextBytes(iv);
//        return iv;
//    }
//}


//package com.example.cryptochat;
//import android.content.Context;
//import android.widget.Toast;
//
//import java.io.UnsupportedEncodingException;
//import java.security.InvalidKeyException;
//
//import javax.crypto.BadPaddingException;
//import javax.crypto.Cipher;
//import javax.crypto.IllegalBlockSizeException;
//import javax.crypto.SecretKey;
//import javax.crypto.spec.SecretKeySpec;
//
//public class AES {
//
//    private SecretKey secretKey;
//    private byte encryptionKey[] = {
//            9, 115, 51, 86, 105, 4, -31, -23, 89 , -83, -35, 98, -123, 20, 43, -128, -55, -43, 45, -63, 87, 93, 69, -48, -68, 88, 17, 20, 3, -105, 119, -53};
//
//    private Cipher cipher, deCipher;
//    private SecretKeySpec secretKeySpec;
//
//    public AES(Context context) {
//        try {
//            cipher = Cipher.getInstance("AES");
//            deCipher = Cipher.getInstance("AES");
//        }
//        catch (Exception e){
//            e.printStackTrace();
//            Toast.makeText(context, "Encryption Error!", Toast.LENGTH_SHORT).show();
//        }
//        secretKeySpec = new SecretKeySpec(encryptionKey, "AES");
//    }
//
//    public String Encrypt(String string, Context context){
//
//        byte[] stringByte = string.getBytes();
//        byte[] encryptedByte = new byte[stringByte.length];
//
//        try {
//            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
//            encryptedByte = cipher.doFinal(stringByte);
//        } catch (InvalidKeyException e) {
//            e.printStackTrace();
//            Toast.makeText(context, "Encryption Error!", Toast.LENGTH_SHORT).show();
//        } catch (BadPaddingException e) {
//            e.printStackTrace();
//            Toast.makeText(context, "Encryption Error!", Toast.LENGTH_SHORT).show();
//        } catch (IllegalBlockSizeException e) {
//            e.printStackTrace();
//            Toast.makeText(context, "Encryption Error!", Toast.LENGTH_SHORT).show();
//        }
//
//        String returnString = null;
//
//        try {
//            returnString = new String(encryptedByte, "ISO-8859-1");
//        } catch (UnsupportedEncodingException e) {
//            e.printStackTrace();
//            Toast.makeText(context, "Encryption Error!", Toast.LENGTH_SHORT).show();
//        }
//        return returnString;
//    }
//
//    public String Decrypt(String string, Context context)  {
//        byte[] EncryptedByte = new byte[0];
//        try {
//            EncryptedByte = string.getBytes("ISO-8859-1");
//        } catch (UnsupportedEncodingException e) {
//            e.printStackTrace();
//            Toast.makeText(context, "Decryption Error!", Toast.LENGTH_SHORT).show();
//        }
//        String decryptedString = string;
//
//        byte[] decryption;
//
//        try {
//            deCipher.init(cipher.DECRYPT_MODE, secretKeySpec);
//            decryption = deCipher.doFinal(EncryptedByte);
//            decryptedString = new String(decryption);
//        } catch (InvalidKeyException e) {
//            e.printStackTrace();
//            Toast.makeText(context, "Decryption Error!", Toast.LENGTH_SHORT).show();
//        } catch (BadPaddingException e) {
//            e.printStackTrace();
//            Toast.makeText(context, "Decryption Error!", Toast.LENGTH_SHORT).show();
//        } catch (IllegalBlockSizeException e) {
//            e.printStackTrace();
//            Toast.makeText(context, "Decryption Error!", Toast.LENGTH_SHORT).show();
//        }
//        return decryptedString;
//    }
//
//}