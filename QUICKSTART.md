# Quick Start Guide

## Building and Installing the DFM Plugin

### Step 1: Build the Plugin

Navigate to the plugin directory and run the build script:

```bash
cd /Users/robcoenen/Dev/MendriX_Dev/mendrix-tms/MX/MX/main/.idea/dfm-plugin
./build.sh
```

This will:
- Download JFlex (lexer generator)
- Generate the lexer from the JFlex specification
- Compile the plugin
- Create a distributable ZIP file at `build/distributions/dfm-plugin-1.0.0.zip`

### Step 2: Install the Plugin

1. Open IntelliJ IDEA (or any JetBrains IDE like WebStorm, PyCharm, etc.)
2. Go to **Settings** (⌘, on Mac) or **File → Settings** (on Windows/Linux)
3. Navigate to **Plugins**
4. Click the gear icon ⚙️ in the top toolbar
5. Select **Install Plugin from Disk...**
6. Browse to: `build/distributions/dfm-plugin-1.0.0.zip`
7. Click **OK**
8. Restart IntelliJ IDEA when prompted

### Step 3: Verify Installation

1. Open any `.dfm` file in your project
2. You should see:
   - Syntax highlighting (keywords in blue, strings in green, etc.)
   - A "D" icon next to `.dfm` files in the project tree
   - Code folding indicators (- / +) in the gutter next to `object`, `inherited`, `inline` blocks

### Step 4: Use Code Folding

Click the minus (-) or plus (+) icons in the editor gutter to fold/unfold code blocks:
- `object ... end` blocks
- `inherited ... end` blocks
- `inline ... end` blocks
- Collection `<item> ... end` blocks

## Troubleshooting

### Build fails

**Error: "java: command not found"**
- Install Java 17 or higher: `brew install openjdk@17` (on macOS)
- Set JAVA_HOME: `export JAVA_HOME=$(/usr/libexec/java_home -v 17)`

**Error: "Permission denied"**
- Make sure the build script is executable: `chmod +x build.sh`

### Plugin doesn't load

**Check IntelliJ version**
- This plugin requires IntelliJ IDEA 2020.3 or later
- Check your version: Help → About

**Check plugin installation**
- Go to Settings → Plugins → Installed
- Look for "Delphi Form (DFM) Support"
- If it shows an error, check the IDE log: Help → Show Log in Finder/Explorer

### Syntax highlighting not working

**File not recognized**
- Verify the file extension is `.dfm` (lowercase)
- Try closing and reopening the file
- Check Settings → Editor → File Types to ensure DFM is listed

**Colors not visible**
- The plugin uses default IntelliJ color schemes
- Try switching to a different color scheme: Settings → Editor → Color Scheme

## Uninstalling

1. Go to Settings → Plugins
2. Find "Delphi Form (DFM) Support" in the Installed list
3. Click the gear icon next to it
4. Select **Uninstall**
5. Restart IntelliJ IDEA

## Need Help?

Check the full README.md for more detailed information about:
- Plugin architecture
- Modifying the plugin
- Development guidelines

