module dev.atikdd.marksman {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires javafx.graphics;

    opens dev.atikdd.marksman to javafx.fxml;
    exports dev.atikdd.marksman;
}