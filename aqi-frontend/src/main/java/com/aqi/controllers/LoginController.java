package com.aqi.controllers;

import com.aqi.utils.DatabaseConnection;
import com.aqi.utils.SceneManager;
import com.aqi.utils.UserSession;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

<<<<<<< Updated upstream
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
=======
// IMPORTANT: Added the BCrypt import!
import org.mindrot.jbcrypt.BCrypt;

>>>>>>> Stashed changes
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class LoginController {

    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private Label statusLabel;

    // Fixed: Only one @FXML annotation here
    @FXML
    private void handleLogin() {
        String email    = emailField.getText().trim();
        String password = passwordField.getText();

        if (email.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Please enter both email and password.");
            return;
        }

<<<<<<< Updated upstream
        String hashedPassword = hashPassword(password);
        String query = "SELECT user_id, username FROM users WHERE email = ? AND password_hash = ?";
=======
        // 1. ONLY search by email, and ask for the saved hash back
        String query = "SELECT user_id, username, password_hash FROM users WHERE email = ?";
>>>>>>> Stashed changes

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, email);
<<<<<<< Updated upstream
            pstmt.setString(2, hashedPassword);
=======

>>>>>>> Stashed changes
            ResultSet rs = pstmt.executeQuery();

            // 2. Did we find an account with that email?
            if (rs.next()) {
<<<<<<< Updated upstream
                String userId   = rs.getString("user_id");
                String username = rs.getString("username");

                UserSession.setUserId(userId);
                UserSession.setUsername(username);

                System.out.println("User " + username + " logged in successfully.");

                // If no health profile yet → go setup, else → Dashboard
                if (!hasHealthProfile(userId)) {
                    SceneManager.switchScene("/views/HealthProfile.fxml", "Health Profile");
                } else {
                    SceneManager.switchScene("/com/example/aqidashboard/dashboard-view.fxml", "Dashboard");
                }

            } else {
=======
                String savedHash = rs.getString("password_hash");

                // 3. Does the typed password match the saved hash?
                if (BCrypt.checkpw(password, savedHash)) {

                    // Login Success!
                    String userId = rs.getString("user_id");
                    UserSession.setUserId(userId);
                    System.out.println("User " + rs.getString("username") + " logged in!");
                    SceneManager.switchScene("/fxml/Dashboard.fxml");

                } else {
                    // Password was wrong
                    statusLabel.setText("Invalid email or password.");
                }
            } else {
                // Email was not found in the database
>>>>>>> Stashed changes
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

<<<<<<< Updated upstream
    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash password", e);
        }
=======
    @FXML
    private void goToAbout() {
        SceneManager.switchScene("/fxml/About.fxml");
>>>>>>> Stashed changes
    }

    @FXML private void goToSignUp() { SceneManager.switchScene("/fxml/SignUp.fxml"); }
    @FXML private void goToAbout()  { SceneManager.switchScene("/fxml/About.fxml"); }
}
