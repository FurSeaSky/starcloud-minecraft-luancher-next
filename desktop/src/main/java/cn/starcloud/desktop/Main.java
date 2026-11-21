package cn.starcloud.desktop;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * Desktop entry point.
 *
 * <p>Placeholder shell: it only opens a window so the JavaFX toolchain can be verified end to end.
 * All launcher behaviour lives in {@code core}; nothing here may be depended on by other modules.
 */
public class Main extends Application {

  /**
   * Builds and shows the main window.
   *
   * @param stage the window supplied by the JavaFX runtime
   */
  @Override
  public void start(Stage stage) {
    Label label = new Label("Starcloud Launcher (JavaFX skeleton)");
    StackPane root = new StackPane(label);

    Scene scene = new Scene(root, 800, 600);

    stage.setTitle("Starcloud");
    stage.setScene(scene);
    stage.show();
  }

  /**
   * Starts the JavaFX application.
   *
   * @param args command line arguments, forwarded to JavaFX untouched
   */
  public static void main(String[] args) {
    launch(args);
  }
}
