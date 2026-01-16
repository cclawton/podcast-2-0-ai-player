#!/bin/bash
set -e

echo "🚀 Setting up Podcast 2.0 AI Player development environment..."

# Update system
sudo apt-get update

# Install Android SDK prerequisites
echo "📱 Installing Android development tools..."
sudo apt-get install -y \
    unzip \
    wget \
    openjdk-17-jdk \
    gradle

# Install Android SDK Command Line Tools
mkdir -p ~/android-sdk/cmdline-tools
cd ~/android-sdk/cmdline-tools
wget https://dl.google.com/android/repository/commandlinetools-linux-9477386_latest.zip
unzip commandlinetools-linux-9477386_latest.zip
mv cmdline-tools latest
rm commandlinetools-linux-9477386_latest.zip

# Set up Android environment variables
echo 'export ANDROID_HOME=~/android-sdk' >> ~/.bashrc
echo 'export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools' >> ~/.bashrc
source ~/.bashrc

# Accept Android licenses
yes | ~/android-sdk/cmdline-tools/latest/bin/sdkmanager --licenses || true

# Install required Android SDK packages
~/android-sdk/cmdline-tools/latest/bin/sdkmanager \
    "platform-tools" \
    "platforms;android-35" \
    "build-tools;35.0.0" \
    "emulator"

# Install Node.js dependencies
echo "📦 Installing Node.js tools..."
npm install -g npm@latest

# Install Claude Code
echo "🤖 Installing Claude Code..."
npm install -g @anthropic-ai/claude-code

# Install Claude-Flow
echo "🌊 Installing Claude-Flow..."
npm install -g claude-flow@alpha

# Install Python dependencies
echo "🐍 Installing Python tools..."
pip install --upgrade pip
pip install \
    fastmcp \
    anthropic \
    pydantic \
    beads

# Install GitHub CLI extensions
echo "📊 Setting up GitHub tools..."
gh extension install github/gh-copilot || true

# Verify installations
echo "✅ Verifying installations..."
node --version
python --version
java -version
gradle --version
claude --version || echo "⚠️  Claude Code needs authentication"
claude-flow --version || echo "⚠️  Claude-Flow needs initialization"
bd --version || echo "⚠️  Beads installed"

echo "✨ Setup complete! Next steps:"
echo "1. Authenticate Claude: claude --dangerously-skip-permissions"
echo "2. Initialize Claude-Flow: claude-flow init --sparc"
echo "3. Initialize Beads: bd init"
echo "4. Start building: claude-flow swarm 'implement podcast app'"
