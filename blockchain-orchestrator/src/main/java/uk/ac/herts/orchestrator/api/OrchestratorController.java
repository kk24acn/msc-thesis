package uk.ac.herts.orchestrator.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.ac.herts.orchestrator.api.dto.SubmitTransactionRequest;
import uk.ac.herts.orchestrator.api.dto.SubmitTransactionResponse;
import uk.ac.herts.orchestrator.service.OrchestratorService;

@RestController
@RequestMapping("/api/v1/transactions")
public class OrchestratorController {
    private final OrchestratorService orchestratorService;

    public OrchestratorController(OrchestratorService orchestratorService) {
        this.orchestratorService = orchestratorService;
    }

    @PostMapping
    public ResponseEntity<SubmitTransactionResponse> submit(@Valid @RequestBody SubmitTransactionRequest request) {
        return new ResponseEntity<>(
                orchestratorService.startTransaction(request),
                HttpStatus.ACCEPTED);
    }
}
