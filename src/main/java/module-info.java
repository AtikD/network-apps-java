open module dev.atikdd.marksman {
    requires javafx.controls;
    requires javafx.fxml;
    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires javafx.graphics;
    requires com.google.gson;
    requires org.hibernate.orm.core;
    requires jakarta.persistence;
    requires org.xerial.sqlitejdbc;
    requires java.naming;
    requires org.slf4j;

    exports dev.atikdd.marksman;
    exports dev.atikdd.marksman.net;
    exports dev.atikdd.marksman.server;
}
