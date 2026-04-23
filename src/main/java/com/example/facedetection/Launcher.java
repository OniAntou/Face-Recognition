package com.example.facedetection;

import javafx.application.Application;

/**
 * Launcher class to bypass JavaFX module system check when running from a fat JAR.
 */
public class Launcher {
    public static void main(String[] args) {
        Application.launch(MainApp.class, args);
    }
}
