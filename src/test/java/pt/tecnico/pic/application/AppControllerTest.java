package pt.tecnico.pic.application;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class AppControllerTest {
    @Test
    void canInstantiateAppController() {
        AppController appController = new AppController();

        assertNotNull(appController);
    }
}
