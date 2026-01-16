#!/bin/bash
set -e

echo "🚀 Setting up Podcast 2.0 AI Player development environment..."

# Update system
sudo apt-get update

# Install Android SDK prerequisites
echo "📱 Installing Android development tools..."
sudo apt-get install -y --no-install-recommends \
    unzip \
    wget \
    gradle

# Clean apt cache to save space
sudo apt-get clean
sudo rm -rf /var/lib/apt/lists/*

# Install Android SDK Command Line Tools
echo "📦 Installing Android SDK..."
mkdir -p ~/android-sdk/cmdline-tools
cd ~/android-sdk/cmdline-tools
wget -q https://dl.google.com/android/repository/commandlinetools-linux-9477386_latest.zip
unzip -q commandlinetools-linux-9477386_latest.zip
mv cmdline-tools latest
rm commandlinetools-linux-9477386_latest.zip

# Set up Android environment variables
export ANDROID_HOME=~/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

echo 'export ANDROID_HOME=~/android-sdk' >> ~/.bashrc
echo 'export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools' >> ~/.bashrc

# Accept Android licenses
yes | ~/android-sdk/cmdline-tools/latest/bin/sdkmanager --licenses 2>/dev/null || true

# Install required Android SDK packages
echo "📲 Installing Android SDK packages..."
~/android-sdk/cmdline-tools/latest/bin/sdkmanager --install \
    "platform-tools" \
    "platforms;android-35" \
    "build-tools;35.0.0"

# Clean up Android SDK cache
rm -rf ~/.android/cache/* 2>/dev/null || true

# Install Node.js dependencies
echo "📦 Installing Node.js tools..."
npm install -g npm@latest

# Install Claude Code
echo "🤖 Installing Claude Code..."
npm install -g @anthropic-ai/claude-code

# Install Claude-Flow
echo "🌊 Installing Claude-Flow..."
npm install -g claude-flow@alpha || echo "⚠️ Claude-Flow alpha may not be available yet"

# Clean npm cache
npm cache clean --force

# Install Python dependencies
echo "🐍 Installing Python tools..."
pip install --upgrade pip
pip install --no-cache-dir \
    fastmcp \
    anthropic \
    pydantic \
    beads

# Install GitHub CLI extensions
echo "📊 Setting up GitHub tools..."
gh extension install github/gh-copilot 2>/dev/null || true

# Final cleanup
echo "🧹 Cleaning up..."
sudo apt-get autoremove -y 2>/dev/null || true

# Verify installations
echo "✅ Verifying installations..."
echo "Node.js: $(node --version)"
echo "Python: $(python --version)"
echo "Java: $(java -version 2>&1 | head -1)"
echo "Gradle: $(gradle --version | head -3 | tail -1)"
claude --version 2>/dev/null || echo "⚠️ Claude Code needs authentication"
claude-flow --version 2>/dev/null || echo "⚠️ Claude-Flow needs initialization"
bd --version 2>/dev/null || echo "⚠️ Beads installed"

echo ""
echo "✨ Setup complete! Next steps:"
echo "1. Authenticate Claude: claude --dangerously-skip-permissions"
echo "2. Initialize Claude-Flow: claude-flow init --sparc"
echo "3. Initialize Beads: bd init"
echo "4. Start building: claude-flow swarm 'implement podcast app'"
