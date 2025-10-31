#!/bin/bash
set -e

# Configurazione
DOCKER_REGISTRY="192.168.1.128:30002"  # Modifica con il tuo registry
DOCKER_NAMESPACE="martini-jbpm"  # Modifica con il tuo namespace/username
IMAGE_NAME="jbpm-server-with-mdb"
VERSION="${VERSION:-1.0.0}"
FULL_IMAGE_NAME="${DOCKER_REGISTRY}/${DOCKER_NAMESPACE}/${IMAGE_NAME}:${VERSION}"

echo "=========================================="
echo "Building jBPM Server with MDB"
echo "=========================================="
echo "Image: ${FULL_IMAGE_NAME}"
echo ""

# Step 1: Build del MDB se non esiste
echo "Step 1: Building project (model + kjar + service) ..."
pushd .. >/dev/null
export JAVA_HOME=$(/usr/libexec/java_home -v 11)
mvn -DskipTests clean install
popd >/dev/null

# Step 2: Copia il fat JAR nella directory kubernetes
echo ""
echo "Step 2: Copying artifacts to kubernetes directory..."
# Service fat JAR
cp -f ../martini-jbpm-service/target/martini-jbpm-service-1.0.0-SNAPSHOT.jar .
echo "- Service JAR: $(du -h martini-jbpm-service-1.0.0-SNAPSHOT.jar | cut -f1)"
# Model JAR + POM (rinominato al formato atteso da Maven local repo)
cp -f ../martini-jbpm-model/target/martini-jbpm-model-1.0.0-SNAPSHOT.jar .
cp -f ../martini-jbpm-model/pom.xml martini-jbpm-model-1.0.0-SNAPSHOT.pom
echo "- Model JAR/POM copiati"

# Step 3: Build dell'immagine Docker
echo ""
echo "Step 3: Building Docker image..."
docker build -t ${FULL_IMAGE_NAME} .

# Step 4: Tag latest
echo ""
echo "Step 4: Tagging as latest..."
docker tag ${FULL_IMAGE_NAME} ${DOCKER_REGISTRY}/${DOCKER_NAMESPACE}/${IMAGE_NAME}:latest

# Step 5: Push (opzionale - commenta se non vuoi fare push automatico)
echo ""
echo "Step 5: Pushing to registry..."
read -p "Do you want to push to ${DOCKER_REGISTRY}? (y/n) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    docker push ${FULL_IMAGE_NAME}
    docker push ${DOCKER_REGISTRY}/${DOCKER_NAMESPACE}/${IMAGE_NAME}:latest
    echo "✅ Image pushed successfully!"
else
    echo "⏭️  Skipping push"
fi

echo ""
echo "=========================================="
echo "✅ Build completed!"
echo "=========================================="
echo "Image: ${FULL_IMAGE_NAME}"
echo ""
echo "Next steps:"
echo "1. Update kubernetes/kie-server.yaml with the new image:"
echo "   image: ${FULL_IMAGE_NAME}"
echo "2. Apply the updated deployment:"
echo "   kubectl apply -f kubernetes/kie-server.yaml -n jbpm"
echo "3. Check the logs:"
echo "   kubectl logs -f deployment/kie-server -n jbpm"
echo ""
