package ru.transferservis.app.data.local;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.function.BooleanSupplier;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import ru.transferservis.app.domain.model.OrderReceipt;

public final class PassengerOrderSessionStore {

    // Booking and active-order screens use separate store instances.
    private static final Object SESSION_LOCK = new Object();

    private static final String PREFS_NAME =
            "passenger_order_session";

    private static final String KEYSTORE_PROVIDER =
            "AndroidKeyStore";

    private static final String KEY_ALIAS =
            "transfer_servis_passenger_order_access_v1";

    private static final String TRANSFORMATION =
            "AES/GCM/NoPadding";

    private static final int GCM_TAG_BITS =
            128;


    private static final String PREF_ORDER_ID =
            "order_id";

    private static final String PREF_ACCESS_EXPIRES_AT =
            "access_expires_at";

    private static final String PREF_TOKEN_IV =
            "token_iv";

    private static final String PREF_TOKEN_CIPHERTEXT =
            "token_ciphertext";


    private final SharedPreferences preferences;


    public PassengerOrderSessionStore(
            @NonNull Context context
    ) {

        preferences =
                context
                        .getApplicationContext()
                        .getSharedPreferences(
                                PREFS_NAME,
                                Context.MODE_PRIVATE
                        );
    }


    // ========================================
    // SAVE
    // ========================================

    public void save(
            @NonNull OrderReceipt receipt
    ) {
        synchronized (SESSION_LOCK) {
            saveLocked(receipt);
        }
    }

    public boolean saveIfCurrent(@NonNull OrderReceipt receipt, @NonNull BooleanSupplier accepted) {
        synchronized (SESSION_LOCK) {
            if (!accepted.getAsBoolean()) {
                return false;
            }
            saveLocked(receipt);
            return true;
        }
    }

    private void saveLocked(@NonNull OrderReceipt receipt) {

        String orderId =
                receipt
                        .getOrderId()
                        .trim();


        String accessToken =
                receipt
                        .getAccessToken()
                        .trim();


        long accessExpiresAt =
                receipt
                        .getAccessExpiresAtMillis();


        if (
                orderId.isEmpty()
                        || accessToken.isEmpty()
                        || accessExpiresAt <=
                        System.currentTimeMillis()
        ) {

            throw new IllegalArgumentException(
                    "Invalid passenger order session"
            );
        }


        try {

            SecretKey secretKey =
                    getOrCreateSecretKey();


            Cipher cipher =
                    Cipher.getInstance(
                            TRANSFORMATION
                    );


            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    secretKey
            );


            cipher.updateAAD(
                    buildAad(
                            orderId,
                            accessExpiresAt
                    )
            );


            byte[] ciphertext =
                    cipher.doFinal(
                            accessToken.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );


            byte[] iv =
                    cipher.getIV();


            boolean saved =
                    preferences
                            .edit()
                            .putString(
                                    PREF_ORDER_ID,
                                    orderId
                            )
                            .putLong(
                                    PREF_ACCESS_EXPIRES_AT,
                                    accessExpiresAt
                            )
                            .putString(
                                    PREF_TOKEN_IV,
                                    Base64.encodeToString(
                                            iv,
                                            Base64.NO_WRAP
                                    )
                            )
                            .putString(
                                    PREF_TOKEN_CIPHERTEXT,
                                    Base64.encodeToString(
                                            ciphertext,
                                            Base64.NO_WRAP
                                    )
                            )
                            .commit();


            if (!saved) {

                throw new IllegalStateException(
                        "Unable to persist passenger order session"
                );
            }

        } catch (
                GeneralSecurityException |
                IOException error
        ) {

            throw new IllegalStateException(
                    "Unable to encrypt passenger order session",
                    error
            );
        }
    }


    // ========================================
    // LOAD
    // ========================================

    @Nullable
    public Session load() {
        synchronized (SESSION_LOCK) {
            return loadLocked();
        }
    }

    @Nullable
    private Session loadLocked() {
        try {
            return readLocked();
        } catch (ClassCastException error) {
            // Damaged preferences are not a usable authenticated session.
            clearLocked();
            return null;
        }
    }

    @Nullable
    private Session readLocked() {

        String orderId =
                preferences.getString(
                        PREF_ORDER_ID,
                        null
                );


        long accessExpiresAt =
                preferences.getLong(
                        PREF_ACCESS_EXPIRES_AT,
                        0L
                );


        String encodedIv =
                preferences.getString(
                        PREF_TOKEN_IV,
                        null
                );


        String encodedCiphertext =
                preferences.getString(
                        PREF_TOKEN_CIPHERTEXT,
                        null
                );


        if (
                orderId == null
                        || orderId.trim().isEmpty()
                        || accessExpiresAt <= 0L
                        || encodedIv == null
                        || encodedCiphertext == null
        ) {

            return null;
        }


        if (
                System.currentTimeMillis() >=
                        accessExpiresAt
        ) {

            clear();

            return null;
        }


        try {

            byte[] iv =
                    Base64.decode(
                            encodedIv,
                            Base64.NO_WRAP
                    );


            byte[] ciphertext =
                    Base64.decode(
                            encodedCiphertext,
                            Base64.NO_WRAP
                    );


            SecretKey secretKey =
                    getOrCreateSecretKey();


            Cipher cipher =
                    Cipher.getInstance(
                            TRANSFORMATION
                    );


            cipher.init(
                    Cipher.DECRYPT_MODE,
                    secretKey,
                    new GCMParameterSpec(
                            GCM_TAG_BITS,
                            iv
                    )
            );


            cipher.updateAAD(
                    buildAad(
                            orderId,
                            accessExpiresAt
                    )
            );


            byte[] plaintext =
                    cipher.doFinal(
                            ciphertext
                    );


            String accessToken =
                    new String(
                            plaintext,
                            StandardCharsets.UTF_8
                    )
                            .trim();


            if (accessToken.isEmpty()) {

                clear();

                return null;
            }


            return new Session(
                    orderId,
                    accessToken,
                    accessExpiresAt
            );

        } catch (
                GeneralSecurityException |
                IOException |
                IllegalArgumentException error
        ) {

            clear();

            return null;
        }
    }


    // ========================================
    // CLEAR
    // ========================================

    public void clear() {
        synchronized (SESSION_LOCK) {
            clearLocked();
        }
    }

    public boolean isCurrent(@NonNull Session expected) {
        synchronized (SESSION_LOCK) {
            return matches(loadLocked(), expected);
        }
    }

    public boolean clearIfMatches(@NonNull Session expected) {
        synchronized (SESSION_LOCK) {
            if (!matches(loadLocked(), expected)) {
                return false;
            }
            clearLocked();
            return true;
        }
    }

    private static boolean matches(@Nullable Session current, @NonNull Session expected) {
        return current != null
                && current.orderId.equals(expected.orderId)
                && current.accessToken.equals(expected.accessToken)
                && current.accessExpiresAtMillis == expected.accessExpiresAtMillis;
    }

    private void clearLocked() {

        preferences
                .edit()
                .clear()
                .apply();
    }


    // ========================================
    // KEYSTORE
    // ========================================

    @NonNull
    private SecretKey getOrCreateSecretKey()
            throws GeneralSecurityException,
            IOException {

        KeyStore keyStore =
                KeyStore.getInstance(
                        KEYSTORE_PROVIDER
                );


        keyStore.load(
                null
        );


        KeyStore.Entry existingEntry =
                keyStore.getEntry(
                        KEY_ALIAS,
                        null
                );


        if (
                existingEntry instanceof
                        KeyStore.SecretKeyEntry
        ) {

            return (
                    (
                            KeyStore.SecretKeyEntry
                            )
                            existingEntry
            )
                    .getSecretKey();
        }


        KeyGenerator keyGenerator =
                KeyGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_AES,
                        KEYSTORE_PROVIDER
                );


        KeyGenParameterSpec spec =
                new KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT
                                | KeyProperties.PURPOSE_DECRYPT
                )
                        .setBlockModes(
                                KeyProperties.BLOCK_MODE_GCM
                        )
                        .setEncryptionPaddings(
                                KeyProperties.ENCRYPTION_PADDING_NONE
                        )
                        .setKeySize(
                                256
                        )
                        .build();


        keyGenerator.init(
                spec
        );


        return keyGenerator.generateKey();
    }


    // ========================================
    // AAD
    // ========================================

    @NonNull
    private byte[] buildAad(
            @NonNull String orderId,
            long accessExpiresAt
    ) {

        String value =
                orderId
                        + "\n"
                        + accessExpiresAt;


        return value.getBytes(
                StandardCharsets.UTF_8
        );
    }


    // ========================================
    // SESSION
    // ========================================

    public static final class Session {

        private final String orderId;

        private final String accessToken;

        private final long accessExpiresAtMillis;


        public Session(
                @NonNull String orderId,
                @NonNull String accessToken,
                long accessExpiresAtMillis
        ) {

            this.orderId =
                    orderId;

            this.accessToken =
                    accessToken;

            this.accessExpiresAtMillis =
                    accessExpiresAtMillis;
        }


        @NonNull
        public String getOrderId() {
            return orderId;
        }


        @NonNull
        public String getAccessToken() {
            return accessToken;
        }


        public long getAccessExpiresAtMillis() {
            return accessExpiresAtMillis;
        }


        public boolean isExpired() {

            return System.currentTimeMillis() >=
                    accessExpiresAtMillis;
        }
    }
}
