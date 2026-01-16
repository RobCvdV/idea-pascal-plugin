package com.mendrix.pascal;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

/**
 * Color settings page for Pascal files.
 * Allows customization of syntax highlighting colors in Settings -> Editor -> Color Scheme -> Pascal
 */
public class PascalColorSettingsPage implements ColorSettingsPage {

    private static final AttributesDescriptor[] DESCRIPTORS = new AttributesDescriptor[]{
            new AttributesDescriptor("Keyword", PascalSyntaxHighlighter.KEYWORD),
            new AttributesDescriptor("String", PascalSyntaxHighlighter.STRING),
            new AttributesDescriptor("Number", PascalSyntaxHighlighter.NUMBER),
            new AttributesDescriptor("Comment", PascalSyntaxHighlighter.COMMENT),
            new AttributesDescriptor("Compiler directive", PascalSyntaxHighlighter.DIRECTIVE),
            new AttributesDescriptor("Identifier", PascalSyntaxHighlighter.IDENTIFIER),
            new AttributesDescriptor("Operator", PascalSyntaxHighlighter.OPERATOR),
            new AttributesDescriptor("Parentheses", PascalSyntaxHighlighter.PARENTHESES),
            new AttributesDescriptor("Brackets", PascalSyntaxHighlighter.BRACKETS),
            new AttributesDescriptor("Semicolon", PascalSyntaxHighlighter.SEMICOLON),
            new AttributesDescriptor("Comma", PascalSyntaxHighlighter.COMMA),
            new AttributesDescriptor("Dot", PascalSyntaxHighlighter.DOT),
            // Semantic type colors
            new AttributesDescriptor("Type//Class type", PascalSyntaxHighlighter.TYPE_CLASS),
            new AttributesDescriptor("Type//Record type", PascalSyntaxHighlighter.TYPE_RECORD),
            new AttributesDescriptor("Type//Interface type", PascalSyntaxHighlighter.TYPE_INTERFACE),
    };

    @Nullable
    @Override
    public Icon getIcon() {
        return PascalFileType.INSTANCE.getIcon();
    }

    @NotNull
    @Override
    public SyntaxHighlighter getHighlighter() {
        return new PascalSyntaxHighlighter();
    }

    @NotNull
    @Override
    public String getDemoText() {
        return "unit SampleUnit;\n" +
               "\n" +
               "{$MODE DELPHI}\n" +
               "\n" +
               "interface\n" +
               "\n" +
               "uses\n" +
               "  Classes, SysUtils;\n" +
               "\n" +
               "type\n" +
               "  { Sample class definition }\n" +
               "  TSampleClass = class(TObject)\n" +
               "  private\n" +
               "    FName: string;\n" +
               "    FValue: Integer;\n" +
               "  public\n" +
               "    constructor Create(const AName: string);\n" +
               "    destructor Destroy; override;\n" +
               "    procedure DoSomething(Count: Integer); virtual;\n" +
               "    function Calculate(X, Y: Double): Double;\n" +
               "    property Name: string read FName write FName;\n" +
               "    property Value: Integer read FValue default 0;\n" +
               "  end;\n" +
               "\n" +
               "const\n" +
               "  MAX_VALUE = 100;\n" +
               "  PI_VALUE = 3.14159;\n" +
               "  HEX_VALUE = $FF00;\n" +
               "\n" +
               "implementation\n" +
               "\n" +
               "// Constructor implementation\n" +
               "constructor TSampleClass.Create(const AName: string);\n" +
               "begin\n" +
               "  inherited Create;\n" +
               "  FName := AName;\n" +
               "  FValue := 0;\n" +
               "end;\n" +
               "\n" +
               "destructor TSampleClass.Destroy;\n" +
               "begin\n" +
               "  inherited;\n" +
               "end;\n" +
               "\n" +
               "procedure TSampleClass.DoSomething(Count: Integer);\n" +
               "var\n" +
               "  I: Integer;\n" +
               "  S: string;\n" +
               "begin\n" +
               "  for I := 0 to Count - 1 do\n" +
               "  begin\n" +
               "    if I mod 2 = 0 then\n" +
               "      S := 'Even: ' + IntToStr(I)\n" +
               "    else\n" +
               "      S := 'Odd: ' + IntToStr(I);\n" +
               "  end;\n" +
               "\n" +
               "  try\n" +
               "    // Some operation\n" +
               "    if Count < 0 then\n" +
               "      raise Exception.Create('Invalid count');\n" +
               "  except\n" +
               "    on E: Exception do\n" +
               "      WriteLn(E.Message);\n" +
               "  end;\n" +
               "\n" +
               "  case Count of\n" +
               "    0: S := 'Zero';\n" +
               "    1..10: S := 'Small';\n" +
               "  else\n" +
               "    S := 'Large';\n" +
               "  end;\n" +
               "end;\n" +
               "\n" +
               "function TSampleClass.Calculate(X, Y: Double): Double;\n" +
               "begin\n" +
               "  Result := X * Y + 1.5E10;\n" +
               "end;\n" +
               "\n" +
               "end.\n";
    }

    @Nullable
    @Override
    public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
        return null;
    }

    @NotNull
    @Override
    public AttributesDescriptor[] getAttributeDescriptors() {
        return DESCRIPTORS;
    }

    @NotNull
    @Override
    public ColorDescriptor[] getColorDescriptors() {
        return ColorDescriptor.EMPTY_ARRAY;
    }

    @NotNull
    @Override
    public String getDisplayName() {
        return "Pascal";
    }
}
