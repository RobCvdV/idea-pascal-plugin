package com.mendrix.dfm;

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
 * Color settings page for DFM files.
 * Allows customization of syntax highlighting colors in Settings → Editor → Color Scheme → DFM
 */
public class DfmColorSettingsPage implements ColorSettingsPage {

    private static final AttributesDescriptor[] DESCRIPTORS = new AttributesDescriptor[]{
            new AttributesDescriptor("Keyword", DfmSyntaxHighlighter.KEYWORD),
            new AttributesDescriptor("Object name", DfmSyntaxHighlighter.OBJECT_NAME),
            new AttributesDescriptor("Class/Type name", DfmSyntaxHighlighter.CLASS_NAME),
            new AttributesDescriptor("Property name", DfmSyntaxHighlighter.PROPERTY_NAME),
            new AttributesDescriptor("Boolean", DfmSyntaxHighlighter.BOOLEAN),
            new AttributesDescriptor("String", DfmSyntaxHighlighter.STRING),
            new AttributesDescriptor("Number", DfmSyntaxHighlighter.NUMBER),
            new AttributesDescriptor("Comment", DfmSyntaxHighlighter.COMMENT),
            new AttributesDescriptor("Identifier", DfmSyntaxHighlighter.IDENTIFIER),
            new AttributesDescriptor("Operator", DfmSyntaxHighlighter.OPERATOR),
            new AttributesDescriptor("Braces", DfmSyntaxHighlighter.BRACES),
            new AttributesDescriptor("Brackets", DfmSyntaxHighlighter.BRACKETS),
            new AttributesDescriptor("Parentheses", DfmSyntaxHighlighter.PARENTHESES),
    };

    @Nullable
    @Override
    public Icon getIcon() {
        return DfmFileType.INSTANCE.getIcon();
    }

    @NotNull
    @Override
    public SyntaxHighlighter getHighlighter() {
        return new DfmSyntaxHighlighter();
    }

    @NotNull
    @Override
    public String getDemoText() {
        return "object MainForm: TMainForm\n" +
                "  Left = 0\n" +
                "  Top = 0\n" +
                "  Caption = 'Main Form'\n" +
                "  ClientHeight = 480\n" +
                "  ClientWidth = 640\n" +
                "  Color = $00FF8040\n" +
                "  Visible = True\n" +
                "  Enabled = False\n" +
                "  { This is a block comment }\n" +
                "  // This is a line comment\n" +
                "  object Panel1: TPanel\n" +
                "    Left = 8\n" +
                "    Top = 8\n" +
                "    Width = 185\n" +
                "    Height = 41\n" +
                "    Caption = 'Panel1'\n" +
                "    ParentFont = True\n" +
                "  end\n" +
                "  inherited Button1: TButton\n" +
                "    Left = 200\n" +
                "    Top = 16\n" +
                "    Width = 75\n" +
                "    Height = 25\n" +
                "    Caption = 'Click Me'\n" +
                "    OnClick = Button1Click\n" +
                "  end\n" +
                "  object Items: TCollection\n" +
                "    item\n" +
                "      Name = 'First'\n" +
                "      Value = 123.45\n" +
                "      Active = True\n" +
                "    end\n" +
                "    item\n" +
                "      Name = 'Second'\n" +
                "      Value = -67.89\n" +
                "      Active = False\n" +
                "    end\n" +
                "  end\n" +
                "end\n";
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
        return "DFM";
    }
}
