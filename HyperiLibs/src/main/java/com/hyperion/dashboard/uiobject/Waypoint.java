package com.hyperion.dashboard.uiobject;

import com.hyperion.common.Constants;
import com.hyperion.dashboard.Dashboard;
import com.hyperion.dashboard.net.FieldEdit;
import com.hyperion.motion.math.Pose;
import com.sun.javafx.tk.FontMetrics;
import com.sun.javafx.tk.Toolkit;

import org.json.JSONArray;

import javafx.application.Platform;
import javafx.scene.Group;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;

public class Waypoint extends FieldObject {

    public Pose pose;
    public boolean renderID;
    public DisplaySpline parentSpline;

    public ImageView imgView;
    public TextField idField;
    public Text info;

    private long startDragTime;
    private double dragDx, dragDy;

    public Waypoint(String id, double x, double y, double theta, DisplaySpline parentSpline, boolean renderID) {
        this.id = id;
        this.pose = new Pose(x, y, theta);
        this.renderID = renderID;
        this.parentSpline = parentSpline;
        createDisplayGroup();
    }

    public Waypoint(String id, Pose pose, DisplaySpline parentSpline, boolean renderID) {
        this(id, pose.x, pose.y, pose.theta, parentSpline, renderID);
    }

    public Waypoint(String key, JSONArray wpArr) {
        this(key, wpArr.getDouble(0), wpArr.getDouble(1), wpArr.getDouble(2), null, true);
    }

    public void createDisplayGroup() {
        try {
            displayGroup = new Group();
            selection = new Group();

            imgView = new ImageView(Constants.getFile("img", "waypoint.png").toURI().toURL().toString());
            imgView.setFitWidth(Constants.getDouble("dashboard.gui.sizes.waypoint"));
            imgView.setFitHeight(Constants.getDouble("dashboard.gui.sizes.waypoint"));
            displayGroup.getChildren().add(imgView);

            if (renderID) {
                idField = new TextField(id.replace(Dashboard.opModeID + ".waypoint.", ""));
                idField.setStyle("-fx-background-radius: 0; -fx-text-inner-color: rgba(255, 255, 255, 1.0); -fx-background-color: rgba(0, 0, 0, 0.6);");
                idField.setMinWidth(17);
                idField.setMaxWidth(150);

                FontMetrics metrics = Toolkit.getToolkit().getFontLoader().getFontMetrics(idField.getFont());
                idField.setPrefWidth(metrics.computeStringWidth(idField.getText()) + 17);
                idField.textProperty().addListener((observable, oldTextString, newTextString) ->
                    idField.setPrefWidth(metrics.computeStringWidth(newTextString) + 17)
                );
                idField.setTextFormatter(new TextFormatter<>(change -> {
                    if (!change.isContentChange()) {
                        return change;
                    }
                    String txt = change.getControlNewText();
                    if (txt.contains(".")) return null;
                    return change;
                }));

                if (parentSpline == null) {
                    idField.setOnKeyPressed(event -> {
                        if (event.getCode() == KeyCode.ENTER) {
                            String oldID = id;
                            id = Dashboard.opModeID + ".waypoint." + idField.getText();
                            Dashboard.editField(new FieldEdit(oldID, FieldEdit.Type.EDIT_ID, id));
                            Dashboard.fieldPane.requestFocus();
                        }
                    });
                    idField.setOnMousePressed(event -> Dashboard.fieldPane.select(this));
                    displayGroup.getChildren().add(idField);
                }
            }

            info = new Text(pose.toString());
            info.setFill(Color.WHITE);
            info.setVisible(false);
            displayGroup.getChildren().add(info);

            Color selectColor = Color.hsb(360.0 * Math.random(), 1.0, 1.0);
            Rectangle selectRect = new Rectangle(Dashboard.fieldPane.robotSize, Dashboard.fieldPane.robotSize);
            selectRect.setStroke(selectColor);
            selectRect.setStrokeWidth(2);
            selectRect.setFill(new Color(selectColor.getRed(), selectColor.getGreen(), selectColor.getBlue(), 0.3));
            selection.getChildren().add(selectRect);

            setListeners();
            refreshDisplayGroup();
            info.toFront();
            if (renderID) {
                idField.toFront();
            }
            imgView.toFront();

            Dashboard.fieldPane.select(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setListeners() {
        displayGroup.setOnMouseClicked((event -> {
            try {
                if (event.getButton() == MouseButton.SECONDARY) {
                    if (parentSpline != null) {
                        parentSpline.spline.waypoints.remove(parentSpline.waypoints.indexOf(this));
                        parentSpline.waypoints.remove(this);
                        parentSpline.spline.endPath();
                        Dashboard.editField(new FieldEdit(parentSpline.id, FieldEdit.Type.EDIT_BODY, parentSpline.spline.writeJSON().toString()));
                        if (parentSpline.waypoints.size() == 0) {
                            Dashboard.editField(new FieldEdit(parentSpline.id, FieldEdit.Type.DELETE, "{}"));
                        }
                    } else {
                        Dashboard.editField(new FieldEdit(id, FieldEdit.Type.DELETE, "{}"));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }));

        displayGroup.setOnMousePressed((event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                startDragTime = System.currentTimeMillis();
                dragDx = imgView.getLayoutX() - event.getSceneX();
                dragDy = imgView.getLayoutY() - event.getSceneY();
                Dashboard.fieldPane.select(this);
            }
        }));

        displayGroup.setOnMouseDragged((event -> {
            double newX = event.getSceneX() + dragDx;
            double newY = event.getSceneY() + dragDy;

            Rectangle rect = new Rectangle(newX, newY, Constants.getDouble("dashboard.gui.sizes.waypoint"), Constants.getDouble("dashboard.gui.sizes.waypoint"));
            Rectangle rectX = new Rectangle(newX, imgView.getLayoutY(), Constants.getDouble("dashboard.gui.sizes.waypoint"), Constants.getDouble("dashboard.gui.sizes.waypoint"));
            Rectangle rectY = new Rectangle(imgView.getLayoutX(), newY, Constants.getDouble("dashboard.gui.sizes.waypoint"), Constants.getDouble("dashboard.gui.sizes.waypoint"));
            boolean[] intersects = Dashboard.fieldPane.getWBBIntersects(rect, rectX, rectY);
            if (intersects[0]) imgView.setLayoutX(newX);
            if (intersects[1]) imgView.setLayoutY(newY);
            pose = Dashboard.fieldPane.viewToPose(imgView, Constants.getDouble("dashboard.gui.sizes.waypoint"));
            refreshDisplayGroup();
        }));

        displayGroup.setOnMouseReleased((event -> {
            try {
                if (System.currentTimeMillis() - startDragTime > 200 && event.getButton() == MouseButton.PRIMARY) {
                    if (parentSpline != null) {
                        parentSpline.spline.waypoints.get(parentSpline.waypoints.indexOf(this)).setPose(pose);
                        Dashboard.editField(new FieldEdit(parentSpline.id, FieldEdit.Type.EDIT_BODY, parentSpline.spline.writeJSON().toString()));
                    } else {
                        Dashboard.editField(new FieldEdit(id, FieldEdit.Type.EDIT_BODY, new JSONArray(pose.toArray()).toString()));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }));
    }

    public void addDisplayGroup() {
        Platform.runLater(() -> {
            if (renderID && parentSpline == null) {
                idField.setText(id.replace(Dashboard.opModeID + ".waypoint.", ""));
            } else if (renderID) {
                idField.setText(parentSpline.id.replace(Dashboard.opModeID + ".spline.", ""));
            }
            if (parentSpline != null && parentSpline.id.startsWith(Dashboard.opModeID)) {
                parentSpline.displayGroup.getChildren().add(displayGroup);
            } else if (id.startsWith(Dashboard.opModeID)) {
                Dashboard.fieldPane.getChildren().add(displayGroup);
            }
        });
    }

    public void refreshDisplayGroup() {
        double[] poseToDisplay = Dashboard.fieldPane.poseToDisplay(pose, Constants.getDouble("dashboard.gui.sizes.waypoint"));
        imgView.relocate(poseToDisplay[0], poseToDisplay[1]);
        imgView.setRotate(poseToDisplay[2]);
        if (renderID) {
            if (parentSpline != null) {
                idField.setText(parentSpline.id.replace(Dashboard.opModeID + ".spline.", ""));
            } else {
                idField.setText(id.replace(Dashboard.opModeID + ".waypoint.", ""));
            }
            idField.relocate(poseToDisplay[0] + Constants.getDouble("dashboard.gui.sizes.waypoint") + 3, poseToDisplay[1] - 24);
        }
        info.setText(pose.toString().replace(" | ", "\n"));
        info.relocate(poseToDisplay[0] + Constants.getDouble("dashboard.gui.sizes.waypoint") + 3, poseToDisplay[1] + Constants.getDouble("dashboard.gui.sizes.waypoint") - 21);
        selection.relocate(poseToDisplay[0] + Constants.getDouble("dashboard.gui.sizes.waypoint") / 2.0 - Dashboard.fieldPane.robotSize / 2.0,
                            poseToDisplay[1] + Constants.getDouble("dashboard.gui.sizes.waypoint") / 2.0 - Dashboard.fieldPane.robotSize / 2.0);
        selection.setRotate(poseToDisplay[2]);
    }

    public void removeDisplayGroup() {
        Platform.runLater(() -> {
            if (parentSpline != null) {
                parentSpline.displayGroup.getChildren().remove(displayGroup);
            } else {
                Dashboard.fieldPane.getChildren().remove(displayGroup);
            }
        });
    }

    public String toString() {
        return id + " " + pose;
    }

}
