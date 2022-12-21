#!/bin/bash

set -euo pipefail

parent_path=$( cd "$(dirname "${BASH_SOURCE[0]}")" ; pwd -P )
cd "$parent_path/.."

##################################################
# TODO
# decrypt sonatype.sbt + passphrase.env + key.gpg
##################################################

# import key
gpg --batch --import key.gpg

# load passphrase
source ./passphrase.env

# sign artifact
sbt publishSigned

# upload artifact to sonatype (to be replaced with 'sonatypeBundleRelease' later)
sbt sonatypePrepare
sbt sonatypeBundleUpload

# upload artifact to sonatype and release
# sonatypeBundleRelease