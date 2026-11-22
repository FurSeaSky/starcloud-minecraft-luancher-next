package cn.starcloud.desktop.views;

import javafx.scene.layout.BorderPane;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

public class MainView extends BorderPane {
  public MainView() {
    Label topBar = new Label("Starcloud Launcher 顶部栏");
    topBar.setStyle("-fx-padding: 10; -fx-background-color: #222; -fx-text-fill: white;");
    setTop(topBar);

    Label mainContent = new Label("【主体 Center 区域】");
    Label rightPanel = new Label("【右侧 Right 区域】");


    HBox.setHgrow(mainContent, Priority.ALWAYS);

    HBox centerBox = new HBox(mainContent, rightPanel);
    centerBox.setSpacing(0);


    setCenter(centerBox);


    Label footer = new Label("Footer | Starcloud Launcher v1.0");
    footer.setStyle("-fx-padding: 8; -fx-background-color: #333; -fx-text-fill: white;");
    setBottom(footer);
  }
}
