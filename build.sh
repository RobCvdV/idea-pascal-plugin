#!/bin/bash
# Build script for DFM IntelliJ Plugin

set -e

echo "====================================="
echo "Building DFM Plugin for IntelliJ"
echo "====================================="

# Navigate to plugin directory
cd "$(dirname "$0")"

# Build with Gradle
echo "Building plugin with Gradle..."
./gradlew buildPlugin

# Get version from build.gradle.kts
VERSION=$(grep 'version = ' build.gradle.kts | head -1 | sed 's/.*"\(.*\)"/\1/')

echo ""
echo "====================================="
echo "Build complete!"
echo "====================================="
echo "Plugin ZIP location: build/distributions/idea-dfm-plugin-${VERSION}.zip"
echo ""
echo "To install:"
echo "1. Open IntelliJ IDEA (or WebStorm)"
echo "2. Go to Settings → Plugins"
echo "3. Click the gear icon → Install Plugin from Disk"
echo "4. Select: build/distributions/idea-dfm-plugin-${VERSION}.zip"
echo "5. Restart the IDE"
echo "====================================="
