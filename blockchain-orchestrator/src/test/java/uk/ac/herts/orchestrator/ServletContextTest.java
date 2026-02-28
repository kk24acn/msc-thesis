package uk.ac.herts.orchestrator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.ac.herts.orchestrator.api.ReactiveOrchestratorController;
import uk.ac.herts.orchestrator.api.ServletOrchestratorController;

import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("servlet")
public class ServletContextTest {

    @Autowired
    private ServletOrchestratorController servletOrchestratorController;

    @Autowired(required = false)
    private ReactiveOrchestratorController reactiveOrchestratorController;

    @Test
    public void testWebMvcContextLoads() {
        assertNotNull(servletOrchestratorController);
        assertNull(reactiveOrchestratorController);
    }
}