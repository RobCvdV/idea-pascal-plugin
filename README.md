# Delphi Pascal Support Plugin for IntelliJ IDEA

A comprehensive IntelliJ IDEA plugin that provides syntax highlighting and code folding support for Delphi Pascal and Delphi Form (DFM) files.

## Features

### Object Pascal Support (.pas, .dpr, .dpk)
- **Full Syntax Highlighting**: All Pascal keywords, operators, and compiler directives.
- **Semantic Highlighting**: Distinct colors for classes, records, and interfaces.
- **Code Folding**: Support for `begin..end`, `class`, `record`, `try..except/finally`, `case`, `repeat..until`, and interface/implementation sections.
- **Stub Indexing**: Basic support for type definitions.

### Delphi Form Support (.dfm)
- **Syntax Highlighting**
  - Keywords: `object`, `inherited`, `inline`, `end`, `item`
  - Strings, numbers, booleans, and comments
  - Identifiers and property names
- **Code Folding**
  - Fold `object...end`, `inherited...end`, `inline...end`, and `item...end` blocks

## Building the Plugin

### Prerequisites
- Java 17 or higher
- Gradle (uses wrapper, no separate installation needed)

### Build Instructions
1. Navigate to the plugin directory:
   ```bash
   cd idea-pascal-plugin
   ```

2. Run the build command:
   ```bash
   ./gradlew buildPlugin
   ```

3. The plugin will be built to: `build/distributions/pascal-plugin-1.2.0.zip`

## Installing the Plugin
1. Open IntelliJ IDEA (or any JetBrains IDE)
2. Go to **Settings** (or **Preferences** on macOS) → **Plugins**
3. Click the gear icon ⚙️ → **Install Plugin from Disk...**
4. Navigate to and select: `build/distributions/pascal-plugin-1.2.0.zip`
5. Click **OK** and restart IntelliJ IDEA

## Development

### Project Structure
```
nl-akiar-pascal-plugin/
|-- build.gradle.kts          # Gradle build configuration
|-- settings.gradle.kts       # Gradle settings
`-- src/main/
    |-- java/nl/akiar/pascal/
    |   |-- PascalLanguage.java           # Pascal Language definition
    |   |-- dfm/                          # DFM support components
    |   |   |-- DfmLanguage.java
    |   |   |-- DfmFileType.java
    |   |   `-- ...
    |   |-- annotator/                    # Semantic highlighting
    |   |-- dpr/                          # DPR/Project support
    |   `-- psi/                          # Pascal PSI
    `-- resources/
        |-- META-INF/
        |   `-- plugin.xml                # Plugin manifest
        `-- icons/
            |-- pascal.svg                # Pascal icon
            `-- dfm.svg                   # DFM icon
```

## License
This plugin is created for internal use at Akiar.

### Modifying the Plugin

- **Add new keywords**: Edit relevant `.java` or lexer files.
- **Change syntax colors**: Modify `PascalSyntaxHighlighter.java` or `DfmSyntaxHighlighter.java`
- **Improve folding**: Edit `PascalFoldingBuilder.java` or `DfmFoldingBuilder.java`
- **Update plugin info**: Edit `src/main/resources/META-INF/plugin.xml`

After making changes, rebuild with `./gradlew buildPlugin`.

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

This plugin is created for internal use at Akiar.

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

