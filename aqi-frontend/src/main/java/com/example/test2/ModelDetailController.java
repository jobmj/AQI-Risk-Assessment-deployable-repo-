package com.example.test2;

import com.aqi.utils.SceneManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.*;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

public class ModelDetailController {

    private static final String ML_SERVER = "http://localhost:5000";

    // ── State passed from prediction page ────────────────────────
    private String modelName;       // "xgboost" / "randomforest" / "lightgbm"
    private String modelLabel;      // "XGBoost" etc.
    private String modelColor;      // "#10b981" etc.
    private JsonNode lastAqiData;   // full AQI JSON from last prediction

    private final HttpClient   http   = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    // ── UI refs ───────────────────────────────────────────────────
    private VBox      sidebarMenu;
    private ImageView plotImageView;
    private Label     plotTitleLabel;
    private Label     statusLabel;
    private StackPane plotArea;
    private String    activePlot = "feature_importance";

    // Plot type definitions: key → display label
    private static final Map<String, String> PLOTS = new LinkedHashMap<>();
    static {
        PLOTS.put("feature_importance", "📊  Feature Importance");
        PLOTS.put("shap",               "🔍  SHAP Summary");
        PLOTS.put("pdp",                "📈  Partial Dependence");
        PLOTS.put("roc",                "📉  ROC Curve");
        PLOTS.put("confusion",          "🟦  Confusion Matrix");
        PLOTS.put("precision_recall",   "🎯  Precision-Recall");
        PLOTS.put("learning_curve",     "📚  Learning Curve");
    }

    // ── Entry point ───────────────────────────────────────────────
    /**
     * Called by MainController before switching to this page.
     * modelNameInternal: "xgboost" / "randomforest" / "lightgbm"
     * aqiData: the JsonNode from the last /aqi call (contains pm25, pm10, etc.)
     */
    public void init(String modelNameInternal, JsonNode aqiData) {
        this.modelName  = modelNameInternal;
        this.lastAqiData = aqiData;
        this.modelLabel = switch (modelNameInternal) {
            case "randomforest" -> "Random Forest";
            case "lightgbm"     -> "LightGBM";
            default             -> "XGBoost";
        };
        this.modelColor = switch (modelNameInternal) {
            case "randomforest" -> "#f59e0b";
            case "lightgbm"     -> "#8b5cf6";
            default             -> "#10b981";
        };
    }

    // ── Build and return the scene root ──────────────────────────
    public VBox buildPage() {
        VBox root = new VBox(0);
        root.setStyle("-fx-background-color: #0f172a;");
        root.setFillWidth(true);

        // ── Top navbar ────────────────────────────────────────────
        HBox navbar = buildNavbar();
        root.getChildren().add(navbar);

        // ── Body: sidebar + plot area ─────────────────────────────
        HBox body = new HBox(0);
        VBox.setVgrow(body, Priority.ALWAYS);

        VBox sidebar  = buildSidebar();
        VBox plotPane = buildPlotPane();
        HBox.setHgrow(plotPane, Priority.ALWAYS);

        body.getChildren().addAll(sidebar, plotPane);
        root.getChildren().add(body);

        // Load first plot after layout
        Platform.runLater(() -> selectPlot("feature_importance"));

        return root;
    }

    // ── Navbar ────────────────────────────────────────────────────
    private HBox buildNavbar() {
        HBox nav = new HBox(14);
        nav.setAlignment(Pos.CENTER_LEFT);
        nav.setStyle(
            "-fx-background-color: #1e293b;" +
            "-fx-padding: 14 28;" +
            "-fx-border-color: #334155 transparent transparent transparent;" +
            "-fx-border-width: 0 0 1 0;");
        nav.setEffect(new DropShadow(12, 0, 3, Color.color(0,0,0,0.3)));

        // Back chevron
        Label chevron = new Label("‹");
        chevron.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: " + modelColor + ";");
        StackPane chevronCircle = new StackPane(chevron);
        chevronCircle.setPrefSize(34, 34);
        chevronCircle.setStyle(
            "-fx-background-color: rgba(99,102,241,0.12); -fx-background-radius: 17; -fx-cursor: hand;");
        chevronCircle.setOnMouseClicked(e -> goBack());
        chevronCircle.setOnMouseEntered(e ->
            chevronCircle.setStyle("-fx-background-color: rgba(99,102,241,0.25); -fx-background-radius: 17; -fx-cursor: hand;"));
        chevronCircle.setOnMouseExited(e ->
            chevronCircle.setStyle("-fx-background-color: rgba(99,102,241,0.12); -fx-background-radius: 17; -fx-cursor: hand;"));

        Label backLbl = new Label("Prediction");
        backLbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #64748b; -fx-cursor: hand;");
        backLbl.setOnMouseClicked(e -> goBack());

        Label sep = new Label("›");
        sep.setStyle("-fx-font-size: 13px; -fx-text-fill: #475569;");

        // Colored model badge
        Label modelBadge = new Label("  " + modelLabel + "  ");
        modelBadge.setStyle(
            "-fx-background-color: " + modelColor + "22;" +
            "-fx-text-fill: " + modelColor + ";" +
            "-fx-font-size: 13px; -fx-font-weight: bold;" +
            "-fx-background-radius: 8; -fx-padding: 4 10;");

        Label pageTitle = new Label("Model Analysis");
        pageTitle.setStyle("-fx-font-size: 13px; -fx-text-fill: #e2e8f0;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Model info pill
        Label infoPill = new Label("Flask ML  ·  Port 5000");
        infoPill.setStyle(
            "-fx-font-size: 11px; -fx-text-fill: #475569;" +
            "-fx-background-color: #1e293b; -fx-border-color: #334155;" +
            "-fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 4 12;");

        nav.getChildren().addAll(chevronCircle, backLbl, sep, modelBadge, pageTitle, spacer, infoPill);
        return nav;
    }

    // ── Sidebar ───────────────────────────────────────────────────
    private VBox buildSidebar() {
        VBox sidebar = new VBox(0);
        sidebar.setPrefWidth(220);
        sidebar.setMinWidth(220);
        sidebar.setMaxWidth(220);
        sidebar.setStyle(
            "-fx-background-color: #1e293b;" +
            "-fx-border-color: transparent #334155 transparent transparent;" +
            "-fx-border-width: 0 1 0 0;");

        // Header
        VBox header = new VBox(4);
        header.setStyle("-fx-padding: 22 20 16 20; -fx-border-color: transparent transparent #334155 transparent; -fx-border-width: 0 0 1 0;");
        Label title = new Label("Analysis Type");
        title.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #475569; -fx-letter-spacing: 1px;");
        Label sub = new Label(modelLabel);
        sub.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: " + modelColor + ";");
        header.getChildren().addAll(title, sub);
        sidebar.getChildren().add(header);

        // Menu items
        sidebarMenu = new VBox(2);
        sidebarMenu.setStyle("-fx-padding: 10 8;");

        for (Map.Entry<String, String> entry : PLOTS.entrySet()) {
            String key   = entry.getKey();
            String label = entry.getValue();
            VBox item    = makeSidebarItem(key, label);
            sidebarMenu.getChildren().add(item);
        }

        sidebar.getChildren().add(sidebarMenu);

        // Bottom info
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        Label note = new Label("Plots use real AQI\nvalues from search");
        note.setStyle("-fx-font-size: 10px; -fx-text-fill: #334155; -fx-text-alignment: center; -fx-padding: 0 0 16 0;");
        note.setWrapText(true);
        note.setMaxWidth(180);
        VBox bottomBox = new VBox(note);
        bottomBox.setAlignment(Pos.BOTTOM_CENTER);
        VBox.setVgrow(bottomBox, Priority.ALWAYS);
        sidebar.getChildren().addAll(spacer, bottomBox);

        return sidebar;
    }

    private VBox makeSidebarItem(String key, String label) {
        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #94a3b8; -fx-font-family: 'Segoe UI';");
        lbl.setMaxWidth(Double.MAX_VALUE);

        VBox item = new VBox(lbl);
        item.setPadding(new Insets(10, 16, 10, 16));
        item.setStyle("-fx-background-radius: 10; -fx-cursor: hand;");

        item.setOnMouseEntered(e -> {
            if (!key.equals(activePlot))
                item.setStyle("-fx-background-color: #334155; -fx-background-radius: 10; -fx-cursor: hand;");
        });
        item.setOnMouseExited(e -> {
            if (!key.equals(activePlot))
                item.setStyle("-fx-background-radius: 10; -fx-cursor: hand;");
        });
        item.setOnMouseClicked(e -> selectPlot(key));

        // Store key as ID for easy lookup
        item.setId("sidebar_" + key);
        return item;
    }

    // ── Plot pane (right side) ────────────────────────────────────
    private VBox buildPlotPane() {
        VBox pane = new VBox(0);
        pane.setStyle("-fx-background-color: #0f172a;");
        pane.setFillWidth(true);

        // Plot title bar
        HBox titleBar = new HBox(12);
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.setStyle("-fx-padding: 18 28; -fx-border-color: transparent transparent #1e293b transparent; -fx-border-width: 0 0 1 0;");

        plotTitleLabel = new Label("Feature Importance");
        plotTitleLabel.setStyle("-fx-font-size: 17px; -fx-font-weight: bold; -fx-text-fill: #e2e8f0; -fx-font-family: 'Segoe UI';");

        statusLabel = new Label("Click a plot type to load");
        statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #475569;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Save button
        Button saveBtn = new Button("💾  Save PNG");
        saveBtn.setStyle(
            "-fx-background-color: #1e293b; -fx-text-fill: #94a3b8;" +
            "-fx-background-radius: 10; -fx-border-color: #334155;" +
            "-fx-border-radius: 10; -fx-font-size: 12px;" +
            "-fx-padding: 7 14; -fx-cursor: hand;");
        saveBtn.setOnAction(e -> savePlot());

        titleBar.getChildren().addAll(plotTitleLabel, statusLabel, spacer, saveBtn);
        pane.getChildren().add(titleBar);

        // Plot image area
        plotArea = new StackPane();
        plotArea.setStyle("-fx-background-color: #0f172a;");
        VBox.setVgrow(plotArea, Priority.ALWAYS);

        plotImageView = new ImageView();
        plotImageView.setPreserveRatio(true);
        plotImageView.setSmooth(true);
        plotImageView.fitWidthProperty().bind(plotArea.widthProperty().subtract(60));
        plotImageView.fitHeightProperty().bind(plotArea.heightProperty().subtract(40));

        // Placeholder
        Label placeholder = new Label("Select an analysis type\nfrom the sidebar");
        placeholder.setStyle("-fx-font-size: 15px; -fx-text-fill: #334155; -fx-text-alignment: center;");
        placeholder.setWrapText(true);

        // Loading spinner (animated dots)
        Label spinner = new Label("⟳");
        spinner.setStyle("-fx-font-size: 40px; -fx-text-fill: " + modelColor + "; -fx-opacity: 0;");
        spinner.setId("spinner");
        RotateTransition rt = new RotateTransition(Duration.seconds(1.2), spinner);
        rt.setByAngle(360); rt.setCycleCount(Animation.INDEFINITE);
        rt.play();

        plotArea.getChildren().addAll(placeholder, spinner, plotImageView);
        pane.getChildren().add(plotArea);

        return pane;
    }

    // ── Select and load a plot ────────────────────────────────────
    private void selectPlot(String key) {
        activePlot = key;

        // Update sidebar active state
        for (javafx.scene.Node node : sidebarMenu.getChildren()) {
            if (node instanceof VBox item) {
                boolean active = item.getId() != null && item.getId().equals("sidebar_" + key);
                if (active) {
                    item.setStyle(
                        "-fx-background-color: " + modelColor + "22;" +
                        "-fx-background-radius: 10; -fx-cursor: hand;" +
                        "-fx-border-color: " + modelColor + " transparent transparent transparent;" +
                        "-fx-border-width: 0 0 0 3; -fx-border-radius: 0;");
                    Label lbl = (Label) item.getChildren().get(0);
                    lbl.setStyle("-fx-font-size: 13px; -fx-text-fill: " + modelColor + "; -fx-font-weight: bold; -fx-font-family: 'Segoe UI';");
                } else {
                    item.setStyle("-fx-background-radius: 10; -fx-cursor: hand;");
                    Label lbl = (Label) item.getChildren().get(0);
                    lbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #94a3b8; -fx-font-family: 'Segoe UI';");
                }
            }
        }

        // Update title
        String label = PLOTS.getOrDefault(key, key);
        plotTitleLabel.setText(label.replaceAll("^[^ ]+ +", "")); // strip emoji
        statusLabel.setText("Loading…");

        // Show spinner, hide old image
        plotImageView.setImage(null);
        plotImageView.setOpacity(0);
        javafx.scene.Node spinner = plotArea.lookup("#spinner");
        if (spinner != null) spinner.setOpacity(1);

        // Fetch from Flask in background
        new Thread(() -> {
            try {
                // Build payload with real AQI data
                com.fasterxml.jackson.databind.node.ObjectNode payload = mapper.createObjectNode();
                payload.put("model", modelName);
                payload.put("plot",  key);

                if (lastAqiData != null) {
                    payload.put("pm25",             lastAqiData.path("pm25").asDouble(30));
                    payload.put("pm10",             lastAqiData.path("pm10").asDouble(50));
                    payload.put("no2",              lastAqiData.path("no2").asDouble(10));
                    payload.put("o3",               lastAqiData.path("o3").asDouble(50));
                    payload.put("co",               lastAqiData.path("co").asDouble(0.5));
                    payload.put("so2",              lastAqiData.path("so2").asDouble(5));
                    payload.put("temperature",      lastAqiData.path("temperature").asDouble(28));
                    payload.put("relativehumidity", lastAqiData.path("humidity").asDouble(65));
                    payload.put("wind_speed",       lastAqiData.path("windSpeed").asDouble(5));
                    payload.put("wind_direction",   lastAqiData.path("windDirection").asDouble(180));
                    payload.put("lat",              lastAqiData.path("lat").asDouble(10));
                    payload.put("lon",              lastAqiData.path("lon").asDouble(76));
                    int aqi = lastAqiData.path("aqi").asInt(100);
                    payload.put("current_aqi", aqi);
                    payload.put("aqi_lag_1",   aqi);
                    payload.put("aqi_lag_2",   aqi);
                    payload.put("hour",        java.time.LocalDateTime.now().getHour());
                    payload.put("day_of_week", java.time.LocalDateTime.now().getDayOfWeek().getValue() % 7);
                    payload.put("month",       java.time.LocalDateTime.now().getMonthValue());
                }

                HttpResponse<String> resp = http.send(
                    HttpRequest.newBuilder()
                        .uri(URI.create(ML_SERVER + "/plot"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                        .timeout(java.time.Duration.ofSeconds(60)).build(),
                    HttpResponse.BodyHandlers.ofString());

                if (resp.statusCode() == 200) {
                    JsonNode result = mapper.readTree(resp.body());
                    String imgB64   = result.path("image").asText();
                    byte[] imgBytes = Base64.getDecoder().decode(imgB64);
                    Image  image    = new Image(new ByteArrayInputStream(imgBytes));

                    Platform.runLater(() -> {
                        if (spinner != null) spinner.setOpacity(0);
                        plotImageView.setImage(image);
                        // Fade in
                        FadeTransition ft = new FadeTransition(Duration.millis(300), plotImageView);
                        ft.setFromValue(0); ft.setToValue(1); ft.play();
                        statusLabel.setText("✓  " + modelLabel + " · " + label.replaceAll("^[^ ]+ +", ""));
                        statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: " + modelColor + ";");
                    });
                } else {
                    JsonNode err = mapper.readTree(resp.body());
                    String msg = err.path("error").asText("Server error " + resp.statusCode());
                    Platform.runLater(() -> {
                        if (spinner != null) spinner.setOpacity(0);
                        statusLabel.setText("Error: " + msg);
                        statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #ef4444;");
                    });
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                Platform.runLater(() -> {
                    if (spinner != null) spinner.setOpacity(0);
                    statusLabel.setText("Error: " + ex.getMessage());
                    statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #ef4444;");
                });
            }
        }).start();
    }

    // ── Save current plot as PNG ──────────────────────────────────
    private void savePlot() {
        if (plotImageView.getImage() == null) return;
        try {
            javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
            fc.setTitle("Save Plot");
            fc.setInitialFileName(modelName + "_" + activePlot + ".png");
            fc.getExtensionFilters().add(
                new javafx.stage.FileChooser.ExtensionFilter("PNG Image", "*.png"));
            java.io.File file = fc.showSaveDialog(null);
            if (file != null) {
                javafx.embed.swing.SwingFXUtils.fromFXImage(plotImageView.getImage(), null);
                javax.imageio.ImageIO.write(
                    javafx.embed.swing.SwingFXUtils.fromFXImage(plotImageView.getImage(), null),
                    "png", file);
                statusLabel.setText("Saved → " + file.getName());
            }
        } catch (Exception e) {
            statusLabel.setText("Save failed: " + e.getMessage());
        }
    }

    // ── Navigate back ─────────────────────────────────────────────
    private void goBack() {
        SceneManager.switchScene("/com/example/test2/main_view.fxml", "Predict AQI");
    }
}
