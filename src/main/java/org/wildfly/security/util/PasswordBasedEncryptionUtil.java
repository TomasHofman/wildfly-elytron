/*
 * JBoss, Home of Professional Open Source
 * Copyright 2016 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.security.util;


import static org.wildfly.security._private.ElytronMessages.log;

import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;
import java.text.Normalizer;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.wildfly.common.Assert;

/**
 * Password Based Encryption utility class for tooling.
 * It provides builder to build PBE masked strings for usage with {@link org.wildfly.security.credential.store.CredentialStore}.
 *
 * @author <a href="mailto:pskopek@redhat.com">Peter Skopek</a>
 */
public final class PasswordBasedEncryptionUtil {

    private static final String DEFAULT_PBE_ALGORITHM = "PBEWithHmacSHA1andAES_128";

    private final Cipher cipher;
    private final AlgorithmParameters algorithmParameters;
    private final Alphabet alphabet;

    PasswordBasedEncryptionUtil(Cipher cipher, AlgorithmParameters algorithmParameters, Alphabet alphabet) {
        this.cipher = cipher;
        this.alphabet = alphabet;
        this.algorithmParameters = algorithmParameters;
    }

    /**
     * Encrypt a payload and encode the result using {@link Alphabet} given to builder.
     * All necessary parameters are supplied through {@link Builder}.
     * @param payload secret to encrypt
     * @return String encrypted and encoded using given parameters
     * @throws GeneralSecurityException when problem occurs like non-existent algorithm or similar problems
     */
    public String encryptAndEncode(char[] payload) throws GeneralSecurityException {
        return encodeUsingAlphabet(encrypt(charArrayEncode(payload)));
    }

    /**
     * Decode given payload and decrypt it to original.
     * All necessary parameters are supplied through {@link Builder}.
     * @param encodedPayload text to decode and decrypt
     * @return decrypted secret
     * @throws GeneralSecurityException when problem occurs like non-existent algorithm or similar problems
     */
    public char[] decodeAndDecrypt(String encodedPayload) throws GeneralSecurityException {
        return byteArrayDecode(decrypt(decodeUsingAlphabet(encodedPayload)));
    }

    /**
     * Returns algorithm parameters used in the process of encryption.
     * Might be useful to store them separately after encryption happened. It depends on used algorithm.
     * @return {@link AlgorithmParameters} as generated by encryption process
     */
    public AlgorithmParameters getAlgorithmParameters() {
        return algorithmParameters;
    }

    /**
     * Returns encrypted IV (initial vector) as generated by AES algorithm in the process of encryption.
     * Other algorithms are not using it.
     * In case of no such data available it returns {@code null}.
     * It uses already set {@link Alphabet} to encode it.
     * @return encoded form of IV or {@code null} when not available
     */
    public String getEncodedIV() {
        if (algorithmParameters != null) {
            try {
                PBEParameterSpec spec = algorithmParameters.getParameterSpec(PBEParameterSpec.class);
                AlgorithmParameterSpec algSpec = spec.getParameterSpec();
                if (algSpec instanceof IvParameterSpec) {
                    return encodeUsingAlphabet(((IvParameterSpec) algSpec).getIV());
                }
            } catch (InvalidParameterSpecException e) {
                return null;
            }
        }
        return null;
    }

    private byte[] decodeUsingAlphabet(String payload) {
        ByteIterator byteIterator = isBase64(alphabet) ? CodePointIterator.ofString(payload).base64Decode(getAlphabet64(alphabet))
                : CodePointIterator.ofString(payload).base32Decode(getAlphabet32(alphabet));
        return byteIterator.drain();
    }

    private String encodeUsingAlphabet(byte[] payload) {
        CodePointIterator codePointIteratorIterator = isBase64(alphabet) ? ByteIterator.ofBytes(payload).base64Encode(getAlphabet64(alphabet))
                : ByteIterator.ofBytes(payload).base32Encode(getAlphabet32(alphabet));
        return codePointIteratorIterator.drainToString();
    }

    private static boolean isBase64(Alphabet alphabet) {
        return alphabet instanceof Alphabet.Base64Alphabet;
    }

    private static Alphabet.Base64Alphabet getAlphabet64(Alphabet alphabet) {
        return (Alphabet.Base64Alphabet) alphabet;
    }

    private static Alphabet.Base32Alphabet getAlphabet32(Alphabet alphabet) {
        return (Alphabet.Base32Alphabet) alphabet;
    }

    private byte[] encrypt(byte[] payload) throws GeneralSecurityException {
        return cipher.doFinal(payload);
    }

    private byte[] decrypt(byte[] payload) throws GeneralSecurityException {
        return cipher.doFinal(payload);
    }

    private static char[] byteArrayDecode(byte[] buffer) {
        return new String(buffer, StandardCharsets.UTF_8).toCharArray();
    }

    private static byte[] charArrayEncode(char[] buffer) {
        return Normalizer.normalize(new String(buffer), Normalizer.Form.NFKC).getBytes(StandardCharsets.UTF_8);
    }


    /**
     * Builder class to build {@link PasswordBasedEncryptionUtil} class with all necessary parameters to support
     * password based encryption algorithms.
     */
    public static class Builder {

        // algorithm names
        private String keyAlgorithm;
        private String transformation;
        private String parametersAlgorithm;
        // key parameters
        private int iteration = -1;
        private byte[] salt;
        private int keyLength = 0;
        private char[] password;   // actually initial key
        // cipher parameters
        private int cipherMode;
        private int cipherIteration = -1;
        private byte[] cipherSalt;

        private Provider provider;
        private Alphabet alphabet = Alphabet.Base64Alphabet.STANDARD;
        private IvParameterSpec ivSpec;
        private String encodedIV;
        private AlgorithmParameters algorithmParameters;

        /**
         * Set password to use to generate the encryption key
         * @param password the password
         * @return this Builder
         */
        public Builder password(char[] password) {
            this.password = password;
            return this;
        }

        /**
         * Set password to use to generate the encryption key
         * @param password the password
         * @return this Builder
         */
        public Builder password(String password) {
            this.password = password.toCharArray();
            return this;
        }

        /**
         * Set initialization vector for use with AES algorithms
         * @param iv the raw IV
         * @return this Builder
         */
        public Builder iv(byte[] iv) {
            ivSpec = new IvParameterSpec(iv);
            return this;
        }

        /**
         * Set initialization vector for use with AES algorithms
         * @param encodedIV IV encoded using {@link Alphabet} set in this builder (or default)
         * @return this Builder
         */
        public Builder iv(String encodedIV) {
            this.encodedIV = encodedIV;
            return this;
        }

        /**
         * Transformation name to use as {@code Cipher} parameter.
         * @param transformation the name of transformation
         * @return this Builder
         */
        public Builder transformation(String transformation) {
            this.transformation = transformation;
            return this;
        }

        /**
         * Set the name of parameter's algorithm to initialize the {@code Cipher}
         * @param parametersAlgorithm the name of parameter's algorithm
         * @return this Builder
         */
        public Builder parametersAlgorithm(String parametersAlgorithm) {
            this.parametersAlgorithm = parametersAlgorithm;
            return this;
        }

        /**
         * Set salt for key derivation.
         * @param salt the salt
         * @return this Builder
         */
        public Builder salt(String salt) {
            this.salt = salt.getBytes(StandardCharsets.UTF_8);
            return this;
        }

        /**
         * Set salt for key derivation.
         * @param salt the salt
         * @return this Builder
         */
        public Builder salt(byte[] salt) {
            this.salt = salt;
            return this;
        }

        /**
         * Set number of iteration for key derivation.
         * @param iteration the number of iterations
         * @return this Builder
         */
        public Builder iteration(int iteration) {
            this.iteration = iteration;
            return this;
        }

        /**
         * Set the key derivation algorithm.
         * @param keyAlgorithm the algorithm
         * @return this Builder
         */
        public Builder keyAlgorithm(String keyAlgorithm) {
            this.keyAlgorithm = keyAlgorithm;
            return this;
        }

        /**
         * Set the key length.
         * @param keyLength the length
         * @return this Builder
         */
        public Builder keyLength(int keyLength) {
            this.keyLength = keyLength;
            return this;
        }

        /**
         * Set the number of iterations for {@code Cipher}
         * @param cipherIteration number of iterations
         * @return this Builder
         */
        public Builder cipherIteration(int cipherIteration) {
            this.cipherIteration = cipherIteration;
            return this;
        }

        /**
         * Set salt for the {@code Cipher}
         * @param cipherSalt the salt
         * @return this Builder
         */
        public Builder cipherSalt (byte[] cipherSalt) {
            this.cipherSalt = cipherSalt;
            return this;
        }

        /**
         * Set salt for the {@code Cipher}
         * @param cipherSalt the salt
         * @return this Builder
         */
        public Builder cipherSalt (String cipherSalt) {
            this.cipherSalt = cipherSalt.getBytes(StandardCharsets.UTF_8);
            return this;
        }

        /**
         * Set the JCA provider which contains all classes needed by built utility class.
         * @param provider the provider
         * @return this Builder
         */
        public Builder provider(Provider provider) {
            this.provider = provider;
            return this;
        }

        /**
         * Set the JCA provider name which contains all classes needed by built utility class.
         * @param providerName the provider name
         * @return this Builder
         */
        public Builder provider(String providerName) {
            Assert.checkNotNullParam("providerName", providerName);
            provider = Security.getProvider(providerName);
            if (provider == null) {
                throw log.securityProviderDoesnExist(providerName);
            }
            return this;
        }

        /**
         * Set the alphabet to encode/decode result of encryption/decryption.
         * @param alphabet the {@link Alphabet} instance
         * @return this Builder
         */
        public Builder alphabet(Alphabet alphabet) {
            this.alphabet = alphabet;
            return this;
        }

        /**
         * Set encryption mode for chosen {@code Cipher}
         * @return this Builder
         */
        public Builder encryptMode() {
            cipherMode = Cipher.ENCRYPT_MODE;
            return this;
        }

        /**
         * Set decryption mode for chosen {@code Cipher}
         * @return this Builder
         */
        public Builder decryptMode() {
            cipherMode = Cipher.DECRYPT_MODE;
            return this;
        }

        /**
         * Set algorithm parameters for {@code Cipher} initialization.
         * @param algorithmParameters the algorithm parameters instance in form required by the used {@code Cipher}
         * @return this Builder
         */
        public Builder algorithmParameters(AlgorithmParameters algorithmParameters) {
            if (this.algorithmParameters == null) {
                this.algorithmParameters = algorithmParameters;
            }
            return this;
        }

        private Cipher createAndInitCipher(SecretKey secretKey) throws GeneralSecurityException {
            Cipher cipher = provider == null ? Cipher.getInstance(transformation)
                    : Cipher.getInstance(transformation, provider);
            if (cipherMode == Cipher.ENCRYPT_MODE) {
                cipher.init(cipherMode, secretKey, generateAlgorithmParameters(parametersAlgorithm, cipherIteration, cipherSalt, null, provider));
                algorithmParameters = cipher.getParameters();
            } else {
                if (algorithmParameters != null) {
                    cipher.init(cipherMode, secretKey, algorithmParameters);
                } else {
                    cipher.init(cipherMode, secretKey, generateAlgorithmParameters(parametersAlgorithm, cipherIteration, cipherSalt, ivSpec, provider));
                }
            }
            return cipher;
        }

        private static AlgorithmParameters generateAlgorithmParameters(String algorithm, int iterationCount, byte[] salt, IvParameterSpec ivSpec, Provider provider) throws GeneralSecurityException {
            AlgorithmParameters tempParams = provider == null ? AlgorithmParameters.getInstance(algorithm)
                    : AlgorithmParameters.getInstance(algorithm, provider);
            PBEParameterSpec pbeParameterSpec = ivSpec != null ? new PBEParameterSpec(salt, iterationCount, ivSpec)
                    : new PBEParameterSpec(salt, iterationCount);
            tempParams.init(pbeParameterSpec);
            return tempParams;
        }

        private SecretKey deriveSecretKey() throws GeneralSecurityException {
            SecretKeyFactory secretKeyFactory;
            try {
                if (provider != null) {
                    secretKeyFactory = SecretKeyFactory.getInstance(keyAlgorithm, provider);
                } else {
                    secretKeyFactory = SecretKeyFactory.getInstance(keyAlgorithm);
                }
            } catch (NoSuchAlgorithmException e) {
                throw log.noSuchKeyAlgorithm(keyAlgorithm, e);
            }

            PBEKeySpec pbeKeySpec = keyLength == 0 ? new PBEKeySpec(password, salt, iteration)
                    : new PBEKeySpec(password, salt, iteration, keyLength);
            SecretKey partialKey = secretKeyFactory.generateSecret(pbeKeySpec);
            return new SecretKeySpec(partialKey.getEncoded(), transformation);
        }

        /**
         * Builds PBE utility class instance
         * @return PBE utility class instance {@link PasswordBasedEncryptionUtil}
         * @throws GeneralSecurityException when something goes wrong while initializing encryption related objects
         */
        public PasswordBasedEncryptionUtil build() throws GeneralSecurityException {
            if (iteration <= -1) {
                throw log.iterationCountNotSpecified();
            }
            if (salt == null) {
                throw log.saltNotSpecified();
            }
            if (password == null || password.length == 0) {
                throw log.initialKeyNotSpecified();
            }
            if (keyAlgorithm == null) {
                keyAlgorithm = DEFAULT_PBE_ALGORITHM;
            }
            if (transformation == null) {
                    transformation = keyAlgorithm;
            }
            if (parametersAlgorithm == null) {
                parametersAlgorithm = keyAlgorithm;
            }
            if (cipherSalt == null) {
                cipherSalt = salt;
            }
            if (cipherIteration == -1) {
                cipherIteration = iteration;
            }
            if (ivSpec == null && encodedIV != null) {
                ByteIterator byteIterator = isBase64(alphabet) ? CodePointIterator.ofString(encodedIV).base64Decode(getAlphabet64(alphabet))
                        : CodePointIterator.ofString(encodedIV).base32Decode(getAlphabet32(alphabet));
                ivSpec = new IvParameterSpec(byteIterator.drain());
            }

            return new PasswordBasedEncryptionUtil(createAndInitCipher(deriveSecretKey()), algorithmParameters, alphabet);
        }
    }

}
