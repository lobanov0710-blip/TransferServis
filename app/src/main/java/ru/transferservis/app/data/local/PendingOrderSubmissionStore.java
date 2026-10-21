package ru.transferservis.app.data.local;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

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

    private static final Pattern QUOTE_ID = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
            Pattern.CASE_INSENSITIVE);

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

    /** Non-secret reference for the dispatcher, or null when the record is damaged. */
    public String getPendingQuoteId() {
        synchronized (LOCK) {
            return readQuoteIdLocked();
        }
    }

    /** Read the exact encrypted request. Never clear evidence when it cannot be read. */
    @Nullable
    public CreateOrderRequest loadPendingRequest() {
        synchronized (LOCK) {
            return readRequestLocked();
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

    @Nullable
    private String readQuoteIdLocked() {
        CreateOrderRequest request = readRequestLocked();
        return request == null ? null : request.getQuoteId();
    }

    @Nullable
    private CreateOrderRequest readRequestLocked() {
        try {
            String encodedIv = preferences.getString(IV, null);
            String encodedData = preferences.getString(DATA, null);
            if (encodedIv == null || encodedData == null) {
                return null;
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(),
                    new GCMParameterSpec(128, Base64.decode(encodedIv, Base64.NO_WRAP)));
            cipher.updateAAD(AAD);
            String json = new String(
                    cipher.doFinal(Base64.decode(encodedData, Base64.NO_WRAP)),
                    StandardCharsets.UTF_8);
            JsonObject decoded = gson.fromJson(json, JsonObject.class);
            if (decoded == null) {
                return null;
            }
            String quoteId = requiredString(decoded, "quoteId");
            String name = requiredString(decoded, "name");
            String phone = requiredString(decoded, "phone");
            String date = requiredString(decoded, "date");
            String comment = decoded.has("comment") && !decoded.get("comment").isJsonNull()
                    ? requiredString(decoded, "comment") : null;
            int phoneDigits = phone == null ? 0 : phone.replaceAll("\\D", "").length();
            if (quoteId == null || !QUOTE_ID.matcher(quoteId).matches()
                    || name == null || name.trim().isEmpty() || name.length() > 100
                    || phone == null || phone.length() > 40
                    || phoneDigits < 10 || phoneDigits > 15
                    || date == null || !date.matches("\\d{4}-\\d{2}-\\d{2}")
                    || (decoded.has("comment") && !decoded.get("comment").isJsonNull()
                            && comment == null)
                    || (comment != null && comment.length() > 2000)) {
                return null;
            }
            return new CreateOrderRequest(quoteId, name, phone, date, comment);
        } catch (GeneralSecurityException | IOException | RuntimeException error) {
            // Keep the encrypted marker and block a second new booking.
            return null;
        }
    }

    @Nullable
    private static String requiredString(JsonObject object, String name) {
        if (!object.has(name) || !object.get(name).isJsonPrimitive()
                || !object.get(name).getAsJsonPrimitive().isString()) {
            return null;
        }
        return object.get(name).getAsString();
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
