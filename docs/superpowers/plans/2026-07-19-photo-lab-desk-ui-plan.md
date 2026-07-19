# Photo Lab Desk UI Redesign Implementation Plan

> For agentic workers: use the executing-plans workflow to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Replace the dark, purple AI-dashboard presentation with a warm, practical Photo Lab Desk interface while preserving camera, image-processing, controller IDs, and update behavior.

**Architecture:** Keep the existing JavaFX BorderPane and UIManager contract. Make the redesign through FXML/CSS changes, with a small UIManager copy/style cleanup for dynamic camera state and update notifications. No detection or service code changes are required.

**Tech Stack:** Java 25, JavaFX FXML, JavaFX CSS, Maven/Surefire, existing OpenCV integration tests.
**Status:** Implemented and verified.

---

## File map

- Modify src/main/resources/com/example/facedetection/scene.fxml for visible English copy, header/footer composition, and layout spacing. Preserve every controller-facing fx:id and event handler.
- Modify src/main/resources/com/example/facedetection/styles.css for the Photo Lab Desk palette, flat panels, monitor viewports, native-looking controls, and visible focus states.
- Modify src/main/java/com/example/facedetection/ui/UIManager.java to remove decorative start/stop glyphs and centralize dynamic update/status styling in CSS.
- Modify docs/PROJECT_SUMMARY.md to record the UI redesign and keep the verification snapshot current.
- Test through Maven resource processing and the existing full test suite. Do not add GUI launch or camera automation.

## Task 1: Capture the UI baseline and identify wiring constraints

**Files:**
- Read src/main/resources/com/example/facedetection/scene.fxml
- Read src/main/resources/com/example/facedetection/styles.css
- Read src/main/java/com/example/facedetection/ui/UIManager.java
- Read src/main/java/com/example/facedetection/controller/ViewController.java

- [x] Step 1: Preserve the controller contract before editing

Keep these FXML IDs exactly: cameraSelector, cameraButton, insertImageButton, adaptiveExposureCheckBox, brightLightModeCheckBox, genderRecognitionCheckBox, engineLabel, fpsLabel, exposureStatusLabel, originalImageView, processedImageView, sourceViewport, resultViewport, sourceCard, and statusLabel.

Keep these handlers exactly: #toggleCamera, #selectImage, #handleAdaptiveExposureToggle, #handleBrightLightModeToggle, and #handleGenderRecognitionToggle.

- [x] Step 2: Run the current resource baseline

Run:

~~~powershell
mvn -q -DskipTests process-resources
Select-String -Path target\classes\com\example\facedetection\scene.fxml -Pattern 'v1\.2\.2'
~~~

Expected: Maven succeeds and the filtered FXML contains the current release version.

## Task 2: Recompose the FXML for a calm photo workstation

**Files:**
- Modify src/main/resources/com/example/facedetection/scene.fxml

- [x] Step 1: Update the top header copy

Keep headerBar, appTitle, appSubtitle, and fpsLabel. Change the visible copy to:

~~~xml
<Label id="appTitle" text="Face Recognition" />
<Label id="appSubtitle" text="Live camera and image review" />
~~~

Keep the FPS value dynamic and label its small caption Performance in sentence case.

- [x] Step 2: Simplify the control rail copy and grouping

Keep the existing sidebar and all control IDs. Change labels to:

~~~xml
<Label text="Camera" styleClass="sidebar-section-title" />
<Label text="Camera source" styleClass="stat-label" />
<ComboBox ... promptText="Select device" ... />
<Button ... text="Start Camera" ... />
<Button ... text="Open Image" ... />
<Label text="Processing options" styleClass="sidebar-section-title" />
<CheckBox ... text="Adaptive light" />
<CheckBox ... text="Bright-light correction" />
<CheckBox ... text="Show gender labels" />
~~~

Keep checkbox defaults and handlers unchanged. Change SYSTEM STATUS to Status and keep the dynamic engineLabel text and status dot.

- [x] Step 3: Make image panel headings human-facing

Keep both image cards and their existing IDs. Change headings and static status labels to:

~~~xml
<Label text="Source image" styleClass="card-header" />
<Label text="Live" styleClass="status-caption" />
<Label text="Reviewed image" styleClass="card-header" />
~~~

Keep exposureStatusLabel in the result header unless moving it to the footer is necessary; style it as quiet metadata rather than a badge.

- [x] Step 4: Simplify the footer

Keep footerBar, statusLabel, and the filtered version label. Do not create a second label with the same fx:id. The footer should be a compact status strip, not a dashboard metric row.

- [x] Step 5: Validate FXML wiring statically

Run:

~~~powershell
rg -n "fx:id=|onAction=|text=" src\main\resources\com\example\facedetection\scene.fxml
~~~

Expected: every ID and handler from Task 1 remains present and no old all-caps marketing labels remain.

## Task 3: Replace the CSS token system and component treatment

**Files:**
- Modify src/main/resources/com/example/facedetection/styles.css

- [x] Step 1: Define the Photo Lab Desk palette

Replace the current dark-purple variables with these exact tokens:

~~~css
-color-desk: #F1EFE8;
-color-paper: #FBFAF7;
-color-ink: #26302C;
-color-muted-ink: #6D746E;
-color-sage: #5E7565;
-color-sage-hover: #4E6656;
-color-clay: #B86E52;
-color-monitor: #202624;
-color-line: #D8D9D2;
~~~

Set root typography to Segoe UI with background color-desk. Use Georgia only for appTitle.

- [x] Step 2: Flatten the shell and panels

Set headerBar, sidebar, and footerBar to color-paper with color-line borders. Use 1 px borders, no drop shadows, and 12-16 px spacing. Keep the sidebar narrower and visually quiet instead of making it a dark block.

- [x] Step 3: Style action controls without AI-dashboard effects

Use flat sage fill for btn-primary, flat clay fill for btn-danger, and paper fill with a line border for btn-secondary. Remove all gradients and dropshadow declarations. Keep hover and keyboard focus visible through fill and border changes:

~~~css
.btn-primary:focused,
.btn-secondary:focused,
.btn-danger:focused,
.combo-box:focused {
    -fx-border-color: -color-sage;
    -fx-border-width: 2;
}

.toggle-chip:focused {
    -fx-border-color: transparent;
    -fx-border-width: 0;
}

.toggle-chip:focused > .box {
    -fx-border-color: -color-sage;
    -fx-border-width: 2;
}
~~~

- [x] Step 4: Style photo viewports as dark monitors

Use color-monitor for image-viewport, a modest 8-10 px radius, and a thin color-line frame. Keep view-card paper-colored with a 1 px border and no shadow. This contrast is the signature visual detail.

- [x] Step 5: Style labels, toggles, status, and combo box

Use sentence-case typography, color-ink for primary text, color-muted-ink for metadata, sage for selected toggles/status, and clay for danger/update attention. Render selected toggle boxes as solid sage squares with light check glyphs, keep unselected boxes empty, and keep the keyboard focus ring on the square rather than the label. Remove all-caps styling from section headings. Add status-caption and status-update classes if referenced by FXML or UIManager.

- [x] Step 6: Check CSS for removed visual patterns

Run:

~~~powershell
rg -n "linear-gradient|dropshadow|#7C3AED|#0B0B14|#12121F|#18182B|text-transform" src\main\resources\com\example\facedetection\styles.css
~~~

Expected: no gradient, glow shadow, old purple token, or text-transform rule remains.

## Task 4: Align dynamic UI state with the new visual language

**Files:**
- Modify src/main/java/com/example/facedetection/ui/UIManager.java
- Modify src/main/resources/com/example/facedetection/styles.css if a new state class is referenced.

- [x] Step 1: Remove decorative start/stop glyphs

Change the two dynamic labels to plain action text:

~~~java
cameraButton.setText("Stop Camera");
cameraButton.setText("Start Camera");
~~~

Keep primary/danger class swapping exactly as-is.

- [x] Step 2: Use a CSS class for update attention

In showUpdateNotification, add status-update to statusLabel.getStyleClass() and keep the click handler. In resetStatusStyle, remove status-update before clearing the click handler. Do not use an inline color style.

- [x] Step 3: Verify dynamic IDs and state methods compile

Run:

~~~powershell
mvn -q -DskipTests compile
~~~

Expected: compile succeeds with no controller method or field wiring errors.

## Task 5: Refresh documents and run the UI verification suite

**Files:**
- Modify docs/PROJECT_SUMMARY.md

- [x] Step 1: Update the project summary

Add a bullet stating that the JavaFX presentation was redesigned as the Photo Lab Desk UI: warm light workspace, dark monitor viewports, flat controls, and sentence-case English copy. Keep the verification test count current when later code changes add coverage.

- [x] Step 2: Run resource and test verification

Run:

~~~powershell
mvn clean test
mvn clean verify
Select-String -Path target\classes\com\example\facedetection\scene.fxml -Pattern 'v1\.2\.2'
git diff --check
~~~

Expected:
- 79 tests pass with 0 failures/errors; camera skips remain hardware-aware.
- mvn clean verify succeeds and packages the current versioned JAR.
- Filtered FXML contains the current release version.
- git diff --check produces no whitespace errors.

- [x] Step 3: Review project documents for stale UI claims

Run:

~~~powershell
rg -n "AI dashboard|purple|Advanced AI Vision|AI ANALYSIS RESULT|CONTROL PANEL|SOURCE INPUT|SYSTEM STATUS|72 tests|74 tests" README.md docs src
~~~

Expected: only historical design/spec references may mention old labels. Active README and project summary must describe the Photo Lab Desk direction and current test count.

- [x] Step 4: Commit the implementation

~~~powershell
git add src/main/resources/com/example/facedetection/scene.fxml src/main/resources/com/example/facedetection/styles.css src/main/java/com/example/facedetection/ui/UIManager.java docs/PROJECT_SUMMARY.md
git commit -m "feat: refresh face recognition desktop UI"
~~~

## Final handoff

Report the exact commit, test results, and the fact that GUI/camera/installer were not launched automatically. Include updated README/project summary links and note that visual confirmation on the user's screen is still useful because JavaFX font rendering and window sizing are machine-dependent.
