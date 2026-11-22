package cn.starcloud.desktop;

import cn.starcloud.core.launcherCore.ILauncherCore;
import cn.starcloud.core.launcherCore.InitException;
import cn.starcloud.core.launcherCore.LauncherCore;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;

import cn.starcloud.desktop.views.MainView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Font;
import javafx.stage.Stage;

/**
 * Desktop entry point.
 *
 * <p>Placeholder shell: it only opens a window so the JavaFX toolchain can be verified end to end.
 * All launcher behaviour lives in {@code core}; nothing here may be depended on by other modules.
 */
public class Main extends Application {

  public static Font codeGlobalFont;

  private Throwable initFailure;
  private ILauncherCore core = LauncherCore.getInstance();

  @Override
  public void init() {
    try {
      if (!core.init()) {
        initFailure = new IllegalStateException("LauncherCore.init() returned false");
      }
    } catch (Throwable t) {
      t.printStackTrace(System.err);
      initFailure = t;
    }
  }

  @Override
  public void start(Stage stage) {
    if (initFailure != null) {
      showFatalError(initFailure);
      Platform.exit();
      return;
    }

    codeGlobalFont = Font.loadFont(getClass().getResource("/fonts/NotoSansMonoCJKsc-Regular.otf").toExternalForm(),14);
    if(codeGlobalFont ==null){
      throw new InitException("can't load Font");
    }

    MainView MV =  new MainView();
    Scene scene = new Scene(MV, 800, 600);
    URL resource = getClass().getResource("/styles/base.css");
    if (resource != null) {
      scene.getStylesheets().add(resource.toExternalForm());
    }else{
      throw new InitException("Can't find css");
    }
    stage.setTitle("Starcloud");
    stage.setScene(scene);
    stage.show();
  }

  @Override
  public void stop() {
  }

  private void showFatalError(Throwable t) {
    Alert alert = new Alert(Alert.AlertType.ERROR);
    alert.setTitle("Starcloud");
    alert.setHeaderText("初始化失败，应用无法启动");
    alert.setContentText(t.getMessage() != null ? t.getMessage() : t.getClass().getName());

    StringWriter sw = new StringWriter();
    t.printStackTrace(new PrintWriter(sw));
    TextArea detail = new TextArea(sw.toString());
    detail.setEditable(false);
    detail.setWrapText(false);
    detail.setPrefRowCount(12);

    TitledPane expandable = new TitledPane("详细信息", detail);
    expandable.setExpanded(false);
    alert.getDialogPane().setExpandableContent(expandable);

    alert.showAndWait();
  }

  public static void main(String[] args) {
    launch(args);
  }
}
