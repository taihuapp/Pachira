/*
 * Copyright (C) 2018.  Guangliang He.  All Rights Reserved.
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

package net.taihuapp.pachira.net.taihuapp.pachira.dc;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Base64;

public class Vault {

    private static final int IVLEN = 16; // length of IV in bytes
    private static final int SALTLEN = 32; // salt length in bytes
    private static final int HASHKEYLEN = 512; // hash key length in bits
    private static final int HASHNUMITERATION = 100000; // num of iterations for hash
    private static final int ENCRYPTIONNUMITERATION = 12288;
    private static final String HASHALGORITHM = "PBKDF2WithHmacSHA512"; // hash algorithm
    private static final String SECRETKEYFACTORYALGORITHM = "PBEWithHmacSHA512AndAES_128";
    private static final String ENCODESTRINGSEPARATOR = ":";  // separator for encoded strings
    private static final String MASTERKEYALIAS = "MASTERKEY";
    private static final String SESSIONKEYALIAS = "SESSIONKEY";
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
    // This code is copied from Andrii Nemchenko at stackoverflow
    // https://stackoverflow.com/a/9670279/3079849
    private static byte[] toBytes(char[] chars) {
        CharBuffer charBuffer = CharBuffer.wrap(chars);
        ByteBuffer byteBuffer = Charset.forName("UTF-8").encode(charBuffer);
        byte[] bytes = Arrays.copyOfRange(byteBuffer.array(),
                byteBuffer.position(), byteBuffer.limit());
        Arrays.fill(charBuffer.array(), '\u0000'); // clear sensitive data
        Arrays.fill(byteBuffer.array(), (byte) 0); // clear sensitive data
        return bytes;
    }

    private static byte[] hash(final char[] password, final byte[] salt)
            throws NoSuchAlgorithmException, InvalidKeySpecException {

        // from https://www.owasp.org/index.php/Hashing_Java
        final SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance(HASHALGORITHM);
        // the keylength parameter here is needed.
        final PBEKeySpec pbeKeySpec = new PBEKeySpec(password, salt, HASHNUMITERATION, HASHKEYLEN);
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
            SecretKey secretKey = (SecretKey) mKeyStore.getKey(SESSIONKEYALIAS, null);
            SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance("PBE");
            sessionPassword = ((PBEKeySpec) secretKeyFactory.getKeySpec(secretKey, PBEKeySpec.class)).getPassword();

            PBEKeySpec pbeKeySpec = new PBEKeySpec(masterPassword, randomBytes(SALTLEN), HASHNUMITERATION, HASHKEYLEN);
            SecretKey masterSecretKey = secretKeyFactory.generateSecret(pbeKeySpec);
            mKeyStore.setKeyEntry(MASTERKEYALIAS, masterSecretKey, sessionPassword, null);
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
            SecretKey secretKey = (SecretKey) mKeyStore.getKey(SESSIONKEYALIAS, null);
            SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance("PBE");
            sessionPassword = ((PBEKeySpec) secretKeyFactory.getKeySpec(secretKey, PBEKeySpec.class)).getPassword();

            secretKey = (SecretKey) mKeyStore.getKey(MASTERKEYALIAS, sessionPassword);
            if (secretKey == null) // MASTERKEYALIAS not found in keystore
                return null;
            return ((PBEKeySpec) secretKeyFactory.getKeySpec(secretKey, PBEKeySpec.class)).getPassword();
        } finally {
            if (sessionPassword != null)
                Arrays.fill(sessionPassword, ' '); // wipe sessionPassword
        }
    }

    public void deleteMasterPassword() throws KeyStoreException {
        // delete entry in keystore first
        mKeyStore.deleteEntry(MASTERKEYALIAS);
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

        final byte[] salt = randomBytes(SALTLEN);
        final byte[] iv = randomBytes(IVLEN);
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
            pbeKeySpec = new PBEKeySpec(masterPassword, salt, ENCRYPTIONNUMITERATION);

            SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance(SECRETKEYFACTORYALGORITHM);

            SecretKey secretKey = secretKeyFactory.generateSecret(pbeKeySpec);

            PBEParameterSpec pbeParameterSpec = new PBEParameterSpec(salt, ENCRYPTIONNUMITERATION, ivParameterSpec);
            Cipher encryptionCipher = Cipher.getInstance(SECRETKEYFACTORYALGORITHM);
            encryptionCipher.init(Cipher.ENCRYPT_MODE, secretKey, pbeParameterSpec);

            byte[] encryptedBytes = encryptionCipher.doFinal(clearBytes);
            return encode(encryptedBytes) + ENCODESTRINGSEPARATOR + encode(salt) + ENCODESTRINGSEPARATOR + encode(iv);
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

    // take a input of encrypted string with salt and IV (separated by ENCODESTRINGSEPARATOR,
    // returns decrypted content in a char[]
    // if master password is not properly stored in keystore, null will be returned
    public char[] decrypt(String encrpytedSecretWithSaltAndIV) throws IllegalArgumentException,
            NoSuchAlgorithmException, InvalidKeySpecException, KeyStoreException, UnrecoverableKeyException,
            NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException,
            IllegalBlockSizeException, BadPaddingException {
        String[] tokens = encrpytedSecretWithSaltAndIV.split(ENCODESTRINGSEPARATOR);
        if (tokens.length != 3)
            throw new IllegalArgumentException("Input should be three strings separated by " + ENCODESTRINGSEPARATOR);

        byte[] salt = Base64.getDecoder().decode(tokens[1]);
        byte[] iv = Base64.getDecoder().decode(tokens[2]);
        IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);

        char[] masterPassword = null;
        PBEKeySpec pbeKeySpec = null;
        try {
            masterPassword = getMasterPassword();
            if (masterPassword == null)
                return null;

            pbeKeySpec = new PBEKeySpec(masterPassword, salt, ENCRYPTIONNUMITERATION);

            SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance(SECRETKEYFACTORYALGORITHM);

            SecretKey secretKey = secretKeyFactory.generateSecret(pbeKeySpec);

            PBEParameterSpec pbeParameterSpec = new PBEParameterSpec(salt, ENCRYPTIONNUMITERATION, ivParameterSpec);
            Cipher decryptionCipher = Cipher.getInstance(SECRETKEYFACTORYALGORITHM);
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
            byte[] salt = randomBytes(SALTLEN);
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(null, password);  // use password as keyStore password, not sure if necessary
            pbeKeySpec = new PBEKeySpec(password, salt, HASHNUMITERATION, HASHKEYLEN);
            SecretKey secretKey = SecretKeyFactory.getInstance("PBE").generateSecret(pbeKeySpec);
            keyStore.setKeyEntry(SESSIONKEYALIAS, secretKey, null, null);
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
                    || (hashedPassword[i] != mHashedMasterPassword[i]))
                mismatch = true;
        }
        return !mismatch;
    }

    // Take an input string (encodedHashedMasterPassword:encodedSalt)
    // split, decode, and assigned to mHashedMasterPassword and mSalt
    // return true for success and false for failure
    public void setHashedMasterPassword(String encodedHashedMasterPasswordWithSalt) throws IllegalArgumentException {
        String[] stringArray = encodedHashedMasterPasswordWithSalt.split(ENCODESTRINGSEPARATOR);
        if (stringArray.length != 2)
            throw new IllegalArgumentException("Encoded and hashed master password should be "
                    + "two base64 strings separated by '" + ENCODESTRINGSEPARATOR + "'");

        mHashedMasterPassword = decode(stringArray[0]);
        mSalt = decode(stringArray[1]);
    }

    public String getEncodedHashedMasterPassword() {
        return encode(mHashedMasterPassword) + ENCODESTRINGSEPARATOR + encode(mSalt);
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
        byte[] newSalt = randomBytes(SALTLEN);
        byte[] hashedNewMasterPassword;
        hashedNewMasterPassword = hash(password, newSalt);
        putMasterPasswordInKeyStore(password);
        mHashedMasterPassword = hashedNewMasterPassword;
        mSalt = newSalt;
    }
}
