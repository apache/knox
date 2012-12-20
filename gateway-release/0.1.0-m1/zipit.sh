cd ..
cp -rf 0.1.0-m1/ gateway-0.1.0-m1
cd gateway-0.1.0-m1
cp ../../gateway-test-ldap/target/gateway-test-ldap-0.1.0-SNAPSHOT.jar ./bin
cp ../../gateway-server/target/gateway-server-0.1.0-SNAPSHOT.jar ./bin
rm zipit.sh
cd ..
jar -cvf gateway-0.1.0-m1-download.zip gateway-0.1.0-m1
