package uk.ac.herts.orchestrator.client.mpc;

import lombok.RequiredArgsConstructor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.stereotype.Component;
import org.web3j.crypto.Hash;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.utils.Numeric;

import uk.ac.herts.orchestrator.repository.entity.MpcKey;
import uk.ac.herts.orchestrator.repository.entity.Transaction;

@Component
@RequiredArgsConstructor
public class TransactionSigner {

    private final DsgCoordinator dsgCoordinator;

    public SignResult sign(RawTransaction rawTx, Transaction tx, MpcKey mpcKey) throws Exception {
        byte[] encoded = TransactionEncoder.encode(rawTx);
        byte[] msgHash = Hash.sha3(encoded);

        DsgCoordinator.DsgResult dsgResult = dsgCoordinator
                .executeUnderConcurrencyLimit(() -> {
                    tx.setSigningStartedAt(OffsetDateTime.now(ZoneOffset.UTC));
                    DsgCoordinator.DsgResult result = dsgCoordinator.executeDsg(mpcKey, msgHash);
                    tx.setSignedAt(OffsetDateTime.now(ZoneOffset.UTC));
                    return result;
                });

        String hexPayload = Numeric.toHexString(TransactionEncoder.encode(rawTx, dsgResult.signature()));
        return new SignResult(hexPayload, dsgResult.retries());
    }

    public record SignResult(String hexPayload, int retries) {
    }
}
