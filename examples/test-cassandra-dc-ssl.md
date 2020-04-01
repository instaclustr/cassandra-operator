# SSL

In order to enable SSL on Cassandra cluster, you have to create a secret where your all files will be stored:

```
kubectl create secret generic test-cassandra-dc-ssl \
    --from-file=keystore.p12 \
    --from-file=truststore.jks \
    --from-file=ca-cert \
    --from-file=client.cer.pem \
    --from-file=client.key.pem
```