package fr.adrienbrault.idea.symfony2plugin.tests.intentions.yaml;

import fr.adrienbrault.idea.symfony2plugin.tests.SymfonyLightCodeInsightFixtureTestCase;

/**
 * @author Daniel Espendiller <daniel@espendiller.net>
 * @see fr.adrienbrault.idea.symfony2plugin.intentions.yaml.YamlQuotedEscapedInspection
 */
public class YamlQuotedEscapedInspectionTest extends SymfonyLightCodeInsightFixtureTestCase {

    public void testDeprecatedNonEscapedInDoubleQuotedStrings() {
        assertLocalInspectionContains("foo.yml",
            "class: \"Foo<caret>\\Bar\"",
            "Not escaping a backslash in a double-quoted string is deprecated"
        );

        assertLocalInspectionContainsNotContains("foo.yml",
            "class: \"Foo<caret>\\\\Bar\"",
            "Not escaping a backslash in a double-quoted string is deprecated"
        );

        assertLocalInspectionContainsNotContains("foo.yml",
            "class: 'Foo<caret>\\Bar'",
            "Not escaping a backslash in a double-quoted string is deprecated"
        );

        assertLocalInspectionContainsNotContains("foo.yml",
            "class: Foo<caret>\\Bar",
            "Not escaping a backslash in a double-quoted string is deprecated"
        );
    }

    public void testDeprecatedUsageOfAtCharAtTheBeginningOfUnquotedStrings() {
        assertLocalInspectionContains("foo.yml",
            "class: @f<caret>oo",
            "Deprecated usage of '@' at the beginning of unquoted string"
        );

        assertLocalInspectionContains("foo.yml",
            "class: `f<caret>oo",
            "Deprecated usage of '`' at the beginning of unquoted string"
        );

        assertLocalInspectionContains("foo.yml",
            "class: |f<caret>oo",
            "Deprecated usage of '|' at the beginning of unquoted string"
        );

        assertLocalInspectionContains("foo.yml",
            "class: >f<caret>oo",
            "Deprecated usage of '>' at the beginning of unquoted string"
        );

        assertLocalInspectionContains("foo.yml",
            "class: %f<caret>oo",
            "Not quoting a scalar starting with the '%' indicator character is deprecated since Symfony 3.1"
        );

        assertLocalInspectionContainsNotContains("foo.yml",
            "class: '%f<caret>oo'",
            "Not quoting a scalar starting with the '%' indicator character is deprecated since Symfony 3.1"
        );

        assertLocalInspectionContainsNotContains("foo.yml",
            "class: '@f<caret>oo'",
            "Deprecated usage of '@' at the beginning of unquoted string"
        );

        assertLocalInspectionContainsNotContains("foo.yml",
            "class: \"@f<caret>oo\"",
            "Deprecated usage of '@' at the beginning of unquoted string"
        );
    }

}
