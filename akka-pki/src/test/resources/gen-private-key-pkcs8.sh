#!/bin/bash

export PW="sadfasdf12341234"

keytool -genkeypair -v \
  -alias tmp \
  -dname "CN=tmp, OU=Example Org, O=Example Company, L=San Francisco, ST=California, C=US" \
  -keystore tmp.p12 \
  -storetype PKCS12 \
  -keypass:env PW \
  -storepass:env PW \
  -storetype PKCS12 \
  -keyalg EC \
  -keysize 256 \
  -validity 3650

# Export the private key as a PEM. This export adds some extra PKCS#12 bag info.
openssl pkcs12 -in tmp.p12 -nodes -nocerts -out tmp.pem -passin pass:$PW
# A second pass extracts the PKCS#12 bag information and produces a clean PKCS#8 PEM file.
openssl rsa -in tmp.pem -out EC-pkcs8.pem
openssl ec -in tmp.pem -out EC-pkcs1.pem

rm tmp.p*