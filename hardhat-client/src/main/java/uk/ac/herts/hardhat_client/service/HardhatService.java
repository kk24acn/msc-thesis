package uk.ac.herts.hardhat_client.service;

import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.Transfer;
import org.web3j.crypto.Credentials;
import org.web3j.utils.Convert;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigDecimal;

@Service
public class HardhatService {

    private final Web3j web3j;
    private final Credentials credentials;

    public HardhatService() {
        this.web3j = Web3j.build(new HttpService());
        this.credentials = Credentials.create("***");
    }

    public void sendEther(String toAddress, BigDecimal amount) throws Exception {
        TransactionReceipt transactionReceipt = Transfer.sendFunds(this.web3j, credentials, toAddress, amount, Convert.Unit.ETHER).send();

        System.out.println("Transaction hash: " + transactionReceipt.getTransactionHash());
    }
}

