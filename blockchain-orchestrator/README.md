# Add `mitmproxy` certificate to truststore
```sh
keytool -importcert -alias mitmproxy -file ~/.mitmproxy/mitmproxy-ca-cert.pem -keystore src/main/resources/certs/ca.p12 -storepass secret -noprompt
```