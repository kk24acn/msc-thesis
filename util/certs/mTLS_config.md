# IMPORTANT: mTLS is not configured on the project by default. These certificates and commands were executed as a part of experiment and are left for the reference. 

>Run From `./util/certs` folder

## Generate CA
```sh
openssl genrsa -out ca.key 4096
openssl req -x509 -new -nodes -key ca.key -days 365 -out ca.crt -subj "/CN=orchestra"
```

## Generate `signer` cert
```sh
openssl genrsa -out ./signer/signer.key 4096
openssl req -new -key ./signer/signer.key -out ./signer/signer.csr -config ./signer/signer.cnf
openssl x509 -req -in ./signer/signer.csr -CA ca.crt -CAkey ca.key -CAcreateserial -out ./signer/signer.crt -days 365 -extensions v3_req -extfile ./signer/signer.cnf
```

## Generate `orchestrator` cert
```sh
openssl genrsa -out ./orchestrator/orchestrator.key 4096
openssl req -new -key ./orchestrator/orchestrator.key -out ./orchestrator/orchestrator.csr -subj "/CN=orchestrator"
openssl x509 -req -in ./orchestrator/orchestrator.csr -CA ca.crt -CAkey ca.key -CAcreateserial -out ./orchestrator/orchestrator.crt -days 365
```

---

## Create `pkcs12` for `orchestrator`
```sh
openssl pkcs12 -export \
  -in ./orchestrator/orchestrator.crt \
  -inkey ./orchestrator/orchestrator.key \
  -certfile ca.crt \
  -out ./orchestrator/orchestrator.p12 \
  -name sslclient \
  -password pass:secret
```

## Create `pkcs12` for `CA`
```sh
keytool -importcert \
  -file ca.crt \
  -alias orchestra-ca \
  -keystore ca.p12 \
  -storetype PKCS12 \
  -storepass secret \
  -noprompt
```
---

## Verify `crt`
```sh
openssl x509 -in ./signer/signer.crt -noout -text | grep -A1 "Subject Alternative Name"
```

## Verify `pkcs12`
```sh
keytool -list -keystore ca.p12 -storetype PKCS12 -storepass secret
```