package uk.ac.herts.orchestrator.api;

import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.ac.herts.orchestrator.api.dto.SubmitTransactionRequest;
import uk.ac.herts.orchestrator.api.dto.SubmitTransactionResponse;
import uk.ac.herts.orchestrator.service.ServletOrchestratorService;


@RestController
@Profile("servlet")
@RequestMapping("/api/servlet/transactions")
public class ServletOrchestratorController {
    private final ServletOrchestratorService servletOrchestratorService;

    public ServletOrchestratorController(ServletOrchestratorService servletOrchestratorService) {
        this.servletOrchestratorService = servletOrchestratorService;
    }

    @PostMapping
    public ResponseEntity<SubmitTransactionResponse> submit(@Valid @RequestBody SubmitTransactionRequest request) {
        return new ResponseEntity<SubmitTransactionResponse>(
            servletOrchestratorService.startTransaction(request),
            HttpStatus.ACCEPTED);
    }
}
