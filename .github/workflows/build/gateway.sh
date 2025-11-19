#!/bin/sh
# Move the KnoxShell directory to proper place
# This is vecause of https://github.com/docker/compose/issues/4581#issuecomment-321386605
mv /knox-runtime/knoxshell/* /knoxshell

# Start Knox
java -jar /knox-runtime/bin/gateway.jar