# Delphi Form (DFM) Plugin for IntelliJ IDEA

A simple IntelliJ IDEA plugin that provides syntax highlighting and code folding support for Delphi Form (DFM) files.

## Features

- **Syntax Highlighting**
  - Keywords: `object`, `inherited`, `inline`, `end`, `item`
  - Strings (single-quoted)
  - Numbers (decimal and hexadecimal)
  - Comments (line comments `//` and block comments `{...}` and `(*...*)`)
  - Identifiers and property names
  - Operators and punctuation

- **Code Folding**
  - Fold `object...end` blocks
  - Fold `inherited...end` blocks
  - Fold `inline...end` blocks
  - Fold collection `<item>...end` blocks

## Building the Plugin

### Prerequisites

- Java 17 or higher
- Gradle (uses wrapper, no separate installation needed)
- Internet connection (to download dependencies)

### Build Instructions

1. Navigate to the plugin directory:
   ```bash
   cd .idea/dfm-plugin
   ```

2. Run the build script:
   ```bash
   ./build.sh
   ```

   This script will:
   - Download JFlex if not present
   - Generate the lexer from the JFlex specification
   - Build the plugin using Gradle
   - Create a distributable ZIP file

3. The plugin will be built to: `build/distributions/dfm-plugin-1.0.2.zip`

### Manual Build (without build.sh)

If you prefer to build manually:

1. Download JFlex:
   ```bash
   mkdir -p lib
   curl -L "https://github.com/jflex-de/jflex/releases/download/v1.9.1/jflex-full-1.9.1.jar" -o lib/jflex-full-1.9.1.jar
   ```

2. Generate the lexer:
   ```bash
   mkdir -p src/main/gen/com/mendrix/dfm
   java -jar lib/jflex-full-1.9.1.jar -d src/main/gen/com/mendrix/dfm src/main/java/com/mendrix/dfm/Dfm.flex
   ```

3. Build with Gradle:
   ```bash
   ./gradlew buildPlugin
   ```

## Installing the Plugin

1. Open IntelliJ IDEA (or any JetBrains IDE)
2. Go to **Settings** (or **Preferences** on macOS) → **Plugins**
3. Click the gear icon ⚙️ → **Install Plugin from Disk...**
4. Navigate to and select: `build/distributions/dfm-plugin-1.0.2.zip`
5. Click **OK** and restart IntelliJ IDEA

## Using the Plugin

Once installed, the plugin will automatically:
- Recognize `.dfm` files
- Apply syntax highlighting
- Enable code folding in DFM files

### Code Folding

Click the minus/plus icons in the gutter next to `object`, `inherited`, or `inline` declarations to fold/unfold code blocks.

## Development

### Project Structure

```
dfm-plugin/
├── build.gradle.kts          # Gradle build configuration
├── settings.gradle.kts       # Gradle settings
├── build.sh                  # Build script
└── src/main/
    ├── java/com/mendrix/dfm/
    │   ├── DfmLanguage.java              # Language definition
    │   ├── DfmFileType.java              # File type registration
    │   ├── DfmFileTypeFactory.java       # File type factory
    │   ├── Dfm.flex                      # JFlex lexer specification
    │   ├── DfmTokenType.java             # Token type class
    │   ├── DfmTokenTypes.java            # Token type constants
    │   ├── DfmLexerAdapter.java          # Lexer adapter
    │   ├── DfmSyntaxHighlighter.java     # Syntax highlighter
    │   ├── DfmSyntaxHighlighterFactory.java
    │   ├── DfmParserDefinition.java      # Parser definition
    │   ├── DfmParser.java                # Simple parser
    │   ├── DfmFile.java                  # PSI file
    │   ├── DfmPsiElement.java            # PSI element
    │   └── DfmFoldingBuilder.java        # Code folding
    └── resources/
        ├── META-INF/
        │   └── plugin.xml                # Plugin manifest
        └── icons/
            └── dfm.svg                   # File type icon
```

### Modifying the Plugin

- **Add new keywords**: Edit `Dfm.flex` and `DfmTokenTypes.java`
- **Change syntax colors**: Modify `DfmSyntaxHighlighter.java`
- **Improve folding**: Edit `DfmFoldingBuilder.java`
- **Update plugin info**: Edit `src/main/resources/META-INF/plugin.xml`

After making changes, rebuild with `./build.sh` or `./gradlew buildPlugin`.

## Troubleshooting

### Build fails with "JFlex not found"

Run the build script which downloads JFlex automatically:
```bash
./build.sh
```

### Plugin doesn't load after installation

1. Check that you're using IntelliJ IDEA 2020.3 or later
2. Verify the plugin was installed: Settings → Plugins → Installed
3. Check the IDE log: Help → Show Log in Finder (or Explorer)

### Syntax highlighting not working

1. Verify the file extension is `.dfm`
2. Try reopening the file
3. Check Settings → Editor → File Types to ensure DFM is registered

## License

This plugin is created for internal use at MendriX.

## Version History

- **1.0.3** (2026-01-15)
  - **Fixed lexer buffer/EOF handling issues**
  - Previous issue: `zzReader` was null causing NPE when `zzRefill()` was called
  - This version: Fixed "Scan buffer limit reached [0]" EOFException
  - Root cause: JFlex's `zzRefill()` failed when pre-filled buffer was exhausted
  - Fix: Added custom `EOF_READER` that always returns -1 (EOF), so `zzRefill()` properly signals end of input instead of throwing exceptions

- **1.0.2** (2026-01-14)
  - Fixed lexer adapter causing "discontinuous token sequence" errors
  - Fixed folding builder creating out-of-range fold regions
  - Added case-insensitive keyword matching for folding
  - Improved nested block folding with stack-based approach

- **1.0.1** (2026-01-14)
  - Bug fixes

- **1.0.0** (2026-01-14)
  - Initial release
  - Basic syntax highlighting
  - Code folding support

