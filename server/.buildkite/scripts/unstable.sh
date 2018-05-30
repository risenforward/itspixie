#! /bin/bash

set -e

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
$DIR/kill-all-docker-containers.sh

CHANNEL="${1:?Provide the channel this script is run on (e.g. alpha, beta, stable)}"
VERSION_OFFSET="${2:?Offset in minor version to build against}"

# Rolling number versioning for unstable channels
LAST_GIT_TAG=$(git describe origin/master --abbrev=0 --tags)
TAG_ELEMENTS=(${LAST_GIT_TAG//./ })
NEXT_VERSION="${TAG_ELEMENTS[0]}.$((${TAG_ELEMENTS[1]} + ${VERSION_OFFSET}))"
LAST_DOCKER_TAG=$(curl -sS 'https://registry.hub.docker.com/v2/repositories/prismagraphql/prisma/tags/' | jq '."results"[]["name"]' --raw-output | grep -v latest | grep ${NEXT_VERSION}-${CHANNEL}- | head -n 1)

echo "Last git tag: $LAST_GIT_TAG"
echo "Next version: $NEXT_VERSION"
echo "Last docker tag: $LAST_DOCKER_TAG"

if [ -z LAST_DOCKER_TAG ]; then
    NEXT_DOCKER_TAG="$NEXT_VERSION-$CHANNEL-1"
else
    IFS=- read version channelStr rollingVersion <<< ${LAST_DOCKER_TAG}
    NEXT_DOCKER_TAG="$NEXT_VERSION-$CHANNEL-$(($rollingVersion + 1))"

    echo "Rolling version: $rollingVersion"
    echo "Next docker tag: $NEXT_DOCKER_TAG"
fi

# Always release -CHANNEL as well
ADDITIONALLY_RELEASE="$NEXT_VERSION-$CHANNEL"

${DIR}/docker-build.sh ${CHANNEL} ${NEXT_DOCKER_TAG} ${ADDITIONALLY_RELEASE}