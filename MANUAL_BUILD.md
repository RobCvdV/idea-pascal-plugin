# Manual Build Instructions for DFM Plugin

## Issue: JFlex Dependencies

JFlex requires the java-cup runtime library to function. The build script now downloads both dependencies automatically.

## Solution: Automatic Build (Recommended)

### Try the Updated Build Script

```bash
cd /Users/robcoenen/Dev/MendriX_Dev/mendrix-tms/MX/MX/main/.idea/dfm-plugin
./build.sh
```

The script will:
1. Download JFlex (1.1 MB)
2. Download java-cup runtime (25 KB)
3. Generate the lexer with both JARs in classpath
4. Build the plugin with Gradle

## Manual Build (If Automatic Fails)

### Step 1: Clean Up

```bash
cd /Users/robcoenen/Dev/MendriX_Dev/mendrix-tms/MX/MX/main/.idea/dfm-plugin
rm -rf lib
mkdir -p lib
```

### Step 2: Download Dependencies

Download JFlex:
```bash
curl -L "https://repo1.maven.org/maven2/de/jflex/jflex/1.9.1/jflex-1.9.1.jar" -o lib/jflex-1.9.1.jar
```

Download java-cup runtime (required dependency):
```bash
curl -L "https://repo1.maven.org/maven2/com/github/vbmacher/java-cup-runtime/11b/java-cup-runtime-11b.jar" -o lib/java-cup-runtime-11b.jar
```

**Verify downloads:**
```bash
ls -lh lib/
```

Expected output:
- `jflex-1.9.1.jar` → ~1.1 MB (1130240 bytes)
- `java-cup-runtime-11b.jar` → ~25 KB (26000 bytes)

### Step 3: Test JFlex

```bash
java -cp "lib/jflex-1.9.1.jar:lib/java-cup-runtime-11b.jar" jflex.Main --version
```

Should output: `JFlex 1.9.1`

### Step 4: Generate Lexer

```bash
mkdir -p src/main/gen/com/mendrix/dfm
java -cp "lib/jflex-1.9.1.jar:lib/java-cup-runtime-11b.jar" jflex.Main \
  -d src/main/gen/com/mendrix/dfm \
  src/main/java/com/mendrix/dfm/Dfm.flex
```

Expected output:
```
Reading "src/main/java/com/mendrix/dfm/Dfm.flex"
Constructing NFA : 123 states in NFA
Converting NFA to DFA : 
...
Writing code to "src/main/gen/com/mendrix/dfm/DfmLexer.java"
```

### Step 5: Build with Gradle

```bash
./gradlew buildPlugin
```

Expected result: `build/distributions/dfm-plugin-1.0.0.zip`

### Step 6: Verify

```bash
ls -lh build/distributions/dfm-plugin-1.0.0.zip
```

Should show a ZIP file of several hundred KB.

## Troubleshooting

### Error: "NoClassDefFoundError: java_cup/runtime/Scanner"

This means java-cup runtime is missing from the classpath.

**Solution:**
1. Verify java-cup is downloaded: `ls -lh lib/java-cup-runtime-11b.jar`
2. Make sure you're using `-cp` with both JARs (not `-jar`)
3. Use the correct classpath separator (`:` on Mac/Linux, `;` on Windows)

### Error: "Invalid or corrupt jarfile"

**Solution:**
1. Check file size: `ls -lh lib/*.jar`
2. If either JAR is < 1 KB, it's an HTML error page
3. Delete and re-download: `rm lib/*.jar` then download again
4. Or download in browser from:
   - https://repo1.maven.org/maven2/de/jflex/jflex/1.9.1/
   - https://repo1.maven.org/maven2/com/github/vbmacher/java-cup-runtime/11b/

### Error: "java: command not found"

**Solution:**
```bash
# Install Java 17+
brew install openjdk@17

# Add to PATH (add to ~/.zshrc)
export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"

# Verify
java -version
```

### Gradle Build Fails

**Solution:**
```bash
# Clean and rebuild
./gradlew clean
./gradlew buildPlugin --stacktrace --info
```

## Quick One-Liner (Once Dependencies Downloaded)

```bash
java -cp "lib/jflex-1.9.1.jar:lib/java-cup-runtime-11b.jar" jflex.Main -d src/main/gen/com/mendrix/dfm src/main/java/com/mendrix/dfm/Dfm.flex && ./gradlew buildPlugin
```

## Installation

1. Open IntelliJ IDEA
2. **Settings** (⌘,) → **Plugins**
3. **⚙️** → **Install Plugin from Disk...**
4. Select: `build/distributions/dfm-plugin-1.0.0.zip`
5. **Restart** IntelliJ IDEA
6. Open a `.dfm` file to test!

## Success Checklist

- [ ] JFlex downloaded (1.1 MB)
- [ ] java-cup runtime downloaded (25 KB)
- [ ] `java -cp "lib/*" jflex.Main --version` shows "JFlex 1.9.1"
- [ ] Lexer generated at `src/main/gen/com/mendrix/dfm/DfmLexer.java`
- [ ] Gradle build successful
- [ ] ZIP exists at `build/distributions/dfm-plugin-1.0.0.zip`
- [ ] Plugin installs in IntelliJ
- [ ] Syntax highlighting works
- [ ] Code folding works

