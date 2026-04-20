package uk.ac.herts.orchestrator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.ac.herts.orchestrator.api.OrchestratorController;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(SpringExtension.class)
@SpringBootTest
public class ApplicationContextTest {

    @Autowired
    private OrchestratorController orchestratorController;

    @Test
    public void testWebMvcContextLoads() {
        assertNotNull(orchestratorController);
    }
}