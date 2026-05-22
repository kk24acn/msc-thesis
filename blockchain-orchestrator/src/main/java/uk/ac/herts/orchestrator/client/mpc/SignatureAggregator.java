package uk.ac.herts.orchestrator.client.mpc;

import org.springframework.stereotype.Component;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.crypto.Sign.SignatureData;
import org.web3j.utils.Numeric;

import uk.ac.herts.orchestrator.exception.SignatureAggregationException;
import uk.ac.herts.orchestrator.grpc.signer.Dsg.DsgPhaseResponse;
import uk.ac.herts.orchestrator.grpc.signer.Dsg.SignatureShare;

import java.math.BigInteger;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.List;

@Component
public class SignatureAggregator {

    public SignatureData aggregate(List<DsgPhaseResponse> responses, byte[] messageHash,
            String expectedEthereumAddress) {
        BigInteger n = Sign.CURVE_PARAMS.getN();
        BigInteger halfN = n.shiftRight(1);

        BigInteger sumS0 = BigInteger.ZERO;
        BigInteger sumS1 = BigInteger.ZERO;
        byte[] rPoint = responses.get(0).getSignatureShare().getR().toByteArray();

        for (DsgPhaseResponse response : responses) {
            SignatureShare share = response.getSignatureShare();
            BigInteger s0 = new BigInteger(1, share.getS0().toByteArray());
            BigInteger s1 = new BigInteger(1, share.getS1().toByteArray());

            sumS0 = sumS0.add(s0).mod(n);
            sumS1 = sumS1.add(s1).mod(n);
        }

        // DKLs23 fractional formula: s = sum(s0) * sum(s1)^-1
        BigInteger s1Inv = sumS1.modInverse(n);
        BigInteger s = sumS0.multiply(s1Inv).mod(n);

        // The first byte (0x04) indicates an uncompressed point, skip
        byte[] xBytes = Arrays.copyOfRange(rPoint, 1, 33); // r

        // Recovery ID
        byte[] yBytes = Arrays.copyOfRange(rPoint, 33, 65);
        BigInteger y = new BigInteger(1, yBytes);
        int recId = y.testBit(0) ? 1 : 0; // v

        // EIP-2 - all signatures must use the lower half of the curve order
        if (s.compareTo(halfN) > 0) {
            s = n.subtract(s);
            recId ^= 1;
        }

        SignatureData sigData = composeSignature(xBytes, s, recId);
        verifySignature(sigData, messageHash, expectedEthereumAddress);

        return sigData;
    }

    private SignatureData composeSignature(byte[] xBytes, BigInteger s, int recId) {
        return new SignatureData(
                (byte) (recId + 27),
                xBytes,
                Numeric.toBytesPadded(s, 32));
    }

    private void verifySignature(SignatureData sigData, byte[] messageHash, String expectedEthereumAddress) {
        try {
            BigInteger recoveredKey = Sign.signedMessageHashToKey(messageHash, sigData);
            String recoveredAddress = Numeric.prependHexPrefix(Keys.getAddress(recoveredKey));

            if (!recoveredAddress.equalsIgnoreCase(expectedEthereumAddress)) {
                throw new SignatureAggregationException(
                        "ECDSA verification failed: Recovered address does not match expected address");
            }
        } catch (SignatureException e) {
            throw new SignatureAggregationException("Signature recovery failed");
        }
    }
}
