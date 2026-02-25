module com.benktesh.fooddonation {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires java.desktop;
    requires org.mongodb.driver.sync.client;
    requires org.mongodb.driver.core;
    requires org.mongodb.bson;


    opens com.benktesh.fooddonation to javafx.fxml;
    exports com.benktesh.fooddonation;
}