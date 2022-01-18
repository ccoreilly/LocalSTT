#!/bin/sh

BUILD_TOOLS=~/Android/Sdk/build-tools/32.0.0/

if [ -z "$1" ]; then
	echo "usage: $0 your-app.apk"
	exit 1
fi

DIR=`mktemp -d`
DN=`dirname "$1"`
BN=`basename "$1"`
OUT="$DN/repacked-$BN"
OUT_ALIGNED="$DN/aligned-$BN"
OUT_SIGNED="$DN/signed-$BN"

# Debug mode
set -x

# Repack without the META-INF in case it was already signed
# and flag resources.arsc as no-compress:
unzip -q "$1" -d "$DIR"
pushd .
cd $DIR

rm -rf "$DIR/META-INF"
zip -n "resources.arsc" -r ../repacked.$$.apk *

popd

mv "$DIR/../repacked.$$.apk" "$OUT"

# Align
rm -f "$OUT_ALIGNED"
"$BUILD_TOOLS"/zipalign -p -v 4 "$OUT" "$OUT_ALIGNED"

# Verify
"$BUILD_TOOLS"/zipalign -vc 4 "$OUT_ALIGNED"

# Sign
"$BUILD_TOOLS"/apksigner sign -verbose -ks ~/my-release-key.keystore --out "$OUT_SIGNED" "$OUT_ALIGNED"

# Cleanup
rm -rf "$DIR"

echo == Done: $OUT_ALIGNED
