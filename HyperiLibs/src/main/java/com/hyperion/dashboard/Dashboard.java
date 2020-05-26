package com.hyperion.dashboard;

import com.hyperion.common.*;
import com.hyperion.dashboard.net.*;
import com.hyperion.dashboard.pane.*;
import com.hyperion.dashboard.uiobject.*;
import com.hyperion.motion.math.*;

import org.json.*;

import java.io.*;
import java.util.*;

import javafx.application.*;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.stage.*;

/**
 * Main UI client class
 */

public class Dashboard extends Application {

    public static MenuPane menuPane;
    public static FieldPane fieldPane;
    public static LeftPane leftPane;
    public static RightPane rightPane;
    public static VisualPane visualPane;
    public static BTServer btServer;

    public static DisplaySpline selectedSpline = null;
    public static Waypoint selectedWaypoint = null;
    public static List<FieldObject> fieldObjects = new ArrayList<>();
    public static Map<String, String> metrics = new HashMap<>();
    public static boolean isRobotOnField;

    public static String opModeID = "auto.blue.full";
    public static boolean isBuildingPaths;
    public static boolean isSimulating;

    public static List<FieldEdit> queuedEdits = new ArrayList<>();
    public static String constantsSave = "";
    public static boolean hasUnsavedChanges = false;

    public static void main(String[] args) {
        Constants.init(new File(System.getProperty("user.dir") + "/HyperiLibs/src/main/res/data/constants.json"));
//        btServer = new BTServer();
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        stage.initStyle(StageStyle.TRANSPARENT);
        Rectangle2D primaryScreenBounds = Screen.getPrimary().getVisualBounds();
        stage.setX(primaryScreenBounds.getMinX());
        stage.setY(primaryScreenBounds.getMinY());
        stage.setWidth(primaryScreenBounds.getWidth());
        stage.setHeight(primaryScreenBounds.getHeight());
        stage.setOnCloseRequest(e -> System.exit(0));

        FlowPane all = new FlowPane();
        all.setStyle("-fx-background-color: rgba(0, 0, 0, 0.9)");
        VBox sideStuff = new VBox();
        sideStuff.setBackground(Background.EMPTY);
        sideStuff.setSpacing(10);
        menuPane = new MenuPane(stage);
        fieldPane = new FieldPane(stage);
        rightPane = new RightPane();
        leftPane = new LeftPane();
        visualPane = new VisualPane();
        all.getChildren().add(menuPane);
        all.getChildren().add(fieldPane);
        FlowPane.setMargin(fieldPane, new Insets(10));
        HBox hbox = new HBox();
        hbox.setBackground(Background.EMPTY);
        hbox.setSpacing(10);
        hbox.getChildren().add(leftPane);
        hbox.getChildren().add(rightPane);
        sideStuff.getChildren().add(hbox);
        sideStuff.getChildren().add(visualPane);

        ScrollPane sp = new ScrollPane();
        sp.setMaxHeight(fieldPane.fieldSize);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        sp.widthProperty().addListener((o) -> {
            Node vp = sp.lookup(".viewport");
            vp.setStyle("-fx-background-color: transparent;");
        });
        sp.setStyle("-fx-background-color: transparent;");
        sp.setContent(sideStuff);
        all.getChildren().add(sp);
        all.getChildren().add(sideStuff);
        Scene scene = new Scene(all, 1280, 720, Color.TRANSPARENT);
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.S && event.isControlDown()) {
                saveDashboard();
            }
        });

        stage.setScene(scene);
        stage.show();
    }

    // Read in unimetry from json
    public static void readUnimetry(String json) {
        try {
            metrics = new LinkedHashMap<>();
            JSONArray dataArr = new JSONArray(json);
            for (int i = 0; i < dataArr.length(); i++) {
                JSONArray miniObj = dataArr.getJSONArray(i);
                metrics.put(miniObj.getString(0), miniObj.getString(1));
            }

            editUI(new FieldEdit("robot", isRobotOnField ? FieldEdit.Type.EDIT_BODY : FieldEdit.Type.CREATE,
                   new JSONArray(new RigidBody(metrics.get("Current")).toArray()).toString()));
            isRobotOnField = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Edit UI based on FieldEdit
    public static void editUI(FieldEdit edit) {
        try {
            FieldObject newObj = null;
            if (edit.type != FieldEdit.Type.DELETE && edit.type != FieldEdit.Type.EDIT_ID) {
                if (edit.id.contains("waypoint")) {
                    newObj = new Waypoint(edit.id, new JSONArray(edit.body));
                } else if (edit.id.contains("spline")) {
                    newObj = new DisplaySpline(edit.id, new JSONObject(edit.body));
                } else {
                    newObj = new Robot(new JSONArray(edit.body));
                    isRobotOnField = true;
                }
            }
            switch (edit.type) {
                case CREATE:
                    clearAllFieldObjectsWithID(edit.id);
                    fieldObjects.add(newObj);
                    newObj.addDisplayGroup();
                    break;
                case EDIT_BODY:
                    for (int i = 0; i < fieldObjects.size(); i++) {
                        if (fieldObjects.get(i).id.equals(edit.id)) {
                            if (edit.id.equals("robot")) {
                                ((Robot) fieldObjects.get(i)).rigidBody = new RigidBody(new JSONArray(edit.body));
                                fieldObjects.get(i).refreshDisplayGroup();
                            } else {
                                fieldObjects.get(i).removeDisplayGroup();
                                fieldObjects.set(i, newObj);
                                newObj.addDisplayGroup();
                            }
                            break;
                        }
                    }
                    break;
                case EDIT_ID:
                    for (int i = 0; i < fieldObjects.size(); i++) {
                        if (fieldObjects.get(i).id.equals(edit.id)) {
                            fieldObjects.get(i).id = edit.body;
                            fieldObjects.get(i).refreshDisplayGroup();
                            break;
                        }
                    }
                    break;
                case DELETE:
                    Iterator<FieldObject> iter2 = fieldObjects.iterator();
                    while (iter2.hasNext()) {
                        FieldObject next = iter2.next();
                        if (next.id.equals(edit.id)) {
                            next.removeDisplayGroup();
                            iter2.remove();
                            break;
                        }
                    }
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Queue field edits
    public static void queueFieldEdits(FieldEdit... fieldEdits) {
        queuedEdits.addAll(Arrays.asList(fieldEdits));
        changeSaveStatus(true);
    }

    // Send queued field edits
    public static void sendQueuedFieldEdits() {
        try {
            JSONArray arr = new JSONArray();
            for (FieldEdit edit : queuedEdits) {
                arr.put(edit.toJSONObject());
            }
            queuedEdits.clear();
            btServer.sendMessage(Message.Event.FIELD_EDITED, arr);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Set save indicator
    public static void changeSaveStatus(boolean hazUnsavedChanges) {
        hasUnsavedChanges = hazUnsavedChanges;
        Platform.runLater(() -> menuPane.title.setText("Hyperion Dashboard v" + Constants.getString("dashboard.version") + (hasUnsavedChanges ? " (*)" : "")));
    }

    // Save dashboard upon ctrl + s
    public void saveDashboard() {
        try {
            if (hasUnsavedChanges) {
                String newConstants = rightPane.constantsDisplay.getText();
                if (!TextUtils.condensedEquals(newConstants, constantsSave)) {
                    btServer.sendMessage(Message.Event.CONSTANTS_UPDATED, newConstants);
                }

                sendQueuedFieldEdits();
                changeSaveStatus(false);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Remove all objects in FieldEdits with the give id
    public static void clearAllFieldObjectsWithID(String id) {
        Iterator<FieldObject> iter = fieldObjects.iterator();
        while (iter.hasNext()) {
            FieldObject next = iter.next();
            if (next.id.equals(id)) {
                next.removeDisplayGroup();
                iter.remove();
            }
        }
    }

}