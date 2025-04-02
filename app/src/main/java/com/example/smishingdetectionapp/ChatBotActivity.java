package com.example.smishingdetectionapp;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class ChatBotActivity extends AppCompatActivity {
    private EditText userInput;
    private Button sendButton;
    private TextView chatDisplay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chatbot);

        userInput = findViewById(R.id.userInput);
        sendButton = findViewById(R.id.sendButton);
        chatDisplay = findViewById(R.id.chatDisplay);

        sendButton.setOnClickListener(v -> {
            String message = userInput.getText().toString();
            if (!message.isEmpty()) {
                chatDisplay.append("\nYou: " + message);
                getBotResponse(message);
                userInput.setText("");
            }
        });
    }

    private void getBotResponse(String userMessage) {
        String response;
        if (userMessage.toLowerCase().contains("hello")) {
            response = "Hi there! How can I assist you?";
        } else if (userMessage.toLowerCase().contains("help")) {
            response = "Sure! What do you need help with?";
        } else {
            response = "I'm still learning. Could you clarify?";
        }

        chatDisplay.append("\nBot: " + response);
    }
}
