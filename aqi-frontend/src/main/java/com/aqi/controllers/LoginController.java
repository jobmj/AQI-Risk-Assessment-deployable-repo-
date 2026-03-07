package com.aqi.controllers;

import com.aqi.utils.DatabaseConnection;
import com.aqi.utils.SceneManager;
import com.aqi.utils.UserSession;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;


import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class LoginController {

    @FXML private TextField     emailField;
    @FXML private PasswordField passwordField;
    @FXML private Label         statusLabel;
    @FXML private CheckBox rememberMeBox;

    private static final java.util.prefs.Preferences PREFS =
            java.util.prefs.Preferences.userNodeForPackage(LoginController.class);

    @FXML
    public void initialize() {
        String saved = PREFS.get("remembered_email", "");
        if (!saved.isEmpty()) {
            emailField.setText(saved);
            if (rememberMeBox != null) rememberMeBox.setSelected(true);
        }
    }

    @FXML
    private void handleLogin() {
        String email    = emailField.getText().trim();
        String password = passwordField.getText();

        if (email.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Please enter both email and password.");
            return;
        }

        // Search only by email to retrieve the hashed password
        String query = "SELECT user_id, username, password_hash FROM users WHERE email = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, email);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String savedHash = rs.getString("password_hash");
                String userId = rs.getString("user_id");
                String username = rs.getString("username");

                // Verify password using SHA-256
                if (sha256(password).equals(savedHash)) {
                    UserSession.setUserId(userId);
                    UserSession.setUsername(username);

                    System.out.println("User " + username + " logged in successfully.");
                    if (rememberMeBox != null && rememberMeBox.isSelected())
                        PREFS.put("remembered_email", email);
                    else PREFS.remove("remembered_email");

                    // Check if health profile exists to decide where to send the user
                    if (!hasHealthProfile(userId)) {
                        SceneManager.switchScene("/views/HealthProfile.fxml", "Health Profile");
                    } else {
                        // Ensure this path matches your actual dashboard FXML location
                        SceneManager.switchScene("/com/example/aqidashboard/dashboard-view.fxml", "Dashboard");
                    }
                } else {
                    statusLabel.setText("Invalid email or password.");
                }
            } else {
                statusLabel.setText("Invalid email or password.");
            }

        } catch (SQLException e) {
            statusLabel.setText("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean hasHealthProfile(String userId) {
        String query = "SELECT 1 FROM health_profiles WHERE user_id = ?::uuid";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, userId);
            return pstmt.executeQuery().next();
        } catch (SQLException e) {
            return false;
        }
    }

    @FXML
    private void handleForgotPassword() {
        statusLabel.setText("Please contact support to reset your password.");
    }

    // ── Guest login ───────────────────────────────────────────────
    @FXML
    private void handleGuestLogin() {
        UserSession.loginAsGuest();
        SceneManager.switchScene("/com/example/aqidashboard/dashboard-view.fxml", "Dashboard — Guest");
    }

    @FXML
    private void goToSignUp() {
        SceneManager.switchScene("/fxml/SignUp.fxml", "Sign Up");
    }

    @FXML
    private void goToAbout()  {
        SceneManager.switchScene("/fxml/About.fxml", "About Us");
    }

    private String sha256(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 error", e);
        }
    }
}