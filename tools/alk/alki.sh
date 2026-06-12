#!/bin/sh
ALKDIR="$(cd "$(dirname "$0")" && pwd)/v4.3/bin"
export PATH="$PATH:$ALKDIR:$ALKDIR/lib"
export LD_LIBRARY_PATH="$LD_LIBRARY_PATH:$ALKDIR:$ALKDIR/lib"
export DYLD_LIBRARY_PATH="$DYLD_LIBRARY_PATH:$ALKDIR:$ALKDIR/lib"
exec java -Djava.library.path="$ALKDIR/lib" -cp "$ALKDIR/alk.jar:$ALKDIR/lib/com.microsoft.z3.jar" main.ExecutionDriver -a "$@"
