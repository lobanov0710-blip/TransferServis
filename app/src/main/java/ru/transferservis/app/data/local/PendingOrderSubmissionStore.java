package ru.transferservis.app.data.local;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import ru.transferservis.app.data.remote.dto.CreateOrderRequest;

/**
 * Stores one unresolved booking submission BEFORE sending the POST.
 * An unresolved submission is never automatically replayed.
 *
 * The entire request (including passenger details) is encrypted with a dedicated
 * AndroidKeyStore AES-GCM key. Damaged or unreadable data remain fail-closed:
 * the user must not accidentally create another order.
 */
public final class PendingOrderSubmissionStore {
    private static final Object LOCK = new Object();
    private static final String PREFS = "passenger_pending_order";
    private static final String KEY_ALIAS = "transfer_servis_pending_order_v1";
    private static final String IV = "iv";
    private static final String DATA = "ciphertext";
    private static final byte[] AAD =
            "transfer-servis:pending-order:v1".getBytes(StandardCharsets.UTF_8);

    private final SharedPreferences preferences;
    private final Gson gson = new Gson();

    public PendingOrderSubmissionStore(@NonNull Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean hasPending() {
        synchronized (LOCK) {
            return preferences.contains(IV) || preferences.contains(DATA);
        }
    }

    /**
     * @return false if another attempt exists; never overwrites unresolved work.
     * @throws IllegalStateException when a durable encrypted write is impossible.
     */
    public boolean begin(@NonNull CreateOrderRequest request) {
        synchronized (LOCK) {
            if (hasPending()) {
                return false;
            }
            try {
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
                cipher.updateAAD(AAD);
                byte[] encrypted = cipher.doFinal(
                        gson.toJson(request).getBytes(StandardCharsets.UTF_8));
                boolean saved = preferences.edit()
                        .putString(IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                        .putString(DATA, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                        .commit();
                if (!saved) {
                    throw new IllegalStateException("Pending booking not persisted");
                }
                return true;
            } catch (GeneralSecurityException | IOException error) {
                throw new IllegalStateException("Cannot protect pending booking", error);
            }
        }
    }

    /** Remove only the exact submission whose confirmed result is being handled. */
    public boolean clearIfQuoteId(@NonNull String expectedQuoteId) {
        synchronized (LOCK) {
            String storedQuoteId = readQuoteIdLocked();
            if (storedQuoteId == null || !storedQuoteId.equals(expectedQuoteId)) {
                return false;
            }
            return preferences.edit().clear().commit();
        }
    }

    private String readQuoteIdLocked() {
        String encodedIv;
        String encodedData;
        try {
            encodedIv = preferences.getString(IV, null);
            encodedData = preferences.getString(DATA, null);
        } catch (ClassCastException error) {
            return null;
        }
        if (encodedIv == null || encodedData == null) {
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(),
                    new GCMParameterSpec(128, Base64.decode(encodedIv, Base64.NO_WRAP)));
            cipher.updateAAD(AAD);
            String json = new String(
                    cipher.doFinal(Base64.decode(encodedData, Base64.NO_WRAP)),
                    StandardCharsets.UTF_8);
            JsonObject decoded = gson.fromJson(json, JsonObject.class);
            if (decoded == null || !decoded.has("quoteId")
                    || !decoded.get("quoteId").isJsonPrimitive()) {
                return null;
            }
            String quoteId = decoded.get("quoteId").getAsString();
            return quoteId.isEmpty() ? null : quoteId;
        } catch (GeneralSecurityException | IOException
                 | IllegalArgumentException error) {
            // Preserve the evidence and block a new submission. Never silently clear it.
            return null;
        }
    }

    private SecretKey getOrCreateKey() throws GeneralSecurityException, IOException {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        KeyStore.Entry existing = store.getEntry(KEY_ALIAS, null);
        if (existing instanceof KeyStore.SecretKeyEntry) {
            return ((KeyStore.SecretKeyEntry) existing).getSecretKey();
        }
        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return generator.generateKey();
    }
}
