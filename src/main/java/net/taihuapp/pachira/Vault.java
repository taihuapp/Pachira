/*
 * Copyright (C) 2018-2021.  Guangliang He.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This file is part of Pachira.
 *
 * Pachira is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or any
 * later version.
 *
 * Pachira is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.taihuapp.pachira;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Base64;

public class Vault {

    private static final int IV_LEN = 16; // length of IV in bytes
    private static final int SALT_LEN = 32; // salt length in bytes
    private static final int HASH_KEY_LEN = 512; // hash key length in bits
    private static final int HASH_NUM_ITERATION = 100000; // num of iterations for hash
    private static final int ENCRYPTION_NUM_ITERATION = 12288;
    private static final String HASH_ALGORITHM = "PBKDF2WithHmacSHA512"; // hash algorithm
    private static final String SECRET_KEY_FACTORY_ALGORITHM = "PBEWithHmacSHA512AndAES_128";
    private static final String ENCODE_STRING_SEPARATOR = ":";  // separator for encoded strings
    private static final String MASTERKEY_ALIAS = "MASTERKEY";
    private static final String SESSIONKEY_ALIAS = "SESSIONKEY";
    private static final SecureRandom SECURERANDOM = new SecureRandom();

    private KeyStore mKeyStore = null; // to keep master password in memory
    private boolean mMPInKeyStore = false;
    private byte[] mHashedMasterPassword = null;
    private byte[] mSalt = null;

    // returns random bytes of given length
    private static byte[] randomBytes(int len) {
        byte[] rb = new byte[len];
        SECURERANDOM.nextBytes(rb);
        return rb;
    }

    // convert an array of char to an array of byte
    // This code is copied from stackoverflow
    // https://stackoverflow.com/a/9670279/3079849
    private static byte[] toBytes(char[] chars) {
        CharBuffer charBuffer = CharBuffer.wrap(chars);
        ByteBuffer byteBuffer = StandardCharsets.UTF_8.encode(charBuffer);
        byte[] bytes = Arrays.copyOfRange(byteBuffer.array(),
                byteBuffer.position(), byteBuffer.limit());
        Arrays.fill(byteBuffer.array(), (byte) 0); // clear sensitive data
        return bytes;
    }

    private static byte[] hash(final char[] password, final byte[] salt)
            throws NoSuchAlgorithmException, InvalidKeySpecException {

        // from https://www.owasp.org/index.php/Hashing_Java
        final SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance(HASH_ALGORITHM);
        // the key length parameter here is needed.
        final PBEKeySpec pbeKeySpec = new PBEKeySpec(password, salt, HASH_NUM_ITERATION, HASH_KEY_LEN);
        final SecretKey secretKey = secretKeyFactory.generateSecret(pbeKeySpec);
        pbeKeySpec.clearPassword();
        return secretKey.getEncoded();
    }

    private static String encode(final byte[] bytes) { return new String(Base64.getEncoder().encode(bytes)); }
    private static byte[] decode(final String encoded) { return Base64.getDecoder().decode(encoded); }

    public boolean hasMasterPassword() { return mHashedMasterPassword != null; }
    public boolean hasMasterPasswordInKeyStore() { return mMPInKeyStore; }

    // put Master Password into keystore
    // make sure mKeyStore is not null and properly initialized, otherwise bad things will happen.
    // exceptions are thrown when failure
    private void putMasterPasswordInKeyStore(final char[] masterPassword) throws KeyStoreException,
            NoSuchAlgorithmException, UnrecoverableKeyException, InvalidKeySpecException {
        char[] sessionPassword = null;
        try {
            SecretKey secretKey = (SecretKey) mKeyStore.getKey(SESSIONKEY_ALIAS, null);
            SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance("PBE");
            sessionPassword = ((PBEKeySpec) secretKeyFactory.getKeySpec(secretKey, PBEKeySpec.class)).getPassword();

            PBEKeySpec pbeKeySpec = new PBEKeySpec(masterPassword, randomBytes(SALT_LEN), HASH_NUM_ITERATION, HASH_KEY_LEN);
            SecretKey masterSecretKey = secretKeyFactory.generateSecret(pbeKeySpec);
            mKeyStore.setKeyEntry(MASTERKEY_ALIAS, masterSecretKey, sessionPassword, null);
            mMPInKeyStore = true;
        } finally {
            if (sessionPassword != null)
                Arrays.fill(sessionPassword, ' ');
        }
    }

    // retrieve master password from keystore
    // return null if this is no master password stored in keystore
    private char[] getMasterPassword() throws NoSuchAlgorithmException, InvalidKeySpecException,
            KeyStoreException, UnrecoverableKeyException {
        if (!mMPInKeyStore)
            return null;
        char[] sessionPassword = null;
        try {
            SecretKey secretKey = (SecretKey) mKeyStore.getKey(SESSIONKEY_ALIAS, null);
            SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance("PBE");
            sessionPassword = ((PBEKeySpec) secretKeyFactory.getKeySpec(secretKey, PBEKeySpec.class)).getPassword();

            secretKey = (SecretKey) mKeyStore.getKey(MASTERKEY_ALIAS, sessionPassword);
            if (secretKey == null) // MASTER_KEY_ALIAS not found in keystore
                return null;
            return ((PBEKeySpec) secretKeyFactory.getKeySpec(secretKey, PBEKeySpec.class)).getPassword();
        } finally {
            if (sessionPassword != null)
                Arrays.fill(sessionPassword, ' '); // wipe sessionPassword
        }
    }

    public void deleteMasterPassword() throws KeyStoreException {
        // delete entry in keystore first
        mKeyStore.deleteEntry(MASTERKEY_ALIAS);
        mMPInKeyStore = false;
        mHashedMasterPassword = null;
        mSalt = null;
    }

    // take a input of char array, returns a string of it's encryption with the encryption salt
    // if master password is not properly stored in keystore, null string will be returned
    public String encrypt(final char[] clearChars) throws NoSuchAlgorithmException, InvalidKeySpecException,
            KeyStoreException, UnrecoverableKeyException, NoSuchPaddingException, InvalidAlgorithmParameterException,
            InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        final byte[] clearBytes = toBytes(clearChars);

        final byte[] salt = randomBytes(SALT_LEN);
        final byte[] iv = randomBytes(IV_LEN);
        IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);

        char [] masterPassword = null;
        PBEKeySpec pbeKeySpec = null;
        try {
            // need to get Master password
            masterPassword = getMasterPassword();
            if (masterPassword == null)
                return null;

            // according to this link, the key length parameter in PBEKeySpec is not used
            // https://github.com/rogerta/secrets-for-android/issues/103
            pbeKeySpec = new PBEKeySpec(masterPassword, salt, ENCRYPTION_NUM_ITERATION);

            SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance(SECRET_KEY_FACTORY_ALGORITHM);

            SecretKey secretKey = secretKeyFactory.generateSecret(pbeKeySpec);

            PBEParameterSpec pbeParameterSpec = new PBEParameterSpec(salt, ENCRYPTION_NUM_ITERATION, ivParameterSpec);
            Cipher encryptionCipher = Cipher.getInstance(SECRET_KEY_FACTORY_ALGORITHM);
            encryptionCipher.init(Cipher.ENCRYPT_MODE, secretKey, pbeParameterSpec);

            byte[] encryptedBytes = encryptionCipher.doFinal(clearBytes);
            return encode(encryptedBytes) + ENCODE_STRING_SEPARATOR + encode(salt) + ENCODE_STRING_SEPARATOR + encode(iv);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException
                | KeyStoreException
                | UnrecoverableKeyException | NoSuchPaddingException | InvalidAlgorithmParameterException
                | InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
            Arrays.fill(clearBytes, (byte) 0);
            if (masterPassword != null)
                Arrays.fill(masterPassword, '\u0000');
            if (pbeKeySpec != null)
                pbeKeySpec.clearPassword();
            throw e;
        }
    }

    // take a input of encrypted string with salt and IV (separated by ENCODE_STRING_SEPARATOR,
    // returns decrypted content in a char[]
    // if master password is not properly stored in keystore, null will be returned
    public char[] decrypt(String encryptedSecretWithSaltAndIV) throws IllegalArgumentException,
            NoSuchAlgorithmException, InvalidKeySpecException, KeyStoreException, UnrecoverableKeyException,
            NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException,
            IllegalBlockSizeException, BadPaddingException {
        String[] tokens = encryptedSecretWithSaltAndIV.split(ENCODE_STRING_SEPARATOR);
        if (tokens.length != 3)
            throw new IllegalArgumentException("Input should be three strings separated by " + ENCODE_STRING_SEPARATOR);

        byte[] salt = Base64.getDecoder().decode(tokens[1]);
        byte[] iv = Base64.getDecoder().decode(tokens[2]);
        IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);

        char[] masterPassword = null;
        PBEKeySpec pbeKeySpec = null;
        try {
            masterPassword = getMasterPassword();
            if (masterPassword == null)
                return null;

            pbeKeySpec = new PBEKeySpec(masterPassword, salt, ENCRYPTION_NUM_ITERATION);

            SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance(SECRET_KEY_FACTORY_ALGORITHM);

            SecretKey secretKey = secretKeyFactory.generateSecret(pbeKeySpec);

            PBEParameterSpec pbeParameterSpec = new PBEParameterSpec(salt, ENCRYPTION_NUM_ITERATION, ivParameterSpec);
            Cipher decryptionCipher = Cipher.getInstance(SECRET_KEY_FACTORY_ALGORITHM);
            decryptionCipher.init(Cipher.DECRYPT_MODE, secretKey, pbeParameterSpec);

            byte[] clearBytes = decryptionCipher.doFinal(Base64.getDecoder().decode(tokens[0]));
            char[] clearChars = new char[clearBytes.length];
            for (int i = 0; i < clearBytes.length; i++) {
                clearChars[i] = (char) clearBytes[i];
                clearBytes[i] = (byte) 0;
            }
            return clearChars;
        } finally {
            if (masterPassword != null)
                Arrays.fill(masterPassword, '\u0000');
            if (pbeKeySpec != null)
                pbeKeySpec.clearPassword();
        }
    }

    // initialize mKeyStore, if exception is thrown, mKeyStore is kept as null
    public void setupKeyStore() throws KeyStoreException, NoSuchAlgorithmException, IOException,
            CertificateException, InvalidKeySpecException {

        // generate random session password
        // The 95 characters between (char) 32 and (char) 126 will be accepted by
        // secret key factory.  (char) 31 and (char) 127 (probably many characters
        // below (char) 31) will cause a 'password is not ASCII' exception.
        char[] password = new char[32];
        int maxChar = 95;
        for (int i = 0; i < password.length; i++) {
            password[i] = (char) (32 + SECURERANDOM.nextInt(maxChar));
        }

        PBEKeySpec pbeKeySpec = null;
        try {
            byte[] salt = randomBytes(SALT_LEN);
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(null, password);  // use password as keyStore password, not sure if necessary
            pbeKeySpec = new PBEKeySpec(password, salt, HASH_NUM_ITERATION, HASH_KEY_LEN);
            SecretKey secretKey = SecretKeyFactory.getInstance("PBE").generateSecret(pbeKeySpec);
            keyStore.setKeyEntry(SESSIONKEY_ALIAS, secretKey, null, null);
            mKeyStore = keyStore;
        } finally {
            Arrays.fill(password, ' ');
            if (pbeKeySpec != null)
                pbeKeySpec.clearPassword();
        }
    }

    // Take a password, hash it with mSalt and compare the result with
    // mHashedMasterPassword.
    // return true if match, false otherwise
    private boolean comparePassword(final char[] password)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        final byte[] hashedPassword = hash(password, mSalt);
        boolean mismatch = false;
        // hashedPassword returned by hash method will never be null
        if ((mHashedMasterPassword == null) || (mHashedMasterPassword.length != hashedPassword.length))
            mismatch = true;
        for (int i = 0; i < hashedPassword.length; i++) {
            if ((mHashedMasterPassword == null)
                    || (i >= mHashedMasterPassword.length)
                    || (hashedPassword[i] != mHashedMasterPassword[i])) {
                mismatch = true;
                // we do not want to terminate the loop here to prevent
                // the caller to know the location of mismatch happened.
            }
        }
        return !mismatch;
    }

    // Take an input string (encodedHashedMasterPassword:encodedSalt)
    // split, decode, and assigned to mHashedMasterPassword and mSalt
    // return true for success and false for failure
    public void setHashedMasterPassword(String encodedHashedMasterPasswordWithSalt) throws IllegalArgumentException {
        String[] stringArray = encodedHashedMasterPasswordWithSalt.split(ENCODE_STRING_SEPARATOR);
        if (stringArray.length != 2)
            throw new IllegalArgumentException("Encoded and hashed master password should be "
                    + "two base64 strings separated by '" + ENCODE_STRING_SEPARATOR + "'");

        mHashedMasterPassword = decode(stringArray[0]);
        mSalt = decode(stringArray[1]);
    }

    public String getEncodedHashedMasterPassword() {
        return encode(mHashedMasterPassword) + ENCODE_STRING_SEPARATOR + encode(mSalt);
    }

    // return true if input password matches existing master password.
    // Otherwise return false.
    // if the input password is verified, a copy is saved in mKeyStore
    public boolean verifyMasterPassword(final char[] password) throws NoSuchAlgorithmException,
            InvalidKeySpecException, KeyStoreException, UnrecoverableKeyException {
        if (comparePassword(password)) {
            putMasterPasswordInKeyStore(password);
            return true;
        }
        return false;
    }

    // if success, the mSalt is set to be the newly created random salt
    // and mHashedMasterPassword is set to be the hashed password.
    // if failure, neither mSalt and mHashedMasterPassword is changed and exception will be thrown
    public void setMasterPassword(final char[] password) throws NoSuchAlgorithmException,
            InvalidKeySpecException, KeyStoreException, UnrecoverableKeyException {
        byte[] newSalt = randomBytes(SALT_LEN);
        byte[] hashedNewMasterPassword;
        hashedNewMasterPassword = hash(password, newSalt);
        putMasterPasswordInKeyStore(password);
        mHashedMasterPassword = hashedNewMasterPassword;
        mSalt = newSalt;
    }
}
