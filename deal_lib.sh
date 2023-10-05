rm -rf ./binary/testpine
rm -rf ./binary/lib
unzip -o -d ./binary/testpine ./binary/testpine.apk
cp -rf ./binary/testpine/lib ./binary
rm -rf ./binary/testpine