package com.example.smishingdetectionapp.chat;

import android.database.Cursor;
import android.util.Log;

import com.example.smishingdetectionapp.detections.DatabaseAccess;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import okhttp3.*;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.concurrent.TimeUnit;

public class OllamaClient {
    // Base URL for the Ollama API endpoint
    private static final String BASE_URL = "https://618f-112-134-231-34.ngrok-free.app";
    private static final String GEMINI_API_KEY = "AIzaSyB1GdxZqRX89QSepkNmC20W57oIdGd9H18";
    private static final String GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + GEMINI_API_KEY;

    // HTTP client configured with extended timeouts for API calls
    private final OkHttpClient client;
    // Database access instance for querying local storage
    private final DatabaseAccess databaseAccess;
    // Callback interface for handling asynchronous responses
    public interface ResponseCallback {
        void onResponse(String response);
    }

    // Constructor for the OllamaClient class
    public OllamaClient(DatabaseAccess databaseAccess) {
        this.databaseAccess = databaseAccess;
        // Configure the OkHttpClient with extended timeouts
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    // Method to handle database queries for reports and detections
    private String handleDatabaseQuery(String message) {
        message = message.toLowerCase();
        if (message.contains("reports") || message.contains("detections")) {
            databaseAccess.open();

            Cursor cursor = null;

            try {

                if (message.contains("reports")) {
                    if (message.contains("today")) {
                        String today = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                                .format(new Date());
                        cursor = databaseAccess.getReportsForDate(today);
                        return formatResults(cursor, "reports", today);
                    } else if (message.contains("yesterday")) {
                        String yesterday = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                                .format(new Date(System.currentTimeMillis() - 24*60*60*1000));
                        cursor = databaseAccess.getReportsForDate(yesterday);
                        return formatResults(cursor, "reports", yesterday);
                    } else {

                        String datePattern = "(0[1-9]|[12][0-9]|3[01])[-/](0[1-9]|1[012])[-/](\\d{4})";
                        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(datePattern);
                        java.util.regex.Matcher matcher = pattern.matcher(message);

                        if (matcher.find()) {
                            String specificDate = matcher.group().replace("/", "-");

                            specificDate = normalizeDate(specificDate);

                            cursor = databaseAccess.getReportsForSpecificDate(specificDate);
                            if (cursor != null && cursor.getCount() > 0) {
                                return formatResults(cursor, "reports", specificDate);
                            } else {
                                return "No reports found for " + specificDate;
                            }
                        } else {
                            cursor = databaseAccess.getAllReports();
                            return formatResults(cursor, "reports", null);
                        }
                    }
                }
            return "I can help you with 'reports' or 'detections'. Try asking for 'today's reports' or 'all detections'.";
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
                databaseAccess.close();
            }
        }
        return null;
    }

    private String normalizeDate(String date) {

        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
            SimpleDateFormat outputFormat = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
            Date parsedDate = inputFormat.parse(date);
            return outputFormat.format(parsedDate);
        } catch (Exception e) {
            return date;
        }
    }

    private String formatResults(Cursor cursor, String type, String date) {

    if (cursor != null && cursor.moveToFirst()) {

        StringBuilder result = new StringBuilder();

        if (date != null) {
            result.append("Here are the ").append(type).append(" for ").append(date).append(":\n\n");
        } else {
            result.append("Here are all ").append(type).append(":\n\n");
        }

        do {

            String phoneNumber = cursor.getString(cursor.getColumnIndex(DatabaseAccess.DatabaseOpenHelper.KEY_PHONENUMBER));

            String messageDate = cursor.getString(cursor.getColumnIndex(DatabaseAccess.DatabaseOpenHelper.KEY_DATE));

            String messageText = cursor.getString(cursor.getColumnIndex(DatabaseAccess.DatabaseOpenHelper.KEY_MESSAGE));

            result.append("📱 Phone: ").append(phoneNumber)
                  .append("\n📅 Date: ").append(messageDate)
                  .append("\n💬 Message: ").append(messageText)
                  .append("\n\n");
        } while (cursor.moveToNext());

        return result.toString();
    } else {
        if (date != null) {
            return "No " + type + " found for " + date;
        } else {
            return "No " + type + " found";
        }
    }
}

// Main method to get responses from either database or Ollama API

    public void getResponse(String message, ResponseCallback callback) {
        new Thread(() -> {
            // Check if the message can be answered from the database
            String dbResponse = handleDatabaseQuery(message);
            if (dbResponse != null) {
                callback.onResponse(dbResponse);
                return;
            }

            try {
                // Create request body as per Gemini API format
                JSONObject jsonBody = new JSONObject();
                JSONObject content = new JSONObject();
                content.put("text", message);

                JSONObject part = new JSONObject();
                part.put("parts", new JSONArray().put(content));

                jsonBody.put("contents", new JSONArray().put(part));

                RequestBody body = RequestBody.create(
                        jsonBody.toString(),
                        MediaType.parse("application/json")
                );

                Request request = new Request.Builder()
                        .url(GEMINI_URL)
                        .post(body)
                        .addHeader("Content-Type", "application/json")
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String responseBody = response.body().string();
                        Log.d("OllamaClient", "Gemini API Response: " + responseBody);  

                        JSONObject jsonResponse = new JSONObject(responseBody);
                        JSONArray candidates = jsonResponse.getJSONArray("candidates");

                        if (candidates.length() > 0) {
                            String botResponse = candidates.getJSONObject(0)
                                    .getJSONObject("content")
                                    .getJSONArray("parts")
                                    .getJSONObject(0)
                                    .getString("text");

                            callback.onResponse(botResponse);
                        } else {
                            callback.onResponse("Sorry, I couldn't process your request.");
                        }
                    } else {
                        callback.onResponse("Sorry, an error occurred while fetching response.");
                    }
                }
            } catch (Exception e) {
                Log.e("OllamaClient", "Error getting response from Gemini", e);
                callback.onResponse("Sorry, an error occurred.");
            }
        }).start();
    }

}