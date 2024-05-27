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
            42, -78, 16, 33, -92, 55, 64, -19, 91, -64, -76, -8, -17, 101, 78, 0,
            -119, 55, -28, 92, -46, 18, -66, 28, -55, 71, -12, 84, 99, -14, 20, -93
        };        

        return new SecretKeySpec(encryptionKey, "AES");
    }

    private SecretKey loadWhiteningKey() throws NoSuchAlgorithmException {
        // Use a different consistent key for whitening
        byte[] whiteningKeyBytes = {
            -82, 41, -96, 73, -63, -4, -22, -68, 110, -88, -31, 54, -15, 3, -18, 90
        };

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
        0xA2, 0xD8, 0x84, 0x19, 0xF3, 0xEF, 0x0B, 0x4A,
        0x77, 0x2D, 0xD0, 0xBD, 0x25, 0x1A, 0xB1, 0x03,
        0xCA, 0x9F, 0x7D, 0x20, 0x6B, 0x59, 0x89, 0x50,
        0x2E, 0xDB, 0xE4, 0x33, 0xC6, 0xE9, 0x07, 0x63,
        0x22, 0x3B, 0xEC, 0x21, 0x8D, 0xA3, 0xC1, 0xA1,
        0x6D, 0xE7, 0x3F, 0xE2, 0x5C, 0xC0, 0xD6, 0xF2,
        0x1F, 0x3E, 0x30, 0x2A, 0x5A, 0xA5, 0xAB, 0xA8,
        0xCC, 0x5D, 0x79, 0x83, 0xE0, 0x2C, 0x6F, 0xDD,
        0x99, 0x36, 0x7A, 0xC4, 0x60, 0xBE, 0x47, 0x9B,
        0xF9, 0x85, 0x37, 0x93, 0xA9, 0x32, 0x57, 0x56,
        0x7F, 0xD3, 0x49, 0x3A, 0x15, 0xB5, 0x62, 0x4C,
        0xFB, 0x17, 0xAF, 0xF0, 0xBA, 0x58, 0xD2, 0xC3,
        0x71, 0xFE, 0x51, 0x91, 0x53, 0xD5, 0x18, 0x24,
        0x4F, 0x46, 0x6E, 0xF4, 0x8B, 0x0F, 0x5F, 0xF1,
        0x9A, 0x48, 0x65, 0xF5, 0x7C, 0x2F, 0x87, 0xD4,
        0x66, 0xA6, 0x16, 0xD7, 0x61, 0x9D, 0x11, 0x8A,
        0x08, 0x4E, 0x10, 0xB0, 0x67, 0x38, 0x9E, 0x27,
        0x82, 0xE6, 0x05, 0xA7, 0x1E, 0x9C, 0x7E, 0x98,
        0x80, 0x0E, 0x6A, 0xF6, 0x9E, 0x2B, 0x90, 0xCF,
        0x26, 0x81, 0x0D, 0x97, 0x68, 0x3C, 0x55, 0x6C,
        0x35, 0xE5, 0x01, 0x4D, 0xA4, 0x75, 0x9A, 0x8E,
        0x72, 0x12, 0xB3, 0xE8, 0xC2, 0xF8, 0xB2, 0x1D,
        0xF7, 0xE1, 0xAE, 0xB4, 0xD9, 0xCD, 0xAA, 0xF7,
        0xF4, 0x96, 0xD1, 0x42, 0x70, 0x4B, 0xFC, 0x2C,
        0xEA, 0x78, 0x6A, 0xDE, 0x69, 0x64, 0x45, 0x52,
        0x8F, 0x0A, 0x14, 0x1B, 0xC8, 0x88, 0x74, 0xAC,
        0x41, 0x3D, 0xAD, 0x04, 0xB6, 0x95, 0x1C, 0xB8,
        0xDC, 0x8C, 0xC7, 0xDA, 0x06, 0x86, 0xEB, 0xED,
        0x1B, 0x13, 0xC9, 0xC5, 0x23, 0x40, 0x28, 0xCE,
        0xDF, 0x0C, 0x31, 0x34, 0x76, 0x73, 0xB9, 0xA0,
        0xBB, 0x94, 0xEF, 0xB7, 0xFF, 0x43, 0x44, 0x29,
        0x54, 0xC7, 0x92, 0x2F, 0x6B, 0xD5, 0x00, 0xEB
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