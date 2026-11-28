package ru.transferservis.app;

import android.app.Application;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import ru.transferservis.app.data.local.PassengerOrderSessionStore;
import ru.transferservis.app.data.model.OrderStatus;
import ru.transferservis.app.data.remote.ApiClient;
import ru.transferservis.app.domain.model.OrderReceipt;

public final class DebugApplication extends Application {
    private static final String LOG_TAG = "TransferServisTz02Qa";
    private static final String ORDER_ID = "tz02_qa_synthetic_order";

    @Override
    public void onCreate() {
        super.onCreate();
        if (!BuildConfig.PASSENGER_TZ02_QA) {
            return;
        }

        File directory = getNoBackupFilesDir();
        File tester = new File(directory, "tz02-qa-tester.key");
        if (tester.isFile()) {
            try {
                ApiClient.configureTz02QaTesterToken(readSmallFile(tester).trim());
            } catch (IOException error) {
                Log.w(LOG_TAG, "TZ02_TESTER_READ_FAILED");
            }
        }

        File session = new File(directory, "tz02-qa-session.json");
        if (!session.isFile()) {
            return;
        }
        try {
            JSONObject input = new JSONObject(readSmallFile(session));
            String orderId = input.getString("orderId");
            if (!ORDER_ID.equals(orderId)) {
                throw new IllegalArgumentException("Unexpected QA order");
            }
            new PassengerOrderSessionStore(this).save(new OrderReceipt(orderId, OrderStatus.NEW,
                    System.currentTimeMillis(), input.getString("accessToken"),
                    input.getLong("accessExpiresAt")));
            Log.i(LOG_TAG, "TZ02_SESSION_IMPORTED");
        } catch (IOException | JSONException | IllegalArgumentException | IllegalStateException error) {
            Log.w(LOG_TAG, "TZ02_SESSION_IMPORT_FAILED");
        } finally {
            if (!session.delete()) {
                Log.w(LOG_TAG, "TZ02_SESSION_INPUT_DELETE_FAILED");
            }
        }
    }

    private static String readSmallFile(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (output.size() + read > 4096) {
                    throw new IOException("QA input too large");
                }
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
