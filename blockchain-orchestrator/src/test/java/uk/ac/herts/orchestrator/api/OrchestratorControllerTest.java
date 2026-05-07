package uk.ac.herts.orchestrator.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import uk.ac.herts.orchestrator.api.dto.SubmitTransactionRequest;
import uk.ac.herts.orchestrator.api.dto.SubmitTransactionResponse;
import uk.ac.herts.orchestrator.exception.GlobalExceptionHandler;
import uk.ac.herts.orchestrator.exception.TransactionConfirmationException;
import uk.ac.herts.orchestrator.exception.TransactionSigningException;
import uk.ac.herts.orchestrator.exception.TransactionSubmissionException;
import uk.ac.herts.orchestrator.service.OrchestratorService;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class OrchestratorControllerTest {

    @Mock
    private OrchestratorService orchestratorService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new OrchestratorController(orchestratorService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    private static final String URL = "/api/v1/transactions";

    @Test
    void submit_validRequest_returns202WithResponseBody() throws Exception {
        UUID txId = UUID.randomUUID();
        SubmitTransactionRequest req = new SubmitTransactionRequest("key-1", "0xabc", BigDecimal.valueOf(0.5));
        SubmitTransactionResponse resp = SubmitTransactionResponse.builder()
                .transactionId(txId)
                .transactionHash("0xhash")
                .toAddress("0xabc")
                .amountEther(BigDecimal.valueOf(0.5))
                .status("CONFIRMED")
                .build();
        when(orchestratorService.startTransaction(any())).thenReturn(resp);

        mockMvc.perform(post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.transactionId").value(txId.toString()))
                .andExpect(jsonPath("$.transactionHash").value("0xhash"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void submit_blankKeyId_returns400() throws Exception {
        mockMvc.perform(post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"keyId": "", "toAddress": "0xabc", "amountEther": 0.5}
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submit_blankToAddress_returns400() throws Exception {
        mockMvc.perform(post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"keyId": "key-1", "toAddress": "", "amountEther": 0.5}
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submit_zeroAmountEther_returns400() throws Exception {
        mockMvc.perform(post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"keyId": "key-1", "toAddress": "0xabc", "amountEther": 0}
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submit_nullAmountEther_returns400() throws Exception {
        mockMvc.perform(post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"keyId": "key-1", "toAddress": "0xabc"}
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submit_invalidKeyId_returns400WithInvalidRequestCode() throws Exception {
        when(orchestratorService.startTransaction(any()))
                .thenThrow(new IllegalArgumentException("Invalid keyId"));

        mockMvc.perform(post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"keyId": "bad-key", "toAddress": "0xabc", "amountEther": 0.5}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Invalid keyId"));
    }

    @Test
    void submit_signingException_returns502WithSigningFailedCode() throws Exception {
        when(orchestratorService.startTransaction(any()))
                .thenThrow(new TransactionSigningException("DSG quorum unavailable",
                        new RuntimeException("3 signers down")));

        mockMvc.perform(post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"keyId": "key-1", "toAddress": "0xabc", "amountEther": 0.5}
                        """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("TRANSACTION_SIGNING_FAILED"))
                .andExpect(jsonPath("$.message").value("DSG quorum unavailable"));
    }

    @Test
    void submit_submissionException_returns502WithSubmissionFailedCode() throws Exception {
        when(orchestratorService.startTransaction(any()))
                .thenThrow(new TransactionSubmissionException("Ethereum node down", null));

        mockMvc.perform(post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"keyId": "key-1", "toAddress": "0xabc", "amountEther": 0.5}
                        """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("TRANSACTION_SUBMISSION_FAILED"));
    }

    @Test
    void submit_confirmationException_returns502WithConfirmationFailedCode() throws Exception {
        when(orchestratorService.startTransaction(any()))
                .thenThrow(new TransactionConfirmationException("Receipt timeout after 20s", null));

        mockMvc.perform(post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"keyId": "key-1", "toAddress": "0xabc", "amountEther": 0.5}
                        """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("TRANSACTION_CONFIRMATION_FAILED"));
    }
}
